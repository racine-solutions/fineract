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
package org.apache.fineract.selfservice.report.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.exception.OfficeNotFoundException;
import org.apache.fineract.portfolio.client.domain.ClientStatus;
import org.apache.fineract.selfservice.account.service.SelfServiceTransferGuard;
import org.apache.fineract.selfservice.report.data.SelfServiceAdoptionReport;
import org.apache.fineract.selfservice.report.data.SelfServiceFunnelReport;
import org.apache.fineract.selfservice.report.data.SelfServiceTransactionsReport;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Back-office reports on the mobile app. Every report covers the staff member's office and its child offices, or one
 * office (with its child offices) inside that range.
 */
@Service
@RequiredArgsConstructor
public class SelfServiceReportReadService {

    static final int MAX_MONTHS = 36;
    static final int MAX_DAYS = 366;

    /** App users that are not deleted, with the office they belong to (the linked client's office). */
    private static final String USERS_IN_SCOPE = " from m_appselfservice_user u "
            + " left join (select appuser_id, min(client_id) as client_id from m_selfservice_user_client_mapping group by appuser_id) m"
            + "   on m.appuser_id = u.id " //
            + " left join m_client c on c.id = m.client_id " //
            + " join m_office o on o.id = coalesce(c.office_id, u.office_id) " //
            + " where u.is_deleted = false and o.hierarchy like ? ";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;

    public SelfServiceAdoptionReport adoption(final Long officeId, final Integer months) {
        final String hierarchy = scopeHierarchy(officeId) + "%";
        final LocalDateTime since30Days = LocalDateTime.now(ZoneOffset.UTC).minusDays(30);
        final int closed = SelfServiceUserStatus.CLOSED.getValue();

        final List<SelfServiceAdoptionReport.OfficeAdoption> offices = jdbcTemplate.query("select o.id, o.name,"
                + " (select count(*) from m_client ac where ac.office_id = o.id and ac.status_enum = ?) as activeClients,"
                + " (select count(distinct mc.client_id) from m_selfservice_user_client_mapping mc"
                + "    join m_client cc on cc.id = mc.client_id join m_appselfservice_user cu on cu.id = mc.appuser_id"
                + "    where cc.office_id = o.id and cc.status_enum = ? and cu.is_deleted = false and cu.status_enum <> ?) as clientsUsingApp,"
                + " coalesce(sum(case when x.status_enum <> ? then 1 else 0 end), 0) as appUsers,"
                + " coalesce(sum(case when x.status_enum in (?, ?) then 1 else 0 end), 0) as activeAppUsers,"
                + " coalesce(sum(case when x.last_login_on_utc >= ? then 1 else 0 end), 0) as loggedIn30"
                + " from m_office o left join (select o.id as office_id, u.status_enum, u.last_login_on_utc" + USERS_IN_SCOPE
                + " ) x on x.office_id = o.id where o.hierarchy like ? group by o.id, o.name, o.hierarchy order by o.hierarchy",
                (rs, rowNum) -> { // NOSONAR
                    final long activeClients = rs.getLong("activeClients");
                    final long using = rs.getLong("clientsUsingApp");
                    return new SelfServiceAdoptionReport.OfficeAdoption(rs.getLong("id"), rs.getString("name"), activeClients, using,
                            rs.getLong("appUsers"), rs.getLong("activeAppUsers"), rs.getLong("loggedIn30"), rate(using, activeClients));
                }, ClientStatus.ACTIVE.getValue(), ClientStatus.ACTIVE.getValue(), closed, closed, SelfServiceUserStatus.ACTIVE.getValue(),
                SelfServiceUserStatus.VIEW_ONLY.getValue(), Timestamp.valueOf(since30Days), hierarchy, hierarchy);

        long activeClients = 0;
        long using = 0;
        long appUsers = 0;
        long activeAppUsers = 0;
        long loggedIn = 0;
        for (SelfServiceAdoptionReport.OfficeAdoption office : offices) {
            activeClients += office.activeClients();
            using += office.clientsUsingApp();
            appUsers += office.appUsers();
            activeAppUsers += office.activeAppUsers();
            loggedIn += office.loggedInLast30Days();
        }
        final SelfServiceAdoptionReport.Totals totals = new SelfServiceAdoptionReport.Totals(activeClients, using, appUsers, activeAppUsers,
                loggedIn, rate(using, activeClients));

        return new SelfServiceAdoptionReport(totals, offices, monthly(hierarchy, months));
    }

