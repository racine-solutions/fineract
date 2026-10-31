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
package org.apache.fineract.selfservice.admin.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
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
import org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants;
import org.apache.fineract.selfservice.admin.data.SelfServiceUserAdminData;
import org.apache.fineract.selfservice.admin.data.SelfServiceUserClientData;
import org.apache.fineract.selfservice.admin.data.SelfServiceUserLimitData;
import org.apache.fineract.selfservice.admin.data.SelfServiceUserSummaryData;
import org.apache.fineract.selfservice.admin.exception.SelfServiceUserNotFoundException;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SelfServiceUserAdminReadServiceImpl implements SelfServiceUserAdminReadService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 500;
    private static final Map<String, String> SORTABLE_COLUMNS = Map.of("displayName", "u.firstname", "username", "u.username", "officeName",
            "o.name", "status", "u.status_enum", "createdOn", "u.created_on_utc", "lastLoginOn", "u.last_login_on_utc");

    // An app user belongs to the office of the client they act for (falls back to the office on the user record), so
    // a client transferred to another branch moves their app user with them.
    private static final String FROM_SQL = " from m_appselfservice_user u "
            + " left join (select appuser_id, min(client_id) as client_id from m_selfservice_user_client_mapping group by appuser_id) m"
            + "   on m.appuser_id = u.id " //
            + " left join m_client c on c.id = m.client_id " //
            + " join m_office o on o.id = coalesce(c.office_id, u.office_id) " //
            + " left join m_appuser sb on sb.id = u.status_changed_by_id ";

    private static final String SELECT_COLUMNS = " u.id as id, u.username as username, u.firstname as firstname, u.lastname as lastname,"
            + " u.email as email, u.status_enum as statusEnum, u.nonlocked as nonlocked, u.password_reset_required as passwordResetRequired,"
            + " u.status_reason as statusReason, u.status_changed_on_utc as statusChangedOn, sb.username as statusChangedBy,"
            + " u.created_on_utc as createdOn, u.last_login_on_utc as lastLoginOn, o.id as officeId, o.name as officeName,"
            + " c.id as clientId, c.account_no as clientAccountNo, c.display_name as clientDisplayName, c.mobile_no as mobileNo ";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PaginationHelper paginationHelper;

    @Override
    public Page<SelfServiceUserAdminData> retrieveAll(final SelfServiceUserSearchParameters parameters) {
        final List<Object> args = new ArrayList<>();
        final StringBuilder sql = new StringBuilder("select ").append(sqlGenerator.calcFoundRows()).append(SELECT_COLUMNS).append(FROM_SQL);
        appendScope(sql, args, parameters.getOfficeId());

        if (parameters.getStatus() != null) {
            sql.append(" and u.status_enum = ? ");
            args.add(parameters.getStatus());
        }
        if (parameters.getLocked() != null) {
            sql.append(" and u.nonlocked = ? ");
            args.add(!parameters.getLocked());
        }
        if (parameters.getClientId() != null) {
            sql.append(" and exists (select 1 from m_selfservice_user_client_mapping mc where mc.appuser_id = u.id and mc.client_id = ?) ");
            args.add(parameters.getClientId());
        }
        if (StringUtils.isNotBlank(parameters.getSearch())) {
            final String like = "%" + parameters.getSearch().trim().toLowerCase(Locale.ROOT) + "%";
            sql.append(" and (lower(u.username) like ? or lower(u.firstname) like ? or lower(u.lastname) like ? or lower(u.email) like ?")
                    .append(" or lower(c.mobile_no) like ? or lower(c.account_no) like ? or lower(c.display_name) like ?) ");
            for (int i = 0; i < 7; i++) {
                args.add(like);
            }
        }

        final String orderColumn = SORTABLE_COLUMNS.getOrDefault(StringUtils.defaultString(parameters.getOrderBy()), "u.id");
        final String direction = "asc".equalsIgnoreCase(parameters.getSortOrder()) ? "asc" : "desc";
        sql.append(" order by ").append(orderColumn).append(' ').append(direction).append(", u.id desc ");

        final int limit = parameters.getLimit() == null || parameters.getLimit() <= 0 ? DEFAULT_LIMIT
                : Math.min(parameters.getLimit(), MAX_LIMIT);
        final int offset = parameters.getOffset() == null || parameters.getOffset() < 0 ? 0 : parameters.getOffset();
        sql.append(sqlGenerator.limit(limit, offset));

        return paginationHelper.fetchPage(jdbcTemplate, sql.toString(), args.toArray(), new UserMapper(false)); // NOSONAR
    }

    @Override
    public SelfServiceUserSummaryData retrieveSummary(final Long officeId) {
        final List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusDays(30)));
        final StringBuilder sql = new StringBuilder("select count(*) as total,")
                .append(countStatus(SelfServiceUserStatus.PENDING, "pending")).append(',')
                .append(countStatus(SelfServiceUserStatus.ACTIVE, "active")).append(',')
                .append(countStatus(SelfServiceUserStatus.VIEW_ONLY, "viewOnly")).append(',')
                .append(countStatus(SelfServiceUserStatus.SUSPENDED, "suspended")).append(',')
                .append(countStatus(SelfServiceUserStatus.CLOSED, "closed")).append(',')
                .append(" coalesce(sum(case when u.nonlocked = false then 1 else 0 end), 0) as locked,")
                .append(" coalesce(sum(case when u.last_login_on_utc >= ? then 1 else 0 end), 0) as loggedIn30 ").append(FROM_SQL);
        appendScope(sql, args, officeId);

        return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> SelfServiceUserSummaryData.builder() // NOSONAR
                .total(rs.getLong("total")).pending(rs.getLong("pending")).active(rs.getLong("active")).viewOnly(rs.getLong("viewOnly"))
                .suspended(rs.getLong("suspended")).closed(rs.getLong("closed")).locked(rs.getLong("locked"))
                .loggedInLast30Days(rs.getLong("loggedIn30")).build(), args.toArray());
    }

    @Override
    public SelfServiceUserAdminData retrieveOne(final Long id) {
        final List<Object> args = new ArrayList<>();
        final StringBuilder sql = new StringBuilder("select ").append(SELECT_COLUMNS).append(", ").append(
                " (select count(*) from self_service_auth_token t where t.user_id = u.id and t.token_type = ? and t.expires_at > ?)")
                .append(" as openSessions, ")
                .append(" (select max(t.created_at) from self_service_auth_token t where t.user_id = u.id and t.token_type = ? and t.expires_at > ?)")
                .append(" as lastSessionStartedOn ").append(FROM_SQL);
        final Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        args.add(SelfServiceUsersApiConstants.ACCESS_TOKEN_TYPE);
        args.add(now);
        args.add(SelfServiceUsersApiConstants.ACCESS_TOKEN_TYPE);
        args.add(now);
        appendScope(sql, args, null);
        sql.append(" and u.id = ? ");
        args.add(id);

        final List<SelfServiceUserAdminData> result = jdbcTemplate.query(sql.toString(), new UserMapper(true), args.toArray()); // NOSONAR
        if (result.isEmpty()) {
            throw new SelfServiceUserNotFoundException(id);
        }
        return result.get(0).toBuilder().limits(retrieveLimits(id)).clients(retrieveClients(id)).build();
    }

    private List<SelfServiceUserLimitData> retrieveLimits(final Long userId) {
        final LocalDate today = DateUtils.getBusinessLocalDate();
        return jdbcTemplate.query(
                "select l.currency_code, l.per_transfer_limit, l.daily_limit, l.updated_on_utc, ub.username as updatedBy,"
                        + " (select coalesce(sum(t.amount), 0) from m_selfservice_transfer_log t where t.appuser_id = l.appuser_id"
                        + "   and t.currency_code = l.currency_code and t.transfer_date = ?) as usedToday"
                        + " from m_selfservice_user_limit l left join m_appuser ub on ub.id = l.updated_by_id"
                        + " where l.appuser_id = ? order by l.currency_code",
                (rs, rowNum) -> SelfServiceUserLimitData.builder() // NOSONAR
                        .currencyCode(rs.getString("currency_code")).perTransferLimit(rs.getBigDecimal("per_transfer_limit"))
                        .dailyLimit(rs.getBigDecimal("daily_limit")).usedToday(rs.getBigDecimal("usedToday"))
                        .updatedOn(toUtcIso(rs.getTimestamp("updated_on_utc"))).updatedBy(rs.getString("updatedBy")).build(),
                today, userId);
    }

    private List<SelfServiceUserClientData> retrieveClients(final Long userId) {
        return jdbcTemplate.query("select c.id, c.account_no, c.display_name, o.id as officeId, o.name as officeName"
                + " from m_selfservice_user_client_mapping m join m_client c on c.id = m.client_id join m_office o on o.id = c.office_id"
                + " where m.appuser_id = ? order by c.id",
                (rs, rowNum) -> SelfServiceUserClientData.builder().id(rs.getLong("id")) // NOSONAR
                        .accountNo(rs.getString("account_no")).displayName(rs.getString("display_name")).officeId(rs.getLong("officeId"))
                        .officeName(rs.getString("officeName")).build(),
                userId);
    }

    /** Limits rows to the staff member's office and its child offices, optionally to one office in that range. */
    private void appendScope(final StringBuilder sql, final List<Object> args, final Long officeId) {
        sql.append(" where o.hierarchy like ? ");
        args.add(context.officeHierarchy() + "%");
        if (officeId != null) {
            sql.append(" and o.id = ? ");
            args.add(officeId);
        }
    }

    private static String countStatus(final SelfServiceUserStatus status, final String alias) {
        return " coalesce(sum(case when u.status_enum = " + status.getValue() + " then 1 else 0 end), 0) as " + alias;
    }

    static String toUtcIso(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS) + "Z";
    }

    private static final class UserMapper implements RowMapper<SelfServiceUserAdminData> {

        private final boolean withSessions;

        UserMapper(final boolean withSessions) {
            this.withSessions = withSessions;
        }

        @Override
        public SelfServiceUserAdminData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final String firstname = rs.getString("firstname");
            final String lastname = rs.getString("lastname");
            final Long clientId = rs.getObject("clientId") == null ? null : rs.getLong("clientId");
            final SelfServiceUserAdminData.SelfServiceUserAdminDataBuilder builder = SelfServiceUserAdminData.builder().id(rs.getLong("id"))
                    .username(rs.getString("username")).firstname(firstname).lastname(lastname)
                    .displayName(StringUtils.trim(StringUtils.defaultString(firstname) + " " + StringUtils.defaultString(lastname)))
                    .email(rs.getString("email")).mobileNo(rs.getString("mobileNo")).officeId(rs.getLong("officeId"))
                    .officeName(rs.getString("officeName")).clientId(clientId).clientAccountNo(rs.getString("clientAccountNo"))
                    .clientDisplayName(rs.getString("clientDisplayName"))
                    .status(SelfServiceUserStatus.fromInt(rs.getInt("statusEnum")).toEnumOptionData()).locked(!rs.getBoolean("nonlocked"))
                    .passwordResetRequired(rs.getBoolean("passwordResetRequired")).statusReason(rs.getString("statusReason"))
                    .statusChangedOn(toUtcIso(rs.getTimestamp("statusChangedOn"))).statusChangedBy(rs.getString("statusChangedBy"))
                    .createdOn(toUtcIso(rs.getTimestamp("createdOn"))).lastLoginOn(toUtcIso(rs.getTimestamp("lastLoginOn")));
            if (withSessions) {
                builder.openSessions(rs.getInt("openSessions")).lastSessionStartedOn(toUtcIso(rs.getTimestamp("lastSessionStartedOn")));
            }
            return builder.build();
        }
    }
}
