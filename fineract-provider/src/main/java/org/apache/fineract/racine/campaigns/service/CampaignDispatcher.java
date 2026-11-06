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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.notification.data.SmsMessageData;
import org.apache.fineract.notification.data.SmsSendOutcome;
import org.apache.fineract.notification.service.SmsNotificationWritePlatformService;
import org.apache.fineract.racine.campaigns.domain.CampaignStatus;
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
 * an SMS that already went out (and was paid for).
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
        final List<AudienceService.Recipient> recipients = audienceService.recipients(
                AudienceService.parse((String) campaign.get("audience_json")), (String) campaign.get("hierarchy"),
                CommPurpose.valueOf((String) campaign.get("purpose")));
        final long toSend = recipients.stream().filter(recipient -> recipient.skipReason() == null).count();
        final String waiting = smsWaitingReason(toSend);
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
        final String channel = (String) campaign.get("channel");
        jdbcTemplate.batchUpdate("insert into m_comm_delivery (run_id, campaign_id, client_id, channel, address, status, reason, attempts,"
                + " created_on_utc) values (?, ?, ?, ?, ?, ?, ?, 0, ?)", new BatchPreparedStatementSetter() {

                    @Override
                    public void setValues(final java.sql.PreparedStatement ps, final int i) throws java.sql.SQLException {
                        final AudienceService.Recipient recipient = recipients.get(i);
                        ps.setLong(1, runId);
                        ps.setLong(2, id);
                        ps.setLong(3, recipient.clientId());
                        ps.setString(4, channel);
                        ps.setString(5, StringUtils.abbreviate(recipient.phone() != null ? recipient.phone() : recipient.mobileNo(), 150));
                        ps.setString(6, recipient.skipReason() == null ? DeliveryStatus.QUEUED.name() : DeliveryStatus.SKIPPED.name());
                        ps.setString(7, recipient.skipReason() == null ? null : recipient.skipReason().name());
                        ps.setObject(8, now);
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
        final List<Map<String, Object>> queued = jdbcTemplate.queryForList(
                "select id, client_id from m_comm_delivery where campaign_id = ?" + " and status = ? order by id", id,
                DeliveryStatus.QUEUED.name());
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
        final String text = jdbcTemplate.queryForObject("select message_text from m_comm_campaign where id = ?", String.class, id);
        final String senderId = senderId(id);
        final Mustache mustache = renderer.compile(text, "campaign-" + id);
        final List<Map<String, Object>> batch = queued.subList(0, Math.min(BATCH_SIZE, queued.size()));
        final Map<Long, Map<String, Object>> scopes = renderer
                .scopes(batch.stream().map(row -> ((Number) row.get("client_id")).longValue()).toList());
        final String countryCode = environment.getProperty("sms.default.country.code", "256");
        for (final Map<String, Object> row : batch) {
            // Stop between messages if staff paused or stopped the campaign
            final String status = jdbcTemplate.queryForObject("select status from m_comm_campaign where id = ?", String.class, id);
            if (!CampaignStatus.SENDING.name().equals(status)) {
                return;
            }
            final Long deliveryId = ((Number) row.get("id")).longValue();
            final Map<String, Object> scope = scopes.get(((Number) row.get("client_id")).longValue());
            final DeliveryReason stopReason = callInNewTransaction(() -> sendOne(deliveryId, scope, mustache, countryCode, senderId), id);
            if (stopReason != null) {
                // Nothing else can go out until the SMS account is fixed: leave the rest retryable
                runInNewTransaction(() -> {
                    jdbcTemplate.update("update m_comm_delivery set status = ?, reason = ? where campaign_id = ? and status = ?",
                            DeliveryStatus.FAILED.name(), stopReason.name(), id, DeliveryStatus.QUEUED.name());
                    final String message = switch (stopReason) {
                        case NO_SMS_CREDIT -> "SMS credit ran out during sending. Top up, then retry the failed messages.";
                        case SMS_OFF -> "SMS was switched off during sending. Switch it on, then retry the failed messages.";
                        default -> "The SMS account became inactive during sending. Retry the failed messages once it is active.";
                    };
                    jdbcTemplate.update("update m_comm_campaign set last_error = ? where id = ?", message, id);
                }, id);
                return;
            }
        }
    }

    /**
     * @return a reason when the SMS account stops all further sending, else null
     */
    private DeliveryReason sendOne(final Long deliveryId, final Map<String, Object> scope, final Mustache mustache,
            final String countryCode, final String senderId) {
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
                ? reason
                : null;
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
