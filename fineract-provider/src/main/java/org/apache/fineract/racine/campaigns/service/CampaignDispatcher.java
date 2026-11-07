/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.racine.campaigns.service;

import com.github.mustachejava.Mustache;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.BrevoEmailClient;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.notification.data.SmsMessageData;
import org.apache.fineract.notification.data.SmsSendOutcome;
import org.apache.fineract.notification.service.SmsNotificationWritePlatformService;
import org.apache.fineract.racine.campaigns.domain.CampaignStatus;
import org.apache.fineract.racine.campaigns.domain.CommChannel;
import org.apache.fineract.racine.campaigns.domain.CommPurpose;
import org.apache.fineract.racine.campaigns.domain.DeliveryReason;
import org.apache.fineract.racine.campaigns.domain.DeliveryStatus;
import org.apache.fineract.racine.campaigns.domain.ScheduleType;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs from the "Send Due Campaigns" job. Starts approved campaigns whose time has come, then sends their messages in
 * batches. Each message is sent and recorded in its own transaction, so a failure later on never undoes the record of
 * an SMS or email that already went out (and was paid for). Email goes through Brevo, at most the daily email limit a
 * day; Brevo's webhook later reports which emails were delivered, bounced or marked as spam.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignDispatcher {

    /** Messages per campaign per job run; the job runs every minute. */
    static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbcTemplate;
    private final AudienceService audienceService;
    private final CommMessageRenderer renderer;
    private final SmsNotificationWritePlatformService smsService;
    private final BrevoEmailClient emailClient;
    private final CampaignWriteService writeService;
    private final PlatformTransactionManager transactionManager;
    private final Environment environment;

    public void runDueCampaigns() {
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        for (final Long id : jdbcTemplate.queryForList(
                "select id from m_comm_campaign where status = ? and next_run_at <= ? order by next_run_at", Long.class,
                CampaignStatus.SCHEDULED.name(), now)) {
            runInNewTransaction(() -> startRun(id), id);
        }
        for (final Long id : jdbcTemplate.queryForList("select id from m_comm_campaign where status = ? order by id", Long.class,
                CampaignStatus.SENDING.name())) {
            try {
                sendBatch(id);
            } catch (RuntimeException e) {
                log.error("Campaign {}: sending failed and continues on the next run: {}", id, e.getMessage(), e);
            }
        }
    }

    /** Works out who gets the message and queues it, if SMS is on and the wallet covers the whole run. */
    void startRun(final Long id) {
        final Map<String, Object> campaign = jdbcTemplate
                .queryForMap("select c.status, c.purpose, c.audience_json, c.channel, c.last_error,"
                        + " o.hierarchy from m_comm_campaign c join m_office o on o.id = c.office_id where c.id = ?", id);
        if (!CampaignStatus.SCHEDULED.name().equals(campaign.get("status"))) {
            return;
        }
        final CampaignSchedule schedule = writeService.schedule(id);
        final LocalDateTime localNow = DateUtils.getLocalDateTimeOfTenant();
        if (!schedule.allowsSendingAt(localNow)) {
            // Within quiet hours: start when sending is allowed again
            jdbcTemplate.update("update m_comm_campaign set next_run_at = ? where id = ?", schedule.firstAllowed(localNow), id);
            return;
        }
        final CommChannel channel = CommChannel.valueOf((String) campaign.get("channel"));
        final List<AudienceService.Recipient> recipients = audienceService.recipients(
                AudienceService.parse((String) campaign.get("audience_json")), (String) campaign.get("hierarchy"),
                CommPurpose.valueOf((String) campaign.get("purpose")), channel);
        final long toSend = recipients.stream().filter(recipient -> recipient.skipReason() == null).count();
        final String waiting = channel == CommChannel.EMAIL ? emailWaitingReason() : smsWaitingReason(toSend);
        if (waiting != null) {
            if (!waiting.equals(campaign.get("last_error"))) {
                jdbcTemplate.update("update m_comm_campaign set last_error = ? where id = ?", waiting, id);
                writeService.history(id, "WAITING", waiting);
            }
            return;
        }
        final LocalDateTime now = CommSupport.nowUtc();
        final Integer last = jdbcTemplate.queryForObject("select coalesce(max(run_number), 0) from m_comm_run where campaign_id = ?",
                Integer.class, id);
        final long runId = new SimpleJdbcInsert(jdbcTemplate).withTableName("m_comm_run")
                .usingColumns("campaign_id", "run_number", "started_on_utc").usingGeneratedKeyColumns("id")
                .executeAndReturnKey(Map.of("campaign_id", id, "run_number", (last == null ? 0 : last) + 1, "started_on_utc", now))
                .longValue();
        jdbcTemplate.batchUpdate(
                "insert into m_comm_delivery (run_id, campaign_id, client_id, channel, address, status, reason, attempts,"
                        + " created_on_utc, recipient_type, recipient_id) values (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
                new BatchPreparedStatementSetter() {

                    @Override
                    public void setValues(final java.sql.PreparedStatement ps, final int i) throws java.sql.SQLException {
                        final AudienceService.Recipient recipient = recipients.get(i);
                        ps.setLong(1, runId);
                        ps.setLong(2, id);
                        ps.setObject(3, recipient.clientId());
                        ps.setString(4, channel.name());
                        ps.setString(5, StringUtils
                                .abbreviate(recipient.address() != null ? recipient.address() : recipient.contact(channel), 150));
                        ps.setString(6, recipient.skipReason() == null ? DeliveryStatus.QUEUED.name() : DeliveryStatus.SKIPPED.name());
                        ps.setString(7, recipient.skipReason() == null ? null : recipient.skipReason().name());
                        ps.setObject(8, now);
                        ps.setString(9, recipient.type());
                        ps.setLong(10, recipient.recipientId());
                    }

                    @Override
                    public int getBatchSize() {
                        return recipients.size();
                    }
                });
        jdbcTemplate.update("update m_comm_campaign set status = ?, next_run_at = null, last_error = null where id = ?",
                CampaignStatus.SENDING.name(), id);
        writeService.history(id, "RUN_STARTED",
                recipients.size() + " clients matched: " + toSend + " to send, " + (recipients.size() - toSend) + " skipped");
    }

    /** Sends the next batch of queued messages, or finishes the campaign when none are left. */
    void sendBatch(final Long id) {
        final List<Map<String, Object>> queued = jdbcTemplate
                .queryForList("select id, client_id, recipient_type, recipient_id from m_comm_delivery where campaign_id = ?"
                        + " and status = ? order by id", id, DeliveryStatus.QUEUED.name());
        if (queued.isEmpty()) {
            runInNewTransaction(() -> finish(id), id);
            return;
        }
        final CampaignSchedule schedule = writeService.schedule(id);
        final LocalDateTime localNow = DateUtils.getLocalDateTimeOfTenant();
        if (!schedule.allowsSendingAt(localNow)) {
            final String waiting = "Paused for quiet hours: sending continues at "
                    + schedule.firstAllowed(localNow).format(CommSupport.LOCAL_TIME) + ".";
            jdbcTemplate.update("update m_comm_campaign set last_error = ? where id = ? and (last_error is null or last_error <> ?)",
                    waiting, id, waiting);
            return;
        }
        jdbcTemplate.update("update m_comm_campaign set last_error = null where id = ? and last_error like 'Paused for quiet hours%'", id);
        final Map<String, Object> campaign = jdbcTemplate
                .queryForMap("select message_text, email_subject, channel from m_comm_campaign where id = ?", id);
        final boolean email = CommChannel.EMAIL.name().equals(campaign.get("channel"));
        int size = Math.min(BATCH_SIZE, queued.size());
        if (email) {
            final int allowance = emailAllowanceToday();
            if (allowance <= 0) {
                final String waiting = "Daily email limit reached (campaign-email-daily-limit): sending continues tomorrow.";
                jdbcTemplate.update("update m_comm_campaign set last_error = ? where id = ? and (last_error is null or last_error <> ?)",
                        waiting, id, waiting);
                return;
            }
            size = Math.min(size, allowance);
        }
        jdbcTemplate.update("update m_comm_campaign set last_error = null where id = ? and (last_error like 'Daily email limit%'"
                + " or last_error like 'Waiting for the email service%')", id);
        final String text = (String) campaign.get("message_text");
        final String senderId = email ? null : senderId(id);
        final Mustache mustache = email ? renderer.compileHtml(text, "campaign-" + id) : renderer.compile(text, "campaign-" + id);
        final Mustache subject = email
                ? renderer.compile(StringUtils.defaultString((String) campaign.get("email_subject")), "campaign-subject-" + id)
                : null;
        final List<Map<String, Object>> batch = queued.subList(0, size);
        final Map<String, Map<String, Object>> scopes = scopes(batch);
        final String countryCode = environment.getProperty("sms.default.country.code", "256");
        for (final Map<String, Object> row : batch) {
            // Stop between messages if staff paused or stopped the campaign
            final String status = jdbcTemplate.queryForObject("select status from m_comm_campaign where id = ?", String.class, id);
            if (!CampaignStatus.SENDING.name().equals(status)) {
                return;
            }
            final Long deliveryId = ((Number) row.get("id")).longValue();
            final Map<String, Object> scope = scopes.get(recipientKey(row));
            final Stop stop = callInNewTransaction(() -> email ? sendOneEmail(deliveryId, scope, mustache, subject)
                    : sendOne(deliveryId, scope, mustache, countryCode, senderId), id);
            if (stop == null) {
                continue;
            }
            if (stop.waiting() != null) {
                // The email service asked us to slow down or its allowance is used up: the rest stay queued
                jdbcTemplate.update("update m_comm_campaign set last_error = ? where id = ?", stop.waiting(), id);
                return;
            }
            // Nothing else can go out until the SMS account or email service is fixed: leave the rest retryable
            runInNewTransaction(() -> {
                jdbcTemplate.update("update m_comm_delivery set status = ?, reason = ? where campaign_id = ? and status = ?",
                        DeliveryStatus.FAILED.name(), stop.failRest().name(), id, DeliveryStatus.QUEUED.name());
                final String message = switch (stop.failRest()) {
                    case NO_SMS_CREDIT -> "SMS credit ran out during sending. Top up, then retry the failed messages.";
                    case SMS_OFF -> "SMS was switched off during sending. Switch it on, then retry the failed messages.";
                    case EMAIL_OFF -> "The email service refused the server's sending key. Ask your administrator to check the email"
                            + " settings, then retry the failed messages.";
                    default -> "The SMS account became inactive during sending. Retry the failed messages once it is active.";
                };
                jdbcTemplate.update("update m_comm_campaign set last_error = ? where id = ?", message, id);
            }, id);
            return;
        }
    }

    /** Placeholder values for a batch of deliveries, keyed like recipientKey: clients, staff and system users. */
    private Map<String, Map<String, Object>> scopes(final List<Map<String, Object>> batch) {
        final List<Long> clients = new java.util.ArrayList<>();
        final List<Long> staff = new java.util.ArrayList<>();
        final List<Long> users = new java.util.ArrayList<>();
        for (final Map<String, Object> row : batch) {
            final String type = StringUtils.defaultIfBlank((String) row.get("recipient_type"), AudienceService.CLIENT);
            final Long recipient = row.get("recipient_id") == null ? null : ((Number) row.get("recipient_id")).longValue();
            if (AudienceService.STAFF.equals(type)) {
                staff.add(recipient);
            } else if (AudienceService.USER.equals(type)) {
                users.add(recipient);
            } else {
                clients.add(((Number) row.get("client_id")).longValue());
            }
        }
        final Map<String, Map<String, Object>> scopes = new java.util.HashMap<>(renderer.staffScopes(staff, users));
        renderer.scopes(clients).forEach((clientId, scope) -> scopes.put(AudienceService.CLIENT + ":" + clientId, scope));
        return scopes;
    }

    /** "CLIENT:7", "STAFF:3" or "USER:5"; deliveries from before staff audiences are clients. */
    private static String recipientKey(final Map<String, Object> row) {
        final String type = StringUtils.defaultIfBlank((String) row.get("recipient_type"), AudienceService.CLIENT);
        if (AudienceService.CLIENT.equals(type)) {
            return AudienceService.CLIENT + ":" + ((Number) row.get("client_id")).longValue();
        }
        return type + ":" + ((Number) row.get("recipient_id")).longValue();
    }

    /**
     * Ends the batch early: {@code failRest} fails every queued message for that reason; {@code waiting} leaves them
     * queued for a later job run and shows why.
     */
    private record Stop(DeliveryReason failRest, String waiting) {
    }

    /**
     * @return why the SMS account stops all further sending, else null
     */
    private Stop sendOne(final Long deliveryId, final Map<String, Object> scope, final Mustache mustache, final String countryCode,
            final String senderId) {
        final LocalDateTime now = CommSupport.nowUtc();
        if (scope == null) {
            jdbcTemplate.update(
                    "update m_comm_delivery set status = ?, reason = ?, attempts = attempts + 1, last_attempt_on_utc = ? where id = ?",
                    DeliveryStatus.FAILED.name(), DeliveryReason.SEND_ERROR.name(), now, deliveryId);
            return null;
        }
        @SuppressWarnings("unchecked")
        final String mobile = (String) ((Map<String, Object>) scope.get("client")).get("phone");
        final String phone = PhoneNumbers.international(mobile, countryCode);
        if (phone == null) {
            final DeliveryReason reason = PhoneNumbers.isBlank(mobile) ? DeliveryReason.NO_PHONE : DeliveryReason.INVALID_PHONE;
            jdbcTemplate.update(
                    "update m_comm_delivery set status = ?, reason = ?, address = ?, attempts = attempts + 1, last_attempt_on_utc = ?"
                            + " where id = ?",
                    DeliveryStatus.FAILED.name(), reason.name(), StringUtils.abbreviate(mobile, 150), now, deliveryId);
            return null;
        }
        final SmsMessageData message = new SmsMessageData(phone, renderer.render(mustache, scope), null);
        final SmsSendOutcome outcome = smsService.sendCampaignSms(message, senderId);
        final DeliveryReason reason = switch (outcome.status()) {
            case SENT -> null;
            case NO_CREDIT -> DeliveryReason.NO_SMS_CREDIT;
            case SMS_OFF -> DeliveryReason.SMS_OFF;
            case ACCOUNT_INACTIVE -> DeliveryReason.SMS_ACCOUNT_INACTIVE;
            case GATEWAY_REFUSED -> DeliveryReason.GATEWAY_REFUSED;
            case ERROR -> DeliveryReason.SEND_ERROR;
        };
        jdbcTemplate.update(
                "update m_comm_delivery set status = ?, reason = ?, address = ?, message = ?, provider_response = ?,"
                        + " attempts = attempts + 1, last_attempt_on_utc = ? where id = ?",
                outcome.isSent() ? DeliveryStatus.SENT.name() : DeliveryStatus.FAILED.name(), reason == null ? null : reason.name(), phone,
                message.getMessage(), StringUtils.abbreviate(outcome.gatewayResponse(), 255), now, deliveryId);
        return reason == DeliveryReason.NO_SMS_CREDIT || reason == DeliveryReason.SMS_OFF || reason == DeliveryReason.SMS_ACCOUNT_INACTIVE
                ? new Stop(reason, null)
                : null;
    }

    /**
     * Sends one campaign email through Brevo and keeps Brevo's message id, which its webhook events refer to.
     *
     * @return why sending has to stop for now, else null
     */
    private Stop sendOneEmail(final Long deliveryId, final Map<String, Object> scope, final Mustache body, final Mustache subject) {
        final LocalDateTime now = CommSupport.nowUtc();
        if (scope == null) {
            fail(deliveryId, DeliveryReason.SEND_ERROR, null, null, now);
            return null;
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> client = (Map<String, Object>) scope.get("client");
        final String raw = (String) client.get("email");
        final String address = EmailAddresses.clean(raw);
        if (address == null) {
            fail(deliveryId, StringUtils.isBlank(raw) ? DeliveryReason.NO_EMAIL : DeliveryReason.INVALID_EMAIL, raw, null, now);
            return null;
        }
        final String html = renderer.render(body, scope);
        final String title = StringUtils.abbreviate(renderer.render(subject, scope), 250);
        final BrevoEmailClient.Result result = emailClient.sendHtml(address, (String) client.get("fullName"), title, html);
        final int http = result.httpStatus();
        if (http == 402 || http == 429) {
            // Brevo's allowance (402) or rate limit (429): try this one again later rather than calling it failed
            log.warn("Campaign email {} held back: the email service answered HTTP {}", deliveryId, http);
            return new Stop(null, http == 429 ? "Waiting for the email service: it asked to slow down. Sending continues shortly."
                    : "Waiting for the email service: its sending allowance is used up. Sending continues once it is available again.");
        }
        final DeliveryReason reason;
        if (result.isAccepted()) {
            reason = null;
        } else if (http == 401 || http == 403) {
            reason = DeliveryReason.EMAIL_OFF;
        } else if (http == 400 && mentionsInvalidEmail(result.response())) {
            reason = DeliveryReason.INVALID_EMAIL;
        } else if (http / 100 == 4) {
            reason = DeliveryReason.EMAIL_REFUSED;
        } else {
            reason = DeliveryReason.SEND_ERROR;
        }
        jdbcTemplate.update(
                "update m_comm_delivery set status = ?, reason = ?, address = ?, subject = ?, message = ?, provider_response = ?,"
                        + " provider_message_id = ?, provider_event = null, provider_event_on_utc = null, attempts = attempts + 1,"
                        + " last_attempt_on_utc = ? where id = ?",
                reason == null ? DeliveryStatus.SENT.name() : DeliveryStatus.FAILED.name(), reason == null ? null : reason.name(), address,
                title, html, StringUtils.abbreviate(result.response(), 255), StringUtils.abbreviate(result.messageId(), 150), now,
                deliveryId);
        return reason == DeliveryReason.EMAIL_OFF ? new Stop(reason, null) : null;
    }

    /** Brevo's 400 answer for a bad recipient, e.g. "email is not valid in to". */
    private static boolean mentionsInvalidEmail(final String response) {
        final String text = StringUtils.defaultString(response).toLowerCase(Locale.ROOT);
        return text.contains("email") && text.contains("valid");
    }

    private void fail(final Long deliveryId, final DeliveryReason reason, final String address, final String response,
            final LocalDateTime now) {
        jdbcTemplate.update(
                "update m_comm_delivery set status = ?, reason = ?, address = coalesce(?, address), provider_response = ?,"
                        + " attempts = attempts + 1, last_attempt_on_utc = ? where id = ?",
                DeliveryStatus.FAILED.name(), reason.name(), StringUtils.abbreviate(address, 150), response, now, deliveryId);
    }

    /**
     * Campaign emails still allowed today (the tenant's day) under campaign-email-daily-limit; no limit while it is
     * switched off.
     */
    public int emailAllowanceToday() {
        final List<Map<String, Object>> setting = jdbcTemplate
                .queryForList("select enabled, value from c_configuration where name = 'campaign-email-daily-limit'");
        if (setting.isEmpty() || !Boolean.TRUE.equals(setting.get(0).get("enabled")) || setting.get(0).get("value") == null) {
            return Integer.MAX_VALUE;
        }
        final long limit = number(setting.get(0).get("value"));
        final ZoneId zone = DateUtils.getDateTimeZoneOfTenant();
        final LocalDateTime dayStartUtc = LocalDate.now(zone).atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        final Long sentToday = jdbcTemplate.queryForObject(
                "select count(*) from m_comm_delivery where channel = ?"
                        + " and provider_message_id is not null and last_attempt_on_utc >= ?",
                Long.class, CommChannel.EMAIL.name(), dayStartUtc);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, limit - (sentToday == null ? 0 : sentToday)));
    }

    /** Why an email run can't start yet, or null when the server can send email. */
    private String emailWaitingReason() {
        return emailClient.isEnabled() ? null
                : "Waiting: email sending is not set up on the server. Ask your administrator to set up the email service.";
    }

    /** Closes the run; a repeating campaign is scheduled for its next run, any other one is completed. */
    private void finish(final Long id) {
        jdbcTemplate.update("update m_comm_run set finished_on_utc = ? where campaign_id = ? and finished_on_utc is null",
                CommSupport.nowUtc(), id);
        final Map<String, Object> counts = jdbcTemplate.queryForMap(
                "select sum(case when d.status = ? then 1 else 0 end) sent,"
                        + " sum(case when d.status = ? then 1 else 0 end) failed, sum(case when d.status = ? then 1 else 0 end) skipped"
                        + " from m_comm_delivery d where d.run_id = (select max(r.id) from m_comm_run r where r.campaign_id = ?)",
                DeliveryStatus.SENT.name(), DeliveryStatus.FAILED.name(), DeliveryStatus.SKIPPED.name(), id);
        final Integer runsDone = jdbcTemplate.queryForObject("select count(*) from m_comm_run where campaign_id = ?", Integer.class, id);
        final CampaignSchedule schedule = writeService.schedule(id);
        final LocalDateTime nextRun = schedule.nextRun(runsDone == null ? 0 : runsDone, DateUtils.getLocalDateTimeOfTenant());
        final String summary = number(counts.get("sent")) + " sent, " + number(counts.get("failed")) + " failed, "
                + number(counts.get("skipped")) + " skipped";
        if (nextRun != null) {
            final LocalDateTime start = schedule.firstAllowed(nextRun);
            jdbcTemplate.update("update m_comm_campaign set status = ?, next_run_at = ? where id = ? and status = ?",
                    CampaignStatus.SCHEDULED.name(), start, id, CampaignStatus.SENDING.name());
            writeService.history(id, "RUN_FINISHED", summary + ". Next run " + start.format(CommSupport.LOCAL_TIME) + ".");
        } else {
            jdbcTemplate.update("update m_comm_campaign set status = ?, next_run_at = null where id = ? and status = ?",
                    CampaignStatus.COMPLETED.name(), id, CampaignStatus.SENDING.name());
            writeService.history(id, "RUN_FINISHED", summary + (schedule.type() == ScheduleType.REPEAT ? ". That was the last run." : ""));
        }
    }

    /** The marketing sender ID, when the campaign is Sales and marketing and one is set and switched on; else null. */
    private String senderId(final Long id) {
        final String purpose = jdbcTemplate.queryForObject("select purpose from m_comm_campaign where id = ?", String.class, id);
        if (!CommPurpose.MARKETING.name().equals(purpose)) {
            return null;
        }
        final List<Map<String, Object>> setting = jdbcTemplate
                .queryForList("select enabled, string_value from c_configuration where name = 'sms-marketing-sender-id'");
        if (setting.isEmpty() || !Boolean.TRUE.equals(setting.get(0).get("enabled"))) {
            return null;
        }
        return StringUtils.trimToNull((String) setting.get(0).get("string_value"));
    }

    /** Why the run can't start yet, or null when SMS is on and the wallet covers it. */
    private String smsWaitingReason(final long toSend) {
        final List<Boolean> switchedOn = jdbcTemplate
                .queryForList("select enabled from c_configuration where name = 'enable-sms-notifications'", Boolean.class);
        if (switchedOn.isEmpty() || !Boolean.TRUE.equals(switchedOn.get(0))) {
            return "Waiting: SMS notifications are switched off (enable-sms-notifications).";
        }
        final List<Map<String, Object>> account = jdbcTemplate
                .queryForList("select sms_total_balance, is_active from m_sms_notification_account" + " where id = 1");
        if (account.isEmpty() || !Boolean.TRUE.equals(account.get(0).get("is_active"))) {
            return "Waiting: the SMS account is inactive.";
        }
        final long balance = number(account.get(0).get("sms_total_balance"));
        if (balance < toSend) {
            return "Waiting for SMS credit: this run needs " + toSend + " SMS and the wallet has " + balance
                    + ". It starts after a top-up.";
        }
        return null;
    }

    private <T> T callInNewTransaction(final java.util.function.Supplier<T> work, final Long campaignId) {
        final TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return template.execute(status -> work.get());
        } catch (RuntimeException e) {
            log.error("Campaign {}: {}", campaignId, e.getMessage(), e);
            return null;
        }
    }

    private void runInNewTransaction(final Runnable work, final Long campaignId) {
        callInNewTransaction(() -> {
            work.run();
            return null;
        }, campaignId);
    }

    private static long number(final Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

}
