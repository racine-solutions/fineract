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
package org.apache.fineract.racine.officerapp.service;

import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.ACCESS_ALLOWED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.ACCESS_OFF;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.DEVICE_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.IDLE;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.ONLINE;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.ONLINE_MINUTES;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.SIGNED_OUT;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.officerapp.data.OfficerAppDeviceData;
import org.apache.fineract.racine.officerapp.data.OfficerAppEventData;
import org.apache.fineract.racine.officerapp.data.OfficerAppSummaryData;
import org.apache.fineract.racine.officerapp.data.OfficerAppUserData;
import org.apache.fineract.racine.officerapp.domain.DeviceInfo;
import org.apache.fineract.racine.officerapp.domain.OfficerAppEvent;
import org.apache.fineract.racine.officerapp.domain.OfficerAppVersion;
import org.apache.fineract.racine.officerapp.exception.OfficerAppDeviceNotFoundException;
import org.apache.fineract.useradministration.exception.UserNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Dashboard data for Admin › Mobile Officer App. Everything is limited to the staff member's office and the offices
 * under it.
 */
@Service
@RequiredArgsConstructor
public class OfficerAppReadService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 500;
    private static final Map<String, String> USER_SORT = Map.of("displayName", "u.firstname", "username", "u.username", "officeName",
            "o.name", "lastSeen", "d.last_seen", "devices", "d.devices", "access", "a.status");

    private static final String USER_COLUMNS = " u.id as userId, u.username as username, u.firstname as firstname,"
            + " u.lastname as lastname, u.email as email, u.enabled as enabled, u.nonlocked as nonlocked, o.id as officeId,"
            + " o.name as officeName, st.id as staffId, st.display_name as staffName, st.mobile_no as mobileNo,"
            + " a.status as accessStatus, a.reason as accessReason, a.changed_on_utc as accessChangedOn, cb.username as accessChangedBy,"
            + " coalesce(d.devices, 0) as devices, coalesce(d.blocked, 0) as blockedDevices, d.first_seen as firstSeen,"
            + " d.last_seen as lastSeen,"
            + " (select count(*) from m_officerapp_session ss where ss.appuser_id = u.id and ss.ended_on_utc is null"
            + "   and ss.last_activity_on_utc > ?) as liveSessions,"
            + " (select e.description from m_officerapp_event e where e.id = (select max(e2.id) from m_officerapp_event e2"
            + "   where e2.appuser_id = u.id)) as lastAction,"
            + " (select max(e3.created_on_utc) from m_officerapp_event e3 where e3.appuser_id = u.id) as lastActionOn ";
    private static final String USER_FROM = " from m_appuser u join m_office o on o.id = u.office_id"
            + " left join m_staff st on st.id = u.staff_id left join m_officerapp_access a on a.appuser_id = u.id"
            + " left join m_appuser cb on cb.id = a.changed_by_id"
            + " left join (select appuser_id, count(*) as devices, sum(case when status = 'BLOCKED' then 1 else 0 end) as blocked,"
            + "   min(first_seen_on_utc) as first_seen, max(last_seen_on_utc) as last_seen from m_officerapp_device group by appuser_id) d"
            + "   on d.appuser_id = u.id where o.hierarchy like ? and u.is_deleted = false ";
    private static final String LIVE_SESSION = " exists (select 1 from m_officerapp_session sx where sx.appuser_id = u.id"
            + " and sx.ended_on_utc is null and sx.last_activity_on_utc > ?) ";

    private static final String DEVICE_COLUMNS = " d.id, d.appuser_id, d.short_code, d.manufacturer, d.model, d.os_version,"
            + " d.app_version, d.app_build, d.status, d.block_reason, d.status_changed_on_utc, d.first_seen_on_utc, d.last_seen_on_utc,"
            + " d.last_ip, u.username, u.firstname, u.lastname, o.name as officeName, sb.username as statusChangedBy,"
            + " (select count(*) from m_officerapp_session s where s.device_id = d.id and s.ended_on_utc is null"
            + "   and s.last_activity_on_utc > ?) as liveSessions ";
    private static final String DEVICE_FROM = " from m_officerapp_device d join m_appuser u on u.id = d.appuser_id"
            + " join m_office o on o.id = u.office_id left join m_appuser sb on sb.id = d.status_changed_by_id where o.hierarchy like ? ";

    private static final String EVENT_COLUMNS = " e.id, e.appuser_id, e.username, e.office_id, e.device_id, e.device_label, e.event_type,"
            + " e.category, e.result, e.actor, e.staff_username, e.command_id, e.entity_name, e.action_name, e.resource_id, e.client_id,"
            + " e.loan_id, e.savings_id, e.amount, e.currency_code, e.description, e.details, e.ip_address, e.captured_on_utc,"
            + " e.created_on_utc ";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PaginationHelper paginationHelper;
    private final OfficerAppSettings settings;

    public OfficerAppSummaryData retrieveSummary(final Long officeId) {
        final LocalDateTime now = OfficerAppSessionService.nowUtc();
        final Timestamp liveSince = Timestamp.valueOf(now.minusMinutes(settings.sessionTimeoutMinutes()));
        final Timestamp onlineSince = Timestamp.valueOf(now.minusMinutes(ONLINE_MINUTES));
        final Timestamp since30 = Timestamp.valueOf(now.minusDays(30));
        final Timestamp today = Timestamp.valueOf(startOfTenantDayUtc());
        final String scope = " join m_appuser u on u.id = %s join m_office o on o.id = u.office_id where o.hierarchy like ?"
                + (officeId != null ? " and o.id = ?" : "");
        final String connected = "select count(distinct s.device_id) as devices, count(distinct case when d.last_seen_on_utc >= ?"
                + " then s.appuser_id end) as online from m_officerapp_session s join m_officerapp_device d on d.id = s.device_id"
                + scope.formatted("s.appuser_id") + " and s.ended_on_utc is null and s.last_activity_on_utc > ?";
        final long[] live = jdbcTemplate.queryForObject(connected, (rs, n) -> new long[] { rs.getLong("devices"), rs.getLong("online") },
                args(onlineSince, hierarchy(), officeId, liveSince));

        final String devices = "select count(*) as registered, coalesce(sum(case when d.status = 'BLOCKED' then 1 else 0 end), 0) as blocked,"
                + " count(distinct case when d.last_seen_on_utc >= ? then d.appuser_id end) as active30,"
                + " count(distinct case when d.last_seen_on_utc >= ? then d.appuser_id end) as activeToday from m_officerapp_device d"
                + scope.formatted("d.appuser_id");
        final long[] dev = jdbcTemplate.queryForObject(devices, (rs,
                n) -> new long[] { rs.getLong("registered"), rs.getLong("blocked"), rs.getLong("active30"), rs.getLong("activeToday") },
                args(since30, today, hierarchy(), officeId));

        final String officers = "select count(*) as officers, coalesce(sum(case when a.status = 'OFF' then 1 else 0 end), 0) as accessOff"
                + " from m_appuser u join m_office o on o.id = u.office_id left join m_officerapp_access a on a.appuser_id = u.id"
                + " where o.hierarchy like ?" + (officeId != null ? " and o.id = ?" : "") + " and u.is_deleted = false"
                + " and (a.appuser_id is not null or exists (select 1 from m_officerapp_device dx where dx.appuser_id = u.id))";
        final long[] off = jdbcTemplate.queryForObject(officers, (rs, n) -> new long[] { rs.getLong("officers"), rs.getLong("accessOff") },
                args(hierarchy(), officeId));

        final String minimum = settings.minimumVersion();
        final List<OfficerAppSummaryData.VersionCount> versions = jdbcTemplate.query(
                "select d.app_version as version, count(*) as devices from m_officerapp_device d" + scope.formatted("d.appuser_id")
                        + " and d.last_seen_on_utc >= ? group by d.app_version order by count(*) desc",
                (rs, n) -> new OfficerAppSummaryData.VersionCount(rs.getString("version"), rs.getLong("devices"),
                        OfficerAppVersion.isOlder(rs.getString("version"), minimum)),
                args(hierarchy(), officeId, since30));
        final long old = versions.stream().filter(OfficerAppSummaryData.VersionCount::belowMinimum)
                .mapToLong(OfficerAppSummaryData.VersionCount::devices).sum();

        return OfficerAppSummaryData.builder().devicesConnected(live[0]).onlineNow(live[1]).activeToday(dev[3]).officersOnApp30Days(dev[2])
                .officers(off[0]).accessOff(off[1]).registeredDevices(dev[0]).blockedDevices(dev[1]).oldVersionDevices(old)
                .minimumVersion(minimum).appEnabled(settings.isOn()).sessionTimeoutMinutes(settings.sessionTimeoutMinutes())
                .accessByDefault(settings.accessByDefault()).requireSession(settings.requireSession()).appVersions(versions).build();
    }

    public Page<OfficerAppUserData> retrieveOfficers(final OfficerAppSearch search) {
        final LocalDateTime now = OfficerAppSessionService.nowUtc();
        final Timestamp liveSince = Timestamp.valueOf(now.minusMinutes(settings.sessionTimeoutMinutes()));
        final Timestamp onlineSince = Timestamp.valueOf(now.minusMinutes(ONLINE_MINUTES));
        final List<Object> args = new ArrayList<>();
        args.add(liveSince);
        args.add(hierarchy());
        final StringBuilder sql = new StringBuilder("select ").append(sqlGenerator.calcFoundRows()).append(USER_COLUMNS).append(USER_FROM);
        if (!Boolean.TRUE.equals(search.all())) {
            sql.append(" and (d.appuser_id is not null or a.appuser_id is not null) ");
        }
        if (search.officeId() != null) {
            sql.append(" and o.id = ? ");
            args.add(search.officeId());
        }
        if (StringUtils.isNotBlank(search.search())) {
            final String like = "%" + search.search().trim().toLowerCase(Locale.ROOT) + "%";
            sql.append(" and (lower(u.username) like ? or lower(u.firstname) like ? or lower(u.lastname) like ? or lower(u.email) like ?")
                    .append(" or exists (select 1 from m_officerapp_device dv where dv.appuser_id = u.id")
                    .append("   and (lower(dv.short_code) like ? or lower(dv.model) like ?))) ");
            for (int i = 0; i < 6; i++) {
                args.add(like);
            }
        }
        if (StringUtils.isNotBlank(search.access())) {
            sql.append(" and coalesce(a.status, ?) = ? ");
            args.add(settings.accessByDefault() ? ACCESS_ALLOWED : ACCESS_OFF);
            args.add(search.access().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.isNotBlank(search.online())) {
            final String online = search.online().trim().toUpperCase(Locale.ROOT);
            if (ONLINE.equals(online)) {
                sql.append(" and ").append(LIVE_SESSION).append(" and d.last_seen >= ? ");
                args.add(liveSince);
                args.add(onlineSince);
            } else if (IDLE.equals(online)) {
                sql.append(" and ").append(LIVE_SESSION).append(" and (d.last_seen is null or d.last_seen < ?) ");
                args.add(liveSince);
                args.add(onlineSince);
            } else if (SIGNED_OUT.equals(online)) {
                sql.append(" and not ").append(LIVE_SESSION);
                args.add(liveSince);
            }
        }
        if (StringUtils.isNotBlank(search.appVersion())) {
            sql.append(" and exists (select 1 from m_officerapp_device dv2 where dv2.appuser_id = u.id and dv2.app_version = ?) ");
            args.add(search.appVersion().trim());
        }
        final String orderColumn = USER_SORT.getOrDefault(StringUtils.defaultString(search.orderBy()), "d.last_seen");
        final String direction = "asc".equalsIgnoreCase(search.sortOrder()) ? "asc" : "desc";
        sql.append(" order by ").append(orderColumn).append(' ').append(direction).append(", u.id desc ");
        sql.append(sqlGenerator.limit(limit(search.limit()), offset(search.offset())));
        return paginationHelper.fetchPage(jdbcTemplate, sql.toString(), args.toArray(), new UserMapper(onlineSince.toLocalDateTime())); // NOSONAR
    }

    /** Any staff user in scope, also one who has never used the app (for the Users page). */
    public OfficerAppUserData retrieveOfficer(final Long userId) {
        final LocalDateTime now = OfficerAppSessionService.nowUtc();
        final Timestamp liveSince = Timestamp.valueOf(now.minusMinutes(settings.sessionTimeoutMinutes()));
        final Timestamp onlineSince = Timestamp.valueOf(now.minusMinutes(ONLINE_MINUTES));
        final List<OfficerAppUserData> rows = jdbcTemplate.query("select " + USER_COLUMNS + USER_FROM + " and u.id = ?", // NOSONAR
                new UserMapper(onlineSince.toLocalDateTime()), liveSince, hierarchy(), userId);
        if (rows.isEmpty()) {
            throw new UserNotFoundException(userId);
        }
        final List<String> roles = jdbcTemplate.queryForList(
                "select r.name from m_appuser_role ur join m_role r on r.id = ur.role_id" + " where ur.appuser_id = ? order by r.name",
                String.class, userId);
        final List<OfficerAppDeviceData> devices = retrieveDevices(
                OfficerAppSearch.builder().userId(userId).limit(MAX_LIMIT).orderBy("lastSeen").build()).getPageItems();
        return rows.get(0).toBuilder().roles(roles).deviceList(devices).stats30Days(stats(userId, now.minusDays(30))).build();
    }

    public Page<OfficerAppDeviceData> retrieveDevices(final OfficerAppSearch search) {
        final LocalDateTime now = OfficerAppSessionService.nowUtc();
        final Timestamp liveSince = Timestamp.valueOf(now.minusMinutes(settings.sessionTimeoutMinutes()));
        final LocalDateTime onlineSince = now.minusMinutes(ONLINE_MINUTES);
        final List<Object> args = new ArrayList<>();
        args.add(liveSince);
        args.add(hierarchy());
        final StringBuilder sql = new StringBuilder("select ").append(sqlGenerator.calcFoundRows()).append(DEVICE_COLUMNS)
                .append(DEVICE_FROM);
        if (search.userId() != null) {
            sql.append(" and d.appuser_id = ? ");
            args.add(search.userId());
        }
        if (search.officeId() != null) {
            sql.append(" and o.id = ? ");
            args.add(search.officeId());
        }
        if (StringUtils.isNotBlank(search.status())) {
            sql.append(" and d.status = ? ");
            args.add(search.status().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.isNotBlank(search.appVersion())) {
            sql.append(" and d.app_version = ? ");
            args.add(search.appVersion().trim());
        }
        if (search.signedIn() != null) {
            sql.append(search.signedIn() ? " and " : " and not ").append(" exists (select 1 from m_officerapp_session sx")
                    .append(" where sx.device_id = d.id and sx.ended_on_utc is null and sx.last_activity_on_utc > ?) ");
            args.add(liveSince);
        }
        if (StringUtils.isNotBlank(search.search())) {
            final String like = "%" + search.search().trim().toLowerCase(Locale.ROOT) + "%";
            sql.append(" and (lower(d.short_code) like ? or lower(d.model) like ? or lower(d.manufacturer) like ?")
                    .append(" or lower(u.username) like ? or lower(u.firstname) like ? or lower(u.lastname) like ?) ");
            for (int i = 0; i < 6; i++) {
                args.add(like);
            }
        }
        sql.append(" order by d.last_seen_on_utc ").append("asc".equalsIgnoreCase(search.sortOrder()) ? "asc" : "desc")
                .append(", d.id desc ");
        sql.append(sqlGenerator.limit(limit(search.limit()), offset(search.offset())));
        final String minimum = settings.minimumVersion();
        return paginationHelper.fetchPage(jdbcTemplate, sql.toString(), args.toArray(), // NOSONAR
                (rs, n) -> deviceOf(rs, onlineSince, minimum));
    }

    public OfficerAppDeviceData retrieveDevice(final Long deviceId) {
        final LocalDateTime now = OfficerAppSessionService.nowUtc();
        final List<OfficerAppDeviceData> rows = jdbcTemplate.query("select " + DEVICE_COLUMNS + DEVICE_FROM + " and d.id = ?", // NOSONAR
                (rs, n) -> deviceOf(rs, now.minusMinutes(ONLINE_MINUTES), settings.minimumVersion()),
                Timestamp.valueOf(now.minusMinutes(settings.sessionTimeoutMinutes())), hierarchy(), deviceId);
        if (rows.isEmpty()) {
            throw new OfficerAppDeviceNotFoundException(deviceId);
        }
        return rows.get(0);
    }

    /** Activity of one officer (userId) or of every officer in scope (userId null). Newest first. */
    public Page<OfficerAppEventData> retrieveEvents(final Long userId, final Long deviceId, final String category, final String result,
            final Integer offset, final Integer limit) {
        final List<Object> args = new ArrayList<>();
        final StringBuilder sql = new StringBuilder("select ").append(sqlGenerator.calcFoundRows()).append(EVENT_COLUMNS)
                .append(" from m_officerapp_event e ");
        if (userId != null) {
            // Scope check: throws when the user is not in the staff member's offices
            assertUserInScope(userId);
            sql.append(" where e.appuser_id = ? ");
            args.add(userId);
        } else {
            sql.append(" join m_office o on o.id = e.office_id where o.hierarchy like ? ");
            args.add(hierarchy());
        }
        if (deviceId != null) {
            sql.append(" and e.device_id = ? ");
            args.add(deviceId);
        }
        if (StringUtils.isNotBlank(category)) {
            final List<String> categories = List.of(category.trim().toUpperCase(Locale.ROOT).split("\\s*,\\s*"));
            sql.append(" and e.category in (").append(String.join(",", categories.stream().map(c -> "?").toList())).append(") ");
            args.addAll(categories);
        }
        if (StringUtils.isNotBlank(result)) {
            sql.append(" and e.result = ? ");
            args.add(result.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" order by e.created_on_utc desc, e.id desc ");
        sql.append(sqlGenerator.limit(limit(limit), offset(offset)));
        return paginationHelper.fetchPage(jdbcTemplate, sql.toString(), args.toArray(), OfficerAppReadService::eventOf); // NOSONAR
    }

    void assertUserInScope(final Long userId) {
        final Integer count = jdbcTemplate.queryForObject("select count(*) from m_appuser u join m_office o on o.id = u.office_id"
                + " where u.id = ? and u.is_deleted = false and o.hierarchy like ?", Integer.class, userId, hierarchy());
        if (count == null || count == 0) {
            throw new UserNotFoundException(userId);
        }
    }

    private OfficerAppUserData.Stats stats(final Long userId, final LocalDateTime since) {
        return jdbcTemplate.queryForObject("select count(distinct case when e.result = 'SUCCESS' then cast(e.created_on_utc as date) end)"
                + " as daysActive, coalesce(sum(case when e.event_type = 'LOGIN' then 1 else 0 end), 0) as signIns,"
                + " coalesce(sum(case when e.category in ('ACTION', 'SYNC') and e.result in ('SUCCESS', 'PENDING') then 1 else 0 end), 0)"
                + " as actions, coalesce(sum(case when e.category = 'SYNC' then 1 else 0 end), 0) as syncs,"
                + " coalesce(sum(case when e.category = 'LOGIN' and e.result in ('REFUSED', 'FAILED') then 1 else 0 end), 0) as refused"
                + " from m_officerapp_event e where e.appuser_id = ? and e.created_on_utc >= ? and e.category in"
                + " ('LOGIN', 'ACTION', 'SYNC', 'REPORT')",
                (rs, n) -> new OfficerAppUserData.Stats(rs.getLong("daysActive"), rs.getLong("signIns"), rs.getLong("actions"),
                        rs.getLong("syncs"), rs.getLong("refused")),
                userId, Timestamp.valueOf(since));
    }

    private String hierarchy() {
        return context.officeHierarchy() + "%";
    }

    /** Arguments in order, leaving out the optional office id when it is null. */
    private static Object[] args(final Object... values) {
        final List<Object> list = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            // the office id always follows the hierarchy pattern
            if (values[i] == null && i > 0 && values[i - 1] instanceof String s && s.endsWith("%")) {
                continue;
            }
            list.add(values[i]);
        }
        return list.toArray();
    }

    private LocalDateTime startOfTenantDayUtc() {
        try {
            return DateUtils.getLocalDateOfTenant().atStartOfDay(DateUtils.getDateTimeZoneOfTenant()).withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (RuntimeException e) {
            return OfficerAppSessionService.nowUtc().truncatedTo(ChronoUnit.DAYS);
        }
    }

    private static int limit(final Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    private static int offset(final Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }

    static String iso(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS) + "Z";
    }

    static String onlineStatus(final int liveSessions, final LocalDateTime lastSeen, final LocalDateTime onlineSince) {
        if (liveSessions <= 0) {
            return SIGNED_OUT;
        }
        return lastSeen != null && !lastSeen.isBefore(onlineSince) ? ONLINE : IDLE;
    }

    private static OfficerAppDeviceData deviceOf(final ResultSet rs, final LocalDateTime onlineSince, final String minimum)
            throws SQLException {
        final int live = rs.getInt("liveSessions");
        final Timestamp lastSeen = rs.getTimestamp("last_seen_on_utc");
        final String appVersion = rs.getString("app_version");
        return OfficerAppDeviceData.builder().id(rs.getLong("id")).userId(rs.getLong("appuser_id")).username(rs.getString("username"))
                .officerName(name(rs.getString("firstname"), rs.getString("lastname"))).officeName(rs.getString("officeName"))
                .shortCode(rs.getString("short_code")).label(DeviceInfo.label(rs.getString("manufacturer"), rs.getString("model")))
                .manufacturer(rs.getString("manufacturer")).model(rs.getString("model")).osVersion(rs.getString("os_version"))
                .appVersion(appVersion).appBuild(rs.getString("app_build"))
                .belowMinimumVersion(OfficerAppVersion.isOlder(appVersion, minimum)).status(rs.getString("status"))
                .blockReason(rs.getString("block_reason")).statusChangedBy(rs.getString("statusChangedBy"))
                .statusChangedOn(iso(rs.getTimestamp("status_changed_on_utc"))).firstSeenOn(iso(rs.getTimestamp("first_seen_on_utc")))
                .lastSeenOn(iso(lastSeen)).lastIp(rs.getString("last_ip")).signedIn(live > 0)
                .onlineStatus(DEVICE_BLOCKED.equals(rs.getString("status")) ? SIGNED_OUT
                        : onlineStatus(live, lastSeen == null ? null : lastSeen.toLocalDateTime(), onlineSince))
                .build();
    }

    private static OfficerAppEventData eventOf(final ResultSet rs, final int rowNum) throws SQLException {
        return new OfficerAppEventData(rs.getLong("id"), rs.getObject("appuser_id", Long.class), rs.getString("username"),
                rs.getObject("office_id", Long.class), rs.getObject("device_id", Long.class), rs.getString("device_label"),
                rs.getString("event_type"), rs.getString("category"), rs.getString("result"), rs.getString("actor"),
                rs.getString("staff_username"), rs.getObject("command_id", Long.class), rs.getString("entity_name"),
                rs.getString("action_name"), rs.getObject("resource_id", Long.class), rs.getObject("client_id", Long.class),
                rs.getObject("loan_id", Long.class), rs.getObject("savings_id", Long.class), rs.getBigDecimal("amount"),
                rs.getString("currency_code"), rs.getString("description"), rs.getString("details"), rs.getString("ip_address"),
                iso(rs.getTimestamp("captured_on_utc")), iso(rs.getTimestamp("created_on_utc")));
    }

    static String name(final String firstname, final String lastname) {
        return StringUtils.trim(StringUtils.defaultString(firstname) + " " + StringUtils.defaultString(lastname));
    }

    private final class UserMapper implements RowMapper<OfficerAppUserData> {

        private final LocalDateTime onlineSince;

        UserMapper(final LocalDateTime onlineSince) {
            this.onlineSince = onlineSince;
        }

        @Override
        public OfficerAppUserData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final String explicit = rs.getString("accessStatus");
            final String accessStatus = explicit != null ? explicit : settings.accessByDefault() ? ACCESS_ALLOWED : ACCESS_OFF;
            final Timestamp lastSeen = rs.getTimestamp("lastSeen");
            final int live = rs.getInt("liveSessions");
            final String web = !rs.getBoolean("enabled") ? "DISABLED" : !rs.getBoolean("nonlocked") ? "LOCKED" : "ACTIVE";
            return OfficerAppUserData.builder().userId(rs.getLong("userId")).username(rs.getString("username"))
                    .firstname(rs.getString("firstname")).lastname(rs.getString("lastname"))
                    .displayName(name(rs.getString("firstname"), rs.getString("lastname"))).email(rs.getString("email"))
                    .officeId(rs.getLong("officeId")).officeName(rs.getString("officeName")).staffId(rs.getObject("staffId", Long.class))
                    .staffName(rs.getString("staffName")).mobileNo(rs.getString("mobileNo")).accessStatus(accessStatus)
                    .accessExplicit(explicit != null).accessReason(rs.getString("accessReason"))
                    .accessChangedOn(iso(rs.getTimestamp("accessChangedOn"))).accessChangedBy(rs.getString("accessChangedBy"))
                    .onlineStatus(onlineStatus(live, lastSeen == null ? null : lastSeen.toLocalDateTime(), onlineSince))
                    .devices(rs.getInt("devices")).blockedDevices(rs.getInt("blockedDevices")).liveSessions(live)
                    .firstSeenOn(iso(rs.getTimestamp("firstSeen"))).lastSeenOn(iso(lastSeen)).lastAction(rs.getString("lastAction"))
                    .lastActionOn(iso(rs.getTimestamp("lastActionOn"))).webStatus(web).build();
        }
    }

    /** For other services: the category constants the timeline filters on. */
    public static List<String> categories() {
        return List.of(OfficerAppEvent.CATEGORY_LOGIN, OfficerAppEvent.CATEGORY_ACTION, OfficerAppEvent.CATEGORY_SYNC,
                OfficerAppEvent.CATEGORY_REPORT, OfficerAppEvent.CATEGORY_ACCESS, OfficerAppEvent.CATEGORY_SESSION);
    }
}