    public SelfServiceFunnelReport funnel(final Long officeId, final LocalDate fromDate, final LocalDate toDate) {
        final String hierarchy = scopeHierarchy(officeId) + "%";
        final LocalDate to = toDate != null ? toDate : LocalDate.now(ZoneOffset.UTC);
        final LocalDate from = validFrom(fromDate, to);
        final Timestamp start = Timestamp.valueOf(from.atStartOfDay());
        final Timestamp end = Timestamp.valueOf(to.plusDays(1).atStartOfDay());

        final Long codesSent = jdbcTemplate.queryForObject(
                "select count(*) from m_selfservice_event e join m_office o on o.id = e.office_id"
                        + " where e.event_type = 'ENROLLMENT_TOKEN' and e.created_on_utc >= ? and e.created_on_utc < ? and o.hierarchy like ?",
                Long.class, start, end, hierarchy);

        return jdbcTemplate.queryForObject("select count(*) as signedUp,"
                + " coalesce(sum(case when u.status_enum <> ? then 1 else 0 end), 0) as confirmed,"
                + " coalesce(sum(case when u.first_login_on_utc is not null or u.last_login_on_utc is not null then 1 else 0 end), 0)"
                + "   as loggedIn,"
                + " coalesce(sum(case when exists (select 1 from m_selfservice_transfer_log t where t.appuser_id = u.id) then 1 else 0 end), 0)"
                + "   as transacted" + USERS_IN_SCOPE + " and u.created_on_utc >= ? and u.created_on_utc < ?",
                (rs, rowNum) -> new SelfServiceFunnelReport(from.toString(), to.toString(), codesSent == null ? 0 : codesSent, // NOSONAR
                        rs.getLong("signedUp"), rs.getLong("confirmed"), rs.getLong("loggedIn"), rs.getLong("transacted")),
                SelfServiceUserStatus.PENDING.getValue(), hierarchy, start, end);
    }

    public SelfServiceTransactionsReport transactions(final Long officeId, final LocalDate fromDate, final LocalDate toDate) {
        final String hierarchy = scopeHierarchy(officeId) + "%";
        final LocalDate to = toDate != null ? toDate : LocalDate.now(ZoneOffset.UTC);
        final LocalDate from = validFrom(fromDate, to);
        final String transfersInScope = " from m_selfservice_transfer_log t where t.transfer_date >= ? and t.transfer_date <= ?"
                + " and t.appuser_id in (select u.id" + USERS_IN_SCOPE + ") ";

        final List<SelfServiceTransactionsReport.ChannelTotal> totals = jdbcTemplate.query(
                "select t.channel, t.currency_code, count(*) as transfers, coalesce(sum(t.amount), 0) as amount,"
                        + " count(distinct t.appuser_id) as users" + transfersInScope
                        + " group by t.channel, t.currency_code order by t.currency_code, t.channel",
                (rs, rowNum) -> new SelfServiceTransactionsReport.ChannelTotal(rs.getString("channel"), rs.getString("currency_code"), // NOSONAR
                        rs.getLong("transfers"), rs.getBigDecimal("amount"), rs.getLong("users")),
                from, to, hierarchy);

        final List<SelfServiceTransactionsReport.DailyTotal> days = jdbcTemplate.query(
                "select t.transfer_date, t.currency_code, count(*) as transfers, coalesce(sum(t.amount), 0) as amount" + transfersInScope
                        + " group by t.transfer_date, t.currency_code order by t.transfer_date, t.currency_code",
                (rs, rowNum) -> new SelfServiceTransactionsReport.DailyTotal(rs.getDate("transfer_date").toLocalDate().toString(), // NOSONAR
                        rs.getString("currency_code"), rs.getLong("transfers"), rs.getBigDecimal("amount")),
                from, to, hierarchy);

        return new SelfServiceTransactionsReport(from.toString(), to.toString(), totals, days, refusals(hierarchy, from, to));
    }

