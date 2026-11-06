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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.campaigns.data.AudienceFilter;
import org.apache.fineract.racine.campaigns.data.CommTemplateData;
import org.apache.fineract.racine.campaigns.data.MessageCheckData;
import org.apache.fineract.racine.campaigns.domain.CampaignStatus;
import org.apache.fineract.racine.campaigns.domain.DeliveryReason;
import org.apache.fineract.racine.campaigns.domain.DeliveryStatus;
import org.apache.fineract.racine.campaigns.domain.RepeatStop;
import org.apache.fineract.racine.campaigns.domain.RepeatUnit;
import org.apache.fineract.racine.campaigns.domain.ScheduleType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campaign life cycle. Every action is a Fineract command, so maker-checker applies when switched on. Nothing is sent
 * until the campaign is approved; anyone with APPROVE_COMMCAMPAIGN may approve, including the person who created it.
 */
@Service
@RequiredArgsConstructor
public class CampaignWriteService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final CommTemplateService templateService;

    /** The parts of a campaign row the life cycle needs. */
    record Row(Long id, String status, String scheduleType, LocalDateTime scheduledAt, Long createdBy, Long lastModifiedBy,
            String officeHierarchy, String messageText) {
    }

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final JsonObject json = command.parsedJson().getAsJsonObject();
        final String name = name(command, null);
        final CommTemplateData template = template(command.longValueOfParameterNamed("templateId"));
        final String audience = audience(json, null);
        final ScheduleInput schedule = schedule(command);
        final Long userId = context.authenticatedUser().getId();
        final LocalDateTime now = CommSupport.nowUtc();
        final Map<String, Object> values = new java.util.HashMap<>();
        values.put("name", name);
        values.put("template_id", template.id());
        values.put("channel", template.channel());
        values.put("purpose", template.purpose());
        values.put("tag", template.tag());
        values.put("message_text", template.text());
        values.put("email_subject", template.emailSubject());
        values.put("audience_json", audience);
        values.putAll(schedule.columns());
        values.put("status", CampaignStatus.DRAFT.name());
        values.put("office_id", context.authenticatedUser().getOffice().getId());
        values.put("created_by", userId);
        values.put("created_on_utc", now);
        values.put("last_modified_by", userId);
        values.put("last_modified_on_utc", now);
        final Long id = new SimpleJdbcInsert(jdbcTemplate).withTableName("m_comm_campaign")
                .usingColumns(values.keySet().toArray(String[]::new)).usingGeneratedKeyColumns("id").executeAndReturnKey(values)
                .longValue();
        history(id, "CREATED", null);
        return result(command, id);
    }

    @Transactional
    public CommandProcessingResult update(final Long id, final JsonCommand command) {
        final Row row = load(id);
        final CampaignStatus status = CampaignStatus.valueOf(row.status());
        if (!CampaignStatus.EDITABLE.contains(status)) {
            throw rule("not.editable", "A campaign that is " + label(status) + " can't be changed.");
        }
        final JsonObject json = command.parsedJson().getAsJsonObject();
        final CommTemplateData template = template(command.parameterExists("templateId") ? command.longValueOfParameterNamed("templateId")
                : jdbcTemplate.queryForObject("select template_id from m_comm_campaign where id = ?", Long.class, id));
        // The schedule is sent whole; without scheduleType the stored schedule is kept
        final ScheduleInput schedule = command.parameterExists("scheduleType") ? schedule(command) : null;
        // An edit after submission or approval needs a fresh approval; a rejected campaign becomes a draft again
        final CampaignStatus next = status == CampaignStatus.PENDING_APPROVAL || status == CampaignStatus.SCHEDULED
                ? CampaignStatus.PENDING_APPROVAL
                : CampaignStatus.DRAFT;
        jdbcTemplate.update(
                "update m_comm_campaign set name = ?, template_id = ?, channel = ?, purpose = ?, tag = ?, message_text = ?,"
                        + " email_subject = ?, audience_json = ?, status = ?, next_run_at = null, last_error = null,"
                        + " approved_by = null, approved_on_utc = null, last_modified_by = ?, last_modified_on_utc = ? where id = ?",
                name(command, id), template.id(), template.channel(), template.purpose(), template.tag(), template.text(),
                template.emailSubject(), audience(json, id), next.name(), context.authenticatedUser().getId(), CommSupport.nowUtc(), id);
        if (schedule != null) {
            final StringBuilder sql = new StringBuilder("update m_comm_campaign set ");
            final java.util.List<Object> params = new ArrayList<>();
            schedule.columns().forEach((column, value) -> {
                sql.append(params.isEmpty() ? "" : ", ").append(column).append(" = ?");
                params.add(value);
            });
            params.add(id);
            jdbcTemplate.update(sql.append(" where id = ?").toString(), params.toArray());
        }
        history(id, "UPDATED", next == CampaignStatus.PENDING_APPROVAL ? "Changed after submission: needs approval again" : null);
        return result(command, id);
    }

    @Transactional
    public CommandProcessingResult submit(final Long id, final JsonCommand command) {
        final Row row = load(id);
        expect(row, EnumSet.of(CampaignStatus.DRAFT, CampaignStatus.REJECTED), "submitted for approval");
        final MessageCheckData check = CommPlaceholders.check(row.messageText(), null);
        if (!check.fits()) {
            throw rule("too.long", "The message can reach " + check.longestLength() + " characters; one SMS holds " + check.limit()
                    + ". Shorten the template first.");
        }
        checkSendTime(row);
        jdbcTemplate.update(
                "update m_comm_campaign set status = ?, submitted_by = ?, submitted_on_utc = ?, rejected_by = null,"
                        + " rejected_on_utc = null, rejection_reason = null where id = ?",
                CampaignStatus.PENDING_APPROVAL.name(), context.authenticatedUser().getId(), CommSupport.nowUtc(), id);
        history(id, "SUBMITTED", null);
        return result(command, id);
    }

    @Transactional
    public CommandProcessingResult approve(final Long id, final JsonCommand command) {
        final Row row = load(id);
        expect(row, EnumSet.of(CampaignStatus.PENDING_APPROVAL), "approved");
        final Long userId = context.authenticatedUser().getId();
        checkSendTime(row);
        final CampaignSchedule schedule = schedule(id);
        final LocalDateTime nextRun = schedule.firstAllowed(
                ScheduleType.NOW.name().equals(row.scheduleType()) ? DateUtils.getLocalDateTimeOfTenant() : row.scheduledAt());
        jdbcTemplate
                .update("update m_comm_campaign set status = ?, approved_by = ?, approved_on_utc = ?, next_run_at = ?, last_error = null"
                        + " where id = ?", CampaignStatus.SCHEDULED.name(), userId, CommSupport.nowUtc(), nextRun, id);
        history(id, "APPROVED", StringUtils.trimToNull(command.stringValueOfParameterNamedAllowingNull("note")));
        return result(command, id);
    }

    @Transactional
    public CommandProcessingResult reject(final Long id, final JsonCommand command) {
        final Row row = load(id);
        expect(row, EnumSet.of(CampaignStatus.PENDING_APPROVAL), "rejected");
        final String reason = StringUtils.trimToNull(command.stringValueOfParameterNamedAllowingNull("reason"));
        if (reason == null || reason.length() > 500) {
            throw CommTemplateService.invalid("reason", "Give a reason for rejecting, up to 500 characters.");
        }
        jdbcTemplate.update(
                "update m_comm_campaign set status = ?, rejected_by = ?, rejected_on_utc = ?, rejection_reason = ? where id = ?",
                CampaignStatus.REJECTED.name(), context.authenticatedUser().getId(), CommSupport.nowUtc(), reason, id);
        history(id, "REJECTED", reason);
        return result(command, id);
    }

    @Transactional
    public CommandProcessingResult pause(final Long id, final JsonCommand command) {
        final Row row = load(id);
        expect(row, EnumSet.of(CampaignStatus.SCHEDULED, CampaignStatus.SENDING), "paused");
        jdbcTemplate.update("update m_comm_campaign set status = ? where id = ?", CampaignStatus.PAUSED.name(), id);
        history(id, "PAUSED", null);
        return result(command, id);
    }

    @Transactional
    public CommandProcessingResult resume(final Long id, final JsonCommand command) {
        final Row row = load(id);
        expect(row, EnumSet.of(CampaignStatus.PAUSED), "resumed");
        final Integer openRuns = jdbcTemplate
                .queryForObject("select count(*) from m_comm_run where campaign_id = ? and finished_on_utc is null", Integer.class, id);
        final CampaignStatus next = openRuns != null && openRuns > 0 ? CampaignStatus.SENDING : CampaignStatus.SCHEDULED;
        jdbcTemplate.update("update m_comm_campaign set status = ? where id = ?", next.name(), id);
        history(id, "RESUMED", null);
        return result(command, id);
    }

    @Transactional
    public CommandProcessingResult stop(final Long id, final JsonCommand command) {
        final Row row = load(id);
        expect(row, EnumSet.of(CampaignStatus.PENDING_APPROVAL, CampaignStatus.SCHEDULED, CampaignStatus.SENDING, CampaignStatus.PAUSED),
                "stopped");
        // Messages already handed to the gateway stay sent; the ones still waiting are not sent
        jdbcTemplate.update("update m_comm_delivery set status = ?, reason = ? where campaign_id = ? and status = ?",
                DeliveryStatus.SKIPPED.name(), DeliveryReason.STOPPED.name(), id, DeliveryStatus.QUEUED.name());
        jdbcTemplate.update("update m_comm_run set finished_on_utc = ? where campaign_id = ? and finished_on_utc is null",
                CommSupport.nowUtc(), id);
        jdbcTemplate.update("update m_comm_campaign set status = ?, next_run_at = null where id = ?", CampaignStatus.STOPPED.name(), id);
        history(id, "STOPPED", StringUtils.trimToNull(command.stringValueOfParameterNamedAllowingNull("note")));
        return result(command, id);
    }

    /**
     * Queues a run's failed messages, and skipped ones for a missing or invalid phone, again. A retry reads the
     * client's current phone, so a corrected record is picked up.
     */
    @Transactional
    public CommandProcessingResult retry(final Long id, final Long runId, final JsonCommand command) {
        final Row row = load(id);
        expect(row, EnumSet.of(CampaignStatus.COMPLETED, CampaignStatus.SENDING, CampaignStatus.PAUSED, CampaignStatus.SCHEDULED),
                "retried");
        final Integer runFound = jdbcTemplate.queryForObject("select count(*) from m_comm_run where id = ? and campaign_id = ?",
                Integer.class, runId, id);
        if (runFound == null || runFound == 0) {
            throw new ResourceNotFoundException("error.msg.comm.run.not.found", "Run " + runId + " of campaign " + id + " does not exist.",
                    new Object[] { runId });
        }
        final List<String> failedReasons = new ArrayList<>();
        for (DeliveryReason reason : DeliveryReason.values()) {
            if (reason.isRetryable()) {
                failedReasons.add(reason.name());
            }
        }
        final MapSqlParameterSource params = new MapSqlParameterSource().addValue("runId", runId)
                .addValue("queued", DeliveryStatus.QUEUED.name()).addValue("failed", DeliveryStatus.FAILED.name())
                .addValue("skipped", DeliveryStatus.SKIPPED.name()).addValue("failedReasons", failedReasons)
                .addValue("skippedReasons", List.of(DeliveryReason.NO_PHONE.name(), DeliveryReason.INVALID_PHONE.name()));
        String only = "";
        final JsonElement ids = command.parsedJson().getAsJsonObject().get("deliveryIds");
        if (ids != null && ids.isJsonArray() && !ids.getAsJsonArray().isEmpty()) {
            final List<Long> chosen = new ArrayList<>();
            ids.getAsJsonArray().forEach(element -> chosen.add(element.getAsLong()));
            params.addValue("ids", chosen);
            only = " and id in (:ids)";
        }
        final int queued = new NamedParameterJdbcTemplate(jdbcTemplate).update("update m_comm_delivery set status = :queued, reason = null"
                + " where run_id = :runId and ((status = :failed and reason in (:failedReasons)) or (status = :skipped and reason in (:skippedReasons)))"
                + only, params);
        if (queued == 0) {
            throw rule("nothing.to.retry", "There is nothing to retry in this run.");
        }
        jdbcTemplate.update("update m_comm_run set finished_on_utc = null where id = ?", runId);
        if (CampaignStatus.COMPLETED.name().equals(row.status()) || CampaignStatus.SCHEDULED.name().equals(row.status())) {
            jdbcTemplate.update("update m_comm_campaign set status = ? where id = ?", CampaignStatus.SENDING.name(), id);
        }
        history(id, "RETRIED", queued + " message" + (queued == 1 ? "" : "s") + " queued again from run " + runNumber(runId));
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id).withSubEntityId(runId)
                .with(Map.<String, Object>of("queued", queued)).build();
    }

    void history(final Long campaignId, final String action, final String note) {
        Long userId = null;
        try {
            userId = context.authenticatedUser().getId();
        } catch (RuntimeException e) {
            // The scheduler acts on its own, without a user
        }
        jdbcTemplate.update(
                "insert into m_comm_campaign_history (campaign_id, action, note, user_id, created_on_utc) values (?, ?, ?, ?, ?)",
                campaignId, action, StringUtils.abbreviate(note, 500), userId, CommSupport.nowUtc());
    }

    /** The campaign, if the staff member's office covers it. */
    Row load(final Long id) {
        final List<Row> rows = jdbcTemplate.query(
                "select c.id, c.status, c.schedule_type, c.scheduled_at, c.created_by, c.last_modified_by,"
                        + " o.hierarchy, c.message_text from m_comm_campaign c join m_office o on o.id = c.office_id where c.id = ? and o.hierarchy like ?",
                (rs, n) -> new Row(rs.getLong("id"), rs.getString("status"), rs.getString("schedule_type"),
                        rs.getObject("scheduled_at", LocalDateTime.class), rs.getLong("created_by"), rs.getLong("last_modified_by"),
                        rs.getString("hierarchy"), rs.getString("message_text")),
                id, context.officeHierarchy() + "%");
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("error.msg.comm.campaign.not.found", "Campaign " + id + " does not exist.",
                    new Object[] { id });
        }
        return rows.get(0);
    }

    private int runNumber(final Long runId) {
        final Integer number = jdbcTemplate.queryForObject("select run_number from m_comm_run where id = ?", Integer.class, runId);
        return number == null ? 0 : number;
    }

    private String name(final JsonCommand command, final Long id) {
        final String name = command.parameterExists("name") || id == null ? command.stringValueOfParameterNamedAllowingNull("name")
                : jdbcTemplate.queryForObject("select name from m_comm_campaign where id = ?", String.class, id);
        if (StringUtils.isBlank(name) || name.trim().length() > 150) {
            throw CommTemplateService.invalid("name", "Give the campaign a name of up to 150 characters.");
        }
        return name.trim();
    }

    private CommTemplateData template(final Long templateId) {
        if (templateId == null) {
            throw CommTemplateService.invalid("templateId", "Choose a template.");
        }
        final CommTemplateData template = templateService.retrieveOne(templateId);
        if (template.archived()) {
            throw CommTemplateService.invalid("templateId", "That template is archived. Choose another one.");
        }
        return template;
    }

    /** Audience filters as JSON, checked by building the query once. */
    private String audience(final JsonObject json, final Long id) {
        if (!json.has("audience")) {
            if (id == null) {
                return "[]";
            }
            return jdbcTemplate.queryForObject("select audience_json from m_comm_campaign where id = ?", String.class, id);
        }
        final List<AudienceFilter> filters = AudienceService.parse(json.get("audience").toString());
        AudienceQueryBuilder.of(filters, context.officeHierarchy(), DateUtils.getBusinessLocalDate());
        return AudienceService.toJson(filters);
    }

    /** A checked schedule and the m_comm_campaign columns it is stored in. */
    record ScheduleInput(CampaignSchedule schedule, Map<String, Object> columns) {
    }

    /** The stored schedule of a campaign. */
    CampaignSchedule schedule(final Long id) {
        return CampaignSchedule
                .of(jdbcTemplate.queryForMap("select " + CampaignSchedule.COLUMNS + " from m_comm_campaign where id = ?", id));
    }

    /**
     * Reads and checks the schedule: scheduleType (NOW, ONCE or REPEAT), scheduledAt (yyyy-MM-dd HH:mm) for ONCE and
     * REPEAT; for REPEAT repeatEvery, repeatUnit (DAYS, WEEKS, MONTHS) and repeatStop (NEVER, ON_DATE with repeatUntil
     * yyyy-MM-dd, AFTER_RUNS with repeatMaxRuns); optional quietFrom and quietTo (HH:mm).
     */
    private ScheduleInput schedule(final JsonCommand command) {
        final ScheduleType type = CommTemplateService.parse(ScheduleType.class,
                StringUtils.defaultIfBlank(command.stringValueOfParameterNamedAllowingNull("scheduleType"), ScheduleType.NOW.name()),
                "scheduleType", "Choose to send now, once at a set time, or on a repeat.");
        LocalDateTime start = null;
        if (type != ScheduleType.NOW) {
            final String value = command.stringValueOfParameterNamedAllowingNull("scheduledAt");
            if (StringUtils.isBlank(value)) {
                throw CommTemplateService.invalid("scheduledAt", "Choose the date and time of the first send.");
            }
            try {
                start = LocalDateTime.parse(value.trim(), CommSupport.LOCAL_TIME);
            } catch (DateTimeParseException e) {
                throw CommTemplateService.invalid("scheduledAt", "Give the send time as yyyy-MM-dd HH:mm.", e);
            }
        }
        Integer every = null;
        RepeatUnit unit = null;
        RepeatStop stop = null;
        LocalDate until = null;
        Integer maxRuns = null;
        if (type == ScheduleType.REPEAT) {
            every = integer(command, "repeatEvery", 1, 365, "Repeat every 1 to 365 days, weeks or months.");
            unit = CommTemplateService.parse(RepeatUnit.class, command.stringValueOfParameterNamedAllowingNull("repeatUnit"), "repeatUnit",
                    "Repeat in DAYS, WEEKS or MONTHS.");
            stop = CommTemplateService.parse(RepeatStop.class,
                    StringUtils.defaultIfBlank(command.stringValueOfParameterNamedAllowingNull("repeatStop"), RepeatStop.NEVER.name()),
                    "repeatStop", "Stop NEVER, ON_DATE or AFTER_RUNS.");
            if (stop == RepeatStop.ON_DATE) {
                try {
                    until = LocalDate.parse(StringUtils.trimToEmpty(command.stringValueOfParameterNamedAllowingNull("repeatUntil")));
                } catch (DateTimeParseException e) {
                    throw CommTemplateService.invalid("repeatUntil", "Give the last date to send as yyyy-MM-dd.", e);
                }
                if (until.isBefore(start.toLocalDate())) {
                    throw CommTemplateService.invalid("repeatUntil", "The last date must be on or after the first send.");
                }
            } else if (stop == RepeatStop.AFTER_RUNS) {
                maxRuns = integer(command, "repeatMaxRuns", 1, 1000, "Stop after 1 to 1000 runs.");
            }
        }
        final LocalTime quietFrom = clock(command, "quietFrom");
        final LocalTime quietTo = clock(command, "quietTo");
        if ((quietFrom == null) != (quietTo == null) || (quietFrom != null && !quietFrom.isBefore(quietTo))) {
            throw CommTemplateService.invalid("quietFrom",
                    "Quiet hours need a start and an end, and the start must come first (e.g. 08:00 to 20:00).");
        }
        final Map<String, Object> columns = new java.util.LinkedHashMap<>();
        columns.put("schedule_type", type.name());
        columns.put("scheduled_at", start);
        columns.put("repeat_every", every);
        columns.put("repeat_unit", unit == null ? null : unit.name());
        columns.put("repeat_stop", stop == null ? null : stop.name());
        columns.put("repeat_until", until);
        columns.put("repeat_max_runs", maxRuns);
        columns.put("quiet_from", quietFrom == null ? null : quietFrom.toString());
        columns.put("quiet_to", quietTo == null ? null : quietTo.toString());
        return new ScheduleInput(new CampaignSchedule(type, start, every, unit, stop, until, maxRuns, quietFrom, quietTo), columns);
    }

    private static Integer integer(final JsonCommand command, final String name, final int min, final int max, final String message) {
        final Long value;
        try {
            value = command.longValueOfParameterNamed(name);
        } catch (RuntimeException e) {
            throw CommTemplateService.invalid(name, message, e);
        }
        if (value == null || value < min || value > max) {
            throw CommTemplateService.invalid(name, message);
        }
        return value.intValue();
    }

    private static LocalTime clock(final JsonCommand command, final String name) {
        final String value = StringUtils.trimToNull(command.stringValueOfParameterNamedAllowingNull(name));
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw CommTemplateService.invalid(name, "Give quiet hours as HH:mm, e.g. 08:00.", e);
        }
    }

    private static void checkSendTime(final Row row) {
        if (!ScheduleType.NOW.name().equals(row.scheduleType())
                && (row.scheduledAt() == null || !row.scheduledAt().isAfter(DateUtils.getLocalDateTimeOfTenant()))) {
            throw rule("send.time.passed", "The send time has passed. Edit the campaign and choose a later time.");
        }
    }

    private static void expect(final Row row, final Set<CampaignStatus> allowed, final String action) {
        final CampaignStatus status = CampaignStatus.valueOf(row.status());
        if (!allowed.contains(status)) {
            throw rule("wrong.status", "A campaign that is " + label(status) + " can't be " + action + ".");
        }
    }

    static String label(final CampaignStatus status) {
        return status.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static GeneralPlatformDomainRuleException rule(final String code, final String message) {
        return new GeneralPlatformDomainRuleException("error.msg.comm.campaign." + code, message);
    }

    private static CommandProcessingResult result(final JsonCommand command, final Long id) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id).build();
    }
}
