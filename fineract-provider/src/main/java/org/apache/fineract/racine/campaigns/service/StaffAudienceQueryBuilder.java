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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.racine.campaigns.data.AudienceFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Turns a staff audience's filters into two SQL conditions: one on staff records (m_staff s) and one on system users
 * who have no staff record (m_appuser u); both are joined to m_office o. Values are always bound, never written into
 * the query. "Is also a client" is matched on phone and email afterwards, in AudienceService.
 */
public final class StaffAudienceQueryBuilder {

    /** The filter that marks an audience as reaching staff instead of clients. */
    public static final String RECIPIENTS = "RECIPIENTS";
    public static final String STAFF = "STAFF";
    public static final String STAFF_IS_CLIENT = "STAFF_IS_CLIENT";

    private final List<String> staffConditions = new ArrayList<>();
    private final List<String> userConditions = new ArrayList<>();
    private final MapSqlParameterSource params = new MapSqlParameterSource();
    private boolean includeStaff = true;
    private boolean includeUsers = true;
    private int index;

    private StaffAudienceQueryBuilder() {}

    /** True when the filters say the audience is staff, not clients. */
    public static boolean isStaffAudience(final List<AudienceFilter> filters) {
        return filters != null && filters.stream().anyMatch(filter -> filter != null && RECIPIENTS.equalsIgnoreCase(filter.field())
                && filter.values() != null && filter.values().stream().anyMatch(STAFF::equalsIgnoreCase));
    }

    public static StaffAudienceQueryBuilder of(final List<AudienceFilter> filters, final String officeHierarchy) {
        final StaffAudienceQueryBuilder builder = new StaffAudienceQueryBuilder();
        final String scope = builder.param(officeHierarchy + "%");
        final String yes = builder.param(true);
        final String no = builder.param(false);
        builder.staffConditions.add("o.hierarchy like :" + scope);
        builder.staffConditions.add("s.is_active = :" + yes);
        builder.userConditions.add("o.hierarchy like :" + scope);
        builder.userConditions.add("u.is_deleted = :" + no);
        builder.userConditions.add("u.enabled = :" + yes);
        builder.userConditions.add("u.staff_id is null");
        for (final AudienceFilter filter : filters == null ? List.<AudienceFilter>of() : filters) {
            builder.add(filter);
        }
        return builder;
    }

    public boolean includeStaff() {
        return includeStaff;
    }

    public boolean includeUsers() {
        return includeUsers;
    }

    public String staffWhere() {
        return String.join(" and ", staffConditions);
    }

    public String userWhere() {
        return String.join(" and ", userConditions);
    }

    public MapSqlParameterSource params() {
        return params;
    }