    /** Refused attempts grouped by why they were refused; the reason is read from each event's details. */
    private List<SelfServiceTransactionsReport.RefusalTotal> refusals(final String hierarchy, final LocalDate from, final LocalDate to) {
        final Map<String, long[]> counts = new LinkedHashMap<>();
        final Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        final Map<String, Set<Long>> users = new LinkedHashMap<>();
        jdbcTemplate.query("select e.appuser_id, e.amount, e.currency_code, e.details from m_selfservice_event e"
                + " join m_office o on o.id = e.office_id where e.event_type = ? and e.created_on_utc >= ? and e.created_on_utc < ?"
                + " and o.hierarchy like ? order by e.created_on_utc", rs -> {
                    final String key = refusalReason(rs.getString("details")) + "|"
                            + StringUtils.defaultString(rs.getString("currency_code"));
                    counts.computeIfAbsent(key, k -> new long[1])[0]++;
                    final BigDecimal amount = rs.getBigDecimal("amount");
                    amounts.merge(key, amount == null ? BigDecimal.ZERO : amount, BigDecimal::add);
                    final long userId = rs.getLong("appuser_id");
                    if (!rs.wasNull()) {
                        users.computeIfAbsent(key, k -> new HashSet<>()).add(userId);
                    }
                }, SelfServiceTransferGuard.REFUSED_EVENT, Timestamp.valueOf(from.atStartOfDay()),
                Timestamp.valueOf(to.plusDays(1).atStartOfDay()), hierarchy);

        final List<SelfServiceTransactionsReport.RefusalTotal> result = new ArrayList<>();
        counts.forEach((key, count) -> {
            final String[] parts = key.split("\\|", -1);
            result.add(new SelfServiceTransactionsReport.RefusalTotal(parts[0], StringUtils.trimToNull(parts[1]), count[0],
                    amounts.get(key), users.getOrDefault(key, Set.of()).size()));
        });
        result.sort((a, b) -> Long.compare(b.attempts(), a.attempts()));
        return result;
    }

    static String refusalReason(final String details) {
        String code = "";
        if (StringUtils.isNotBlank(details)) {
            try {
                final JsonObject json = JsonParser.parseString(details).getAsJsonObject();
                code = json.has("reasonCode") && !json.get("reasonCode").isJsonNull() ? json.get("reasonCode").getAsString() : "";
            } catch (RuntimeException ignored) {
                // unreadable details: counted as OTHER
            }
        }
        final String lower = code.toLowerCase(Locale.ROOT);
        if (lower.contains("per.transfer")) {
            return "PER_TRANSFER_LIMIT";
        }
        if (lower.contains("daily")) {
            return "DAILY_LIMIT";
        }
        if (lower.contains("id.invalid") || lower.contains("not.found")) {
            return "OWNERSHIP";
        }
        return "OTHER";
    }

    /** Sign-ups and first logins per month, oldest first, including months with none. */
    private List<SelfServiceAdoptionReport.MonthlyAdoption> monthly(final String hierarchy, final Integer months) {
        final int count = months == null || months <= 0 ? 12 : Math.min(months, MAX_MONTHS);
        final YearMonth current = YearMonth.now(ZoneOffset.UTC);
        final YearMonth first = current.minusMonths(count - 1L);
        final Timestamp since = Timestamp.valueOf(first.atDay(1).atStartOfDay());

        final Map<YearMonth, long[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            buckets.put(first.plusMonths(i), new long[2]);
        }
        jdbcTemplate.query("select u.created_on_utc, u.first_login_on_utc" + USERS_IN_SCOPE
                + " and (u.created_on_utc >= ? or u.first_login_on_utc >= ?)", rs -> {
                    addToMonth(buckets, rs.getTimestamp("created_on_utc"), 0);
                    addToMonth(buckets, rs.getTimestamp("first_login_on_utc"), 1);
                }, hierarchy, since, since);

        final List<SelfServiceAdoptionReport.MonthlyAdoption> result = new ArrayList<>();
        buckets.forEach(
                (month, values) -> result.add(new SelfServiceAdoptionReport.MonthlyAdoption(month.toString(), values[0], values[1])));
        return result;
    }

    private static void addToMonth(final Map<YearMonth, long[]> buckets, final Timestamp timestamp, final int index) {
        if (timestamp != null) {
            final long[] bucket = buckets.get(YearMonth.from(timestamp.toLocalDateTime()));
            if (bucket != null) {
                bucket[index]++;
            }
        }
    }

    /** The office hierarchy the report covers: the staff member's own range, or one office inside it. */
    String scopeHierarchy(final Long officeId) {
        final String staffHierarchy = context.officeHierarchy();
        if (officeId == null) {
            return staffHierarchy;
        }
        final List<String> found = jdbcTemplate.queryForList("select hierarchy from m_office where id = ?", String.class, officeId);
        if (found.isEmpty() || found.get(0) == null || !found.get(0).startsWith(staffHierarchy)) {
            throw new OfficeNotFoundException(officeId);
        }
        return found.get(0);
    }

    static BigDecimal rate(final long part, final long whole) {
        if (whole <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static LocalDate validFrom(final LocalDate fromDate, final LocalDate to) {
        final LocalDate from = fromDate != null ? fromDate : to.minusDays(29);
        if (from.isAfter(to)) {
            return to;
        }
        return from.isBefore(to.minusDays(MAX_DAYS - 1L)) ? to.minusDays(MAX_DAYS - 1L) : from;
    }
}
