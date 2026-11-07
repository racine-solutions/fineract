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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.campaigns.data.CampaignData;
import org.apache.fineract.racine.campaigns.data.CampaignHistoryData;
import org.apache.fineract.racine.campaigns.data.CampaignRunData;
import org.apache.fineract.racine.campaigns.data.DeliveryData;
import org.apache.fineract.racine.campaigns.domain.DeliveryReason;
import org.apache.fineract.racine.campaigns.domain.DeliveryStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Campaigns, their runs and what happened to each message, for the staff member's offices. */
@Service
@RequiredArgsConstructor
public class CampaignReadService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PlatformSecurityContext context;

    private static final String SELECT = "select c.*, o.name office_name, t.name template_name,"
            + " cu.firstname cu_first, cu.lastname cu_last, cu.username cu_user,"
            + " mu.firstname mu_first, mu.lastname mu_last, mu.username mu_user,"
            + " su.firstname su_first, su.lastname su_last, su.username su_user,"
            + " au.firstname au_first, au.lastname au_last, au.username au_user,"
            + " ru.firstname ru_first, ru.lastname ru_last, ru.username ru_user"
            + " from m_comm_campaign c join m_office o on o.id = c.office_id join m_template t on t.id = c.template_id"
            + " left join m_appuser cu on cu.id = c.created_by left join m_appuser mu on mu.id = c.last_modified_by"
            + " left join m_appuser su on su.id = c.submitted_by left join m_appuser au on au.id = c.approved_by"
            + " left join m_appuser ru on ru.id = c.rejected_by where o.hierarchy like ?";

    /** One page of campaigns, newest first. */
    public Page<CampaignData> retrieveAll(final String status, final Integer offset, final Integer limit) {
        final List<Object> params = new ArrayList<>(List.of(context.officeHierarchy() + "%"));
        String filter = "";
        if (StringUtils.isNotBlank(status)) {
            filter = " and c.status = ?";
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        final Integer total = jdbcTemplate.queryForObject(
                "select count(*) from m_comm_campaign c join m_office o on o.id = c.office_id" + " where o.hierarchy like ?" + filter,
                Integer.class, params.toArray());
        final List<CampaignData> rows = jdbcTemplate.query(
                SELECT + filter + " order by c.id desc " + sqlGenerator.limit(size(limit), from(offset)), (rs, n) -> map(rs, false),
                params.toArray());
        return new Page<>(rows, total == null ? 0 : total);
    }

    static int size(final Integer limit) {
        return limit == null || limit <= 0 ? 25 : Math.min(limit, 500);
    }

    static int from(final Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }

    public CampaignData retrieveOne(final Long id) {
        final List<CampaignData> found = jdbcTemplate.query(SELECT + " and c.id = ?", (rs, n) -> map(rs, true),
                context.officeHierarchy() + "%", id);
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("error.msg.comm.campaign.not.found", "Campaign " + id + " does not exist.",
                    new Object[] { id });
        }
        return found.get(0);
    }

    public List<CampaignRunData> retrieveRuns(final Long campaignId) {
        retrieveOne(campaignId);
        return jdbcTemplate.query("select id from m_comm_run where campaign_id = ? order by run_number desc",
                (rs, n) -> run(rs.getLong("id")), campaignId);
    }

    public CampaignRunData retrieveRun(final Long campaignId, final Long runId) {
        retrieveOne(campaignId);
        final CampaignRunData run = run(runId);
        if (run == null || !run.campaignId().equals(campaignId)) {
            throw new ResourceNotFoundException("error.msg.comm.run.not.found",
                    "Run " + runId + " of campaign " + campaignId + " does not exist.", new Object[] { runId });
        }
        return run;
    }

    public Page<DeliveryData> retrieveDeliveries(final Long campaignId, final Long runId, final String status, final String reason,
            final Integer offset, final Integer limit) {
        retrieveRun(campaignId, runId);
        final StringBuilder where = new StringBuilder(" where d.run_id = ?");
        final List<Object> params = new ArrayList<>(List.of(runId));
        if (StringUtils.isNotBlank(status)) {
            where.append(" and d.status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.isNotBlank(reason)) {
            where.append(" and d.reason = ?");
            params.add(reason.trim().toUpperCase(Locale.ROOT));
        }
        final Integer total = jdbcTemplate.queryForObject("select count(*) from m_comm_delivery d" + where, Integer.class,
                params.toArray());
        final int size = limit == null || limit <= 0 ? 50 : Math.min(limit, 5000);
        final int from = offset == null || offset < 0 ? 0 : offset;
        final List<DeliveryData> rows = jdbcTemplate.query("select d.*, c.display_name, c.account_no from m_comm_delivery d"
                + " join m_client c on c.id = d.client_id" + where + " order by d.id " + sqlGenerator.limit(size, from), (rs, n) -> {
                    final String rowReason = rs.getString("reason");
                    final String rowStatus = rs.getString("status");
                    return new DeliveryData(rs.getLong("id"), rs.getLong("client_id"), rs.getString("display_name"),
                            rs.getString("account_no"), rs.getString("channel"), rs.getString("address"), rs.getString("message"),
                            rowStatus, rowReason, retryable(rowStatus, rowReason), rs.getInt("attempts"),
                            CommSupport.utcText(time(rs, "last_attempt_on_utc")), rs.getString("subject"), rs.getString("provider_event"),
                            CommSupport.utcText(time(rs, "provider_event_on_utc")));
                }, params.toArray());
        return new Page<>(rows, total == null ? 0 : total);
    }

    /**
     * Matches the retry command: failed for a retryable reason, or skipped for a phone that may since have been fixed.
     */
    static boolean retryable(final String status, final String reason) {
        if (reason == null) {
            return false;
        }
        final DeliveryReason parsed = DeliveryReason.valueOf(reason);
        if (DeliveryStatus.FAILED.name().equals(status)) {
            return parsed.isRetryable();
        }
        return DeliveryStatus.SKIPPED.name().equals(status) && DeliveryReason.RECHECKED_WHEN_SKIPPED.contains(parsed);
    }

    private CampaignRunData run(final Long runId) {
        final List<Object[]> runs = jdbcTemplate.query(
                "select campaign_id, run_number, started_on_utc, finished_on_utc from m_comm_run where id = ?", (rs, n) -> new Object[] {
                        rs.getLong("campaign_id"), rs.getInt("run_number"), time(rs, "started_on_utc"), time(rs, "finished_on_utc") },
                runId);
        if (runs.isEmpty()) {
            return null;
        }
        final long[] counts = new long[7];
        final Map<String, Long> reasons = new LinkedHashMap<>();
        jdbcTemplate.query("select status, reason, count(*) n from m_comm_delivery where run_id = ? group by status, reason", rs -> {
            final String status = rs.getString("status");
            final String reason = rs.getString("reason");
            final long n = rs.getLong("n");
            counts[0] += n;
            switch (DeliveryStatus.valueOf(status)) {
                case QUEUED -> counts[1] += n;
                case SENT -> counts[2] += n;
                case FAILED -> counts[3] += n;
                case SKIPPED -> counts[4] += n;
            }
            if (reason != null) {
                reasons.merge(reason, n, Long::sum);
            }
            if (DeliveryReason.SPAM.name().equals(reason)) {
                counts[6] += n;
            }
            if (retryable(status, reason)) {
                reasons.merge("_retryable", n, Long::sum);
            }
        }, runId);
        final Long delivered = jdbcTemplate.queryForObject("select count(*) from m_comm_delivery where run_id = ? and provider_event = ?",
                Long.class, runId, "DELIVERED");
        counts[5] = delivered == null ? 0 : delivered;
        final long retryable = reasons.getOrDefault("_retryable", 0L);
        reasons.remove("_retryable");
        final Object[] row = runs.get(0);
        return new CampaignRunData(runId, (Long) row[0], (Integer) row[1], CommSupport.utcText((LocalDateTime) row[2]),
                CommSupport.utcText((LocalDateTime) row[3]), counts[0], counts[1], counts[2], counts[3], counts[4], retryable, reasons,
                counts[5], counts[6]);
    }

    private CampaignData map(final ResultSet rs, final boolean full) throws SQLException {
        final Long id = rs.getLong("id");
        final List<Long> lastRun = jdbcTemplate.queryForList(
                "select id from m_comm_run where campaign_id = ? order by run_number desc " + sqlGenerator.limit(1), Long.class, id);
        final List<CampaignHistoryData> history = full ? jdbcTemplate.query("select h.id, h.action, h.note, h.created_on_utc, u.firstname,"
                + " u.lastname, u.username from m_comm_campaign_history h left join m_appuser u on u.id = h.user_id where h.campaign_id = ?"
                + " order by h.id desc",
                (h, n) -> new CampaignHistoryData(h.getLong("id"), h.getString("action"), h.getString("note"),
                        h.getString("username") == null ? "System"
                                : MarketingConsentService.userName(h.getString("firstname"), h.getString("lastname"),
                                        h.getString("username")),
                        CommSupport.utcText(time(h, "created_on_utc"))),
                id) : List.of();
        return new CampaignData(id, rs.getString("name"), rs.getLong("template_id"), rs.getString("template_name"), rs.getString("channel"),
                rs.getString("purpose"), rs.getString("tag"), rs.getString("message_text"), rs.getString("audience_json"),
                rs.getString("schedule_type"), CommSupport.localText(rs.getObject("scheduled_at", LocalDateTime.class)),
                CommSupport.localText(rs.getObject("next_run_at", LocalDateTime.class)), rs.getString("status"), rs.getString("last_error"),
                rs.getLong("office_id"), rs.getString("office_name"), user(rs, "cu"), rs.getLong("created_by"),
                CommSupport.utcText(time(rs, "created_on_utc")), user(rs, "mu"), rs.getLong("last_modified_by"), user(rs, "su"),
                CommSupport.utcText(time(rs, "submitted_on_utc")), user(rs, "au"), CommSupport.utcText(time(rs, "approved_on_utc")),
                user(rs, "ru"), CommSupport.utcText(time(rs, "rejected_on_utc")), rs.getString("rejection_reason"),
                lastRun.isEmpty() ? null : run(lastRun.get(0)), history, (Integer) rs.getObject("repeat_every", Integer.class),
                rs.getString("repeat_unit"), rs.getString("repeat_stop"),
                rs.getObject("repeat_until", java.time.LocalDate.class) == null ? null
                        : rs.getObject("repeat_until", java.time.LocalDate.class).toString(),
                (Integer) rs.getObject("repeat_max_runs", Integer.class), rs.getString("quiet_from"), rs.getString("quiet_to"),
                runsDone(id), full ? upcoming(rs) : List.of(), rs.getString("email_subject"));
    }

    private int runsDone(final Long campaignId) {
        final Integer runs = jdbcTemplate.queryForObject("select count(*) from m_comm_run where campaign_id = ?", Integer.class,
                campaignId);
        return runs == null ? 0 : runs;
    }

    /** The next few run times still to come, for a scheduled or repeating campaign. */
    private List<String> upcoming(final ResultSet rs) throws SQLException {
        final String status = rs.getString("status");
        if (!List.of("DRAFT", "PENDING_APPROVAL", "SCHEDULED", "PAUSED", "REJECTED", "SENDING").contains(status)
                || "NOW".equals(rs.getString("schedule_type"))) {
            return List.of();
        }
        final java.util.Map<String, Object> row = new java.util.HashMap<>();
        for (String column : CampaignSchedule.COLUMNS.split(",\\s*")) {
            row.put(column, rs.getObject(column));
        }
        final CampaignSchedule schedule = CampaignSchedule.of(row);
        final int done = runsDone(rs.getLong("id"));
        final LocalDateTime now = org.apache.fineract.infrastructure.core.service.DateUtils.getLocalDateTimeOfTenant();
        return schedule.upcoming(done + 5).stream().skip(done).filter(time -> time.isAfter(now)).limit(5).map(CommSupport::localText)
                .toList();
    }

    /** Campaign messages one client has received, failed or been skipped for, newest first. */
    public Page<org.apache.fineract.racine.campaigns.data.ClientCampaignMessageData> retrieveClientMessages(final Long clientId,
            final Integer offset, final Integer limit) {
        final Integer client = jdbcTemplate.queryForObject(
                "select count(*) from m_client c join m_office o on o.id = c.office_id" + " where c.id = ? and o.hierarchy like ?",
                Integer.class, clientId, context.officeHierarchy() + "%");
        if (client == null || client == 0) {
            throw new ResourceNotFoundException("error.msg.client.id.invalid", "Client " + clientId + " does not exist.",
                    new Object[] { clientId });
        }
        final Integer total = jdbcTemplate.queryForObject("select count(*) from m_comm_delivery where client_id = ?", Integer.class,
                clientId);
        final List<org.apache.fineract.racine.campaigns.data.ClientCampaignMessageData> rows = jdbcTemplate.query(
                "select d.*, c.name campaign_name, r.run_number from m_comm_delivery d join m_comm_campaign c on c.id = d.campaign_id"
                        + " join m_comm_run r on r.id = d.run_id where d.client_id = ? order by d.id desc "
                        + sqlGenerator.limit(size(limit), from(offset)),
                (rs, n) -> new org.apache.fineract.racine.campaigns.data.ClientCampaignMessageData(rs.getLong("id"),
                        rs.getLong("campaign_id"), rs.getString("campaign_name"), rs.getInt("run_number"), rs.getString("channel"),
                        rs.getString("address"), rs.getString("message"), rs.getString("status"), rs.getString("reason"),
                        CommSupport.utcText(
                                time(rs, rs.getTimestamp("last_attempt_on_utc") != null ? "last_attempt_on_utc" : "created_on_utc")),
                        rs.getString("subject"), rs.getString("provider_event")),
                clientId);
        return new Page<>(rows, total == null ? 0 : total);
    }

    private static String user(final ResultSet rs, final String prefix) throws SQLException {
        final String username = rs.getString(prefix + "_user");
        return username == null ? null
                : MarketingConsentService.userName(rs.getString(prefix + "_first"), rs.getString(prefix + "_last"), username);
    }

    private static LocalDateTime time(final ResultSet rs, final String column) throws SQLException {
        final Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