    private void add(final AudienceFilter filter) {
        if (filter == null || StringUtils.isBlank(filter.field())) {
            throw invalid("field", "Each audience filter needs a field.");
        }
        final String field = filter.field().toUpperCase(Locale.ROOT);
        final List<String> values = filter.values() == null ? List.of() : filter.values();
        final boolean negate = "NOT_IN".equalsIgnoreCase(filter.operator());
        switch (field) {
            case RECIPIENTS, STAFF_IS_CLIENT -> {
                // The audience type, and a check made after the query
            }
            case "STAFF_TYPE" -> {
                final List<String> types = nonEmpty(values.stream().map(value -> value.trim().toUpperCase(Locale.ROOT)).toList(), field);
                final List<String> either = new ArrayList<>();
                final String login = "exists (select 1 from m_appuser lu where lu.staff_id = s.id and lu.is_deleted = :" + param(false)
                        + ")";
                for (final String type : types) {
                    switch (type) {
                        case "LOAN_OFFICER" -> either.add("s.is_loan_officer = :" + param(true));
                        case "SYSTEM_USER" -> either.add(login);
                        case "NO_LOGIN" -> either.add("not " + login);
                        default -> throw invalid(field, "Staff type must be LOAN_OFFICER, SYSTEM_USER or NO_LOGIN.");
                    }
                }
                staffConditions.add("(" + String.join(" or ", either) + ")");
                // System users without a staff record are neither loan officers nor without a login
                if (!types.contains("SYSTEM_USER")) {
                    includeUsers = false;
                }
            }
            case "STAFF_OFFICE" -> {
                final String p = param(ids(values, field));
                final String condition = Boolean.TRUE.equals(filter.includeChildren())
                        ? "exists (select 1 from m_office po where po.id in (:" + p + ") and o.hierarchy like concat(po.hierarchy, '%'))"
                        : "o.id in (:" + p + ")";
                staffConditions.add(condition);
                userConditions.add(condition);
            }
            case "STAFF_ROLE" -> {
                final String roles = param(ids(values, field));
                final String no = param(false);
                staffConditions.add((negate ? "not exists" : "exists") + " (select 1 from m_appuser ru join m_appuser_role ar"
                        + " on ar.appuser_id = ru.id where ru.staff_id = s.id and ru.is_deleted = :" + no + " and ar.role_id in (:" + roles
                        + "))");
                userConditions.add((negate ? "not exists" : "exists") + " (select 1 from m_appuser_role ar where ar.appuser_id = u.id"
                        + " and ar.role_id in (:" + roles + "))");
            }
            case "STAFF_MEMBER" -> {
                final List<Long> staffIds = new ArrayList<>();
                final List<Long> userIds = new ArrayList<>();
                for (final String key : nonEmpty(values, field)) {
                    final String[] parts = key.trim().split(":");
                    try {
                        if (parts.length == 2 && "STAFF".equalsIgnoreCase(parts[0])) {
                            staffIds.add(Long.valueOf(parts[1]));
                        } else if (parts.length == 2 && "USER".equalsIgnoreCase(parts[0])) {
                            userIds.add(Long.valueOf(parts[1]));
                        } else {
                            throw invalid(field, "Pick staff from the list.");
                        }
                    } catch (NumberFormatException e) {
                        throw invalid(field, "Pick staff from the list.", e);
                    }
                }
                if (negate) {
                    if (!staffIds.isEmpty()) {
                        staffConditions.add("s.id not in (:" + param(staffIds) + ")");
                    }
                    if (!userIds.isEmpty()) {
                        userConditions.add("u.id not in (:" + param(userIds) + ")");
                    }
                } else {
                    if (staffIds.isEmpty()) {
                        includeStaff = false;
                    } else {
                        staffConditions.add("s.id in (:" + param(staffIds) + ")");
                    }
                    if (userIds.isEmpty()) {
                        includeUsers = false;
                    } else {
                        userConditions.add("u.id in (:" + param(userIds) + ")");
                    }
                }
            }
            default -> throw invalid("field", "Unknown staff audience filter " + filter.field() + ".");
        }
    }

    /** "Is also a client": null when not asked, else whether the staff member must be one. */
    public static Boolean isClientWanted(final List<AudienceFilter> filters) {
        if (filters == null) {
            return null;
        }
        return filters.stream().filter(filter -> filter != null && STAFF_IS_CLIENT.equalsIgnoreCase(filter.field())).findFirst()
                .map(filter -> filter.values() == null || filter.values().isEmpty() || !"NO".equalsIgnoreCase(filter.values().get(0)))
                .orElse(null);
    }

    private String param(final Object value) {
        final String name = "s" + index++;
        params.addValue(name, value);
        return name;
    }

    private static List<Long> ids(final List<String> values, final String field) {
        try {
            return nonEmpty(values.stream().map(value -> Long.valueOf(value.trim())).toList(), field);
        } catch (NumberFormatException e) {
            throw invalid(field, "Choose at least one value for " + field + ".", e);
        }
    }

    private static <T> List<T> nonEmpty(final List<T> values, final String field) {
        if (values.isEmpty()) {
            throw invalid(field, "Choose at least one value for " + field + ".");
        }
        return values;
    }

    private static PlatformApiDataValidationException invalid(final String field, final String message) {
        return invalid(field, message, null);
    }

    private static PlatformApiDataValidationException invalid(final String field, final String message, final Throwable cause) {
        return new PlatformApiDataValidationException("validation.msg.comm.audience." + field.toLowerCase(Locale.ROOT) + ".invalid",
                message, "audience", cause);
    }

    /** Fields a staff audience offers, in display order. */
    public static final List<Map<String, String>> FIELDS = List.of(Map.of("field", "STAFF_TYPE", "label", "Staff type"),
            Map.of("field", "STAFF_OFFICE", "label", "Office"), Map.of("field", "STAFF_ROLE", "label", "Role"),
            Map.of("field", "STAFF_MEMBER", "label", "Specific staff"), Map.of("field", STAFF_IS_CLIENT, "label", "Is also a client"));
}
