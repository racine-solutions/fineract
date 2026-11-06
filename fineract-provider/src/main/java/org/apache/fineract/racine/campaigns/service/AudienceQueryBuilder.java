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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.racine.campaigns.data.AudienceFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Turns audience filters into one SQL condition on m_client c (joined to m_office o). Every filter is a fixed query
 * with bound parameters: staff never write SQL, and nothing they type reaches the query text. Dates are worked out
 * here, so the SQL is the same on MariaDB, MySQL and PostgreSQL.
 */
public final class AudienceQueryBuilder {

    private static final int CLIENT_PENDING = 100;
    private static final int CLIENT_ACTIVE = 300;
    private static final int CLIENT_CLOSED = 600;
    private static final int LOAN_ACTIVE = 300;
    private static final int LOAN_CLOSED_REPAID = 600;
    private static final int ACCOUNT_ACTIVE = 300;
    private static final int SAVINGS = 100;
    private static final int FIXED_DEPOSIT = 200;
    private static final int RECURRING_DEPOSIT = 300;
    private static final int APP_ACTIVE = 300;
    private static final int APP_VIEW_ONLY = 400;
    private static final int APP_CLOSED = 600;

    private final List<String> conditions = new ArrayList<>();
    private final MapSqlParameterSource params = new MapSqlParameterSource();
    private final LocalDate today;
    private int index;
    private boolean statusGiven;

    private AudienceQueryBuilder(final LocalDate today) {
        this.today = today;
    }

    /**
     * @param officeHierarchy
     *            only clients of this office and the offices under it
     */
    public static AudienceQueryBuilder of(final List<AudienceFilter> filters, final String officeHierarchy, final LocalDate today) {
        final AudienceQueryBuilder builder = new AudienceQueryBuilder(today);
        builder.conditions.add("o.hierarchy like :scopeHierarchy");
        builder.params.addValue("scopeHierarchy", officeHierarchy + "%");
        for (final AudienceFilter filter : filters == null ? List.<AudienceFilter>of() : filters) {
            builder.add(filter);
        }
        if (!builder.statusGiven) {
            builder.conditions.add("c.status_enum = :defaultStatus");
            builder.params.addValue("defaultStatus", CLIENT_ACTIVE);
        }
        return builder;
    }

    public String where() {
        return String.join(" and ", conditions);
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
            case "OFFICE" -> {
                final String p = param(ids(values, field));
                conditions.add(Boolean.TRUE.equals(filter.includeChildren())
                        ? "exists (select 1 from m_office po where po.id in (:" + p + ") and o.hierarchy like concat(po.hierarchy, '%'))"
                        : "o.id in (:" + p + ")");
            }
            case "CLIENT" -> conditions.add(in("c.id", ids(values, field), negate));
            case "CLIENT_STATUS" -> {
                statusGiven = true;
                final List<Integer> statuses = values.stream().map(value -> switch (value.toUpperCase(Locale.ROOT)) {
                    case "ACTIVE" -> CLIENT_ACTIVE;
                    case "PENDING" -> CLIENT_PENDING;
                    case "CLOSED" -> CLIENT_CLOSED;
                    default -> throw invalid(field, "Client status must be ACTIVE, PENDING or CLOSED.");
                }).toList();
                conditions.add("c.status_enum in (:" + param(nonEmpty(statuses, field)) + ")");
            }
            case "GENDER" -> conditions.add(in("c.gender_cv_id", ids(values, field), negate));
            case "CLIENT_TYPE" -> conditions.add(in("c.client_type_cv_id", ids(values, field), negate));
            case "CLASSIFICATION" -> conditions.add(in("c.client_classification_cv_id", ids(values, field), negate));
            case "LEGAL_FORM" ->
                conditions.add(in("c.legal_form_enum", nonEmpty(values.stream().map(value -> switch (value.toUpperCase(Locale.ROOT)) {
                    case "PERSON" -> 1;
                    case "ENTITY" -> 2;
                    default -> throw invalid(field, "Legal form must be PERSON or ENTITY.");
                }).toList(), field), negate));
            case "AGE" -> {
                final Integer min = integer(values, 0, field);
                final Integer max = integer(values, 1, field);
                if (min == null && max == null) {
                    throw invalid(field, "Give a youngest age, an oldest age or both.");
                }
                if (min != null) {
                    conditions.add("c.date_of_birth <= :" + param(today.minusYears(min)));
                }
                if (max != null) {
                    conditions.add("c.date_of_birth > :" + param(today.minusYears(max + 1L)));
                }
            }
            case "BIRTHDAY" -> {
                final String when = first(values, field).toUpperCase(Locale.ROOT);
                conditions.add("extract(month from c.date_of_birth) = :" + param(today.getMonthValue()));
                if ("TODAY".equals(when)) {
                    conditions.add("extract(day from c.date_of_birth) = :" + param(today.getDayOfMonth()));
                } else if (!"THIS_MONTH".equals(when)) {
                    throw invalid(field, "Birthday must be TODAY or THIS_MONTH.");
                }
            }
            case "NEW_CLIENTS" -> conditions.add("c.activation_date >= :" + param(today.minusDays(days(values, field))));
            case "HAS_ACTIVE_LOAN" -> conditions.add(exists(yes(values, field),
                    "select 1 from m_loan l where l.client_id = c.id and l.loan_status_id = :" + param(LOAN_ACTIVE)));
            case "LOAN_PRODUCT" -> conditions.add(exists(!negate, "select 1 from m_loan l where l.client_id = c.id and l.loan_status_id = :"
                    + param(LOAN_ACTIVE) + " and l.product_id in (:" + param(ids(values, field)) + ")"));
            case "NEXT_DUE" -> conditions.add("exists (select 1 from m_loan l join m_loan_repayment_schedule rs on rs.loan_id = l.id"
                    + " where l.client_id = c.id and l.loan_status_id = :" + param(LOAN_ACTIVE) + " and rs.completed_derived = :"
                    + param(false) + " and rs.duedate between :" + param(today) + " and :" + param(today.plusDays(days(values, field)))
                    + ")");
            case "DELINQUENCY" -> conditions.add(exists(!negate,
                    "select 1 from m_loan l join m_loan_delinquency_tag_history h on h.loan_id = l.id and h.liftedon_date is null"
                            + " where l.client_id = c.id and l.loan_status_id = :" + param(LOAN_ACTIVE)
                            + " and h.delinquency_range_id in (:" + param(ids(values, field)) + ")"));
            case "LOAN_REPAID" -> conditions.add("exists (select 1 from m_loan l where l.client_id = c.id and l.loan_status_id = :"
                    + param(LOAN_CLOSED_REPAID) + " and l.closedon_date >= :" + param(today.minusDays(days(values, field))) + ")");
            case "HAS_SAVINGS" -> conditions.add(exists(yes(values, field), account(SAVINGS)));
            case "SAVINGS_BALANCE" -> {
                final BigDecimal min = decimal(values, 0, field);
                final BigDecimal max = decimal(values, 1, field);
                if (min == null && max == null) {
                    throw invalid(field, "Give a lowest balance, a highest balance or both.");
                }
                final String total = "(select coalesce(sum(s.account_balance_derived), 0) from m_savings_account s where s.client_id = c.id"
                        + " and s.status_enum = :" + param(ACCOUNT_ACTIVE) + " and s.deposit_type_enum = :" + param(SAVINGS) + ")";
                if (min != null) {
                    conditions.add(total + " >= :" + param(min));
                }
                if (max != null) {
                    conditions.add(total + " <= :" + param(max));
                }
            }
            case "HAS_FIXED_DEPOSIT" -> conditions.add(exists(yes(values, field), account(FIXED_DEPOSIT)));
            case "FD_MATURING" -> conditions.add("exists (select 1 from m_savings_account s join m_deposit_account_term_and_preclosure t"
                    + " on t.savings_account_id = s.id where s.client_id = c.id and s.status_enum = :" + param(ACCOUNT_ACTIVE)
                    + " and s.deposit_type_enum = :" + param(FIXED_DEPOSIT) + " and t.maturity_date between :" + param(today) + " and :"
                    + param(today.plusDays(days(values, field))) + ")");
            case "HAS_RECURRING_DEPOSIT" -> conditions.add(exists(yes(values, field), account(RECURRING_DEPOSIT)));
            case "HAS_SHARES" -> conditions.add(exists(yes(values, field),
                    "select 1 from m_share_account sa where sa.client_id = c.id and sa.status_enum = :" + param(ACCOUNT_ACTIVE)));
            case "SHARES" ->
                conditions.add("(select coalesce(sum(sa.total_approved_shares), 0) from m_share_account sa where sa.client_id = c.id"
                        + " and sa.status_enum = :" + param(ACCOUNT_ACTIVE) + ") >= :" + param(days(values, field)));
            case "MOBILE_APP" -> {
                final String user = "select 1 from m_selfservice_user_client_mapping m join m_appselfservice_user u on u.id = m.appuser_id"
                        + " where m.client_id = c.id and u.is_deleted = :" + param(false);
                switch (first(values, field).toUpperCase(Locale.ROOT)) {
                    case "USES" -> conditions.add("exists (" + user + " and u.status_enum in (:" + param(List.of(APP_ACTIVE, APP_VIEW_ONLY))
                            + ") and u.last_login_on_utc is not null)");
                    case "NEVER_LOGGED_IN" -> conditions
                            .add("exists (" + user + " and u.status_enum <> :" + param(APP_CLOSED) + " and u.last_login_on_utc is null)");
                    case "NOT_ON_APP" -> conditions.add("not exists (" + user + " and u.status_enum <> :" + param(APP_CLOSED) + ")");
                    default -> throw invalid(field, "Mobile app must be USES, NEVER_LOGGED_IN or NOT_ON_APP.");
                }
            }
            case "INACTIVE" -> {
                final String since = param(today.minusDays(days(values, field)));
                final String reversed = param(false);
                conditions.add("not exists (select 1 from m_loan_transaction lt join m_loan l on l.id = lt.loan_id where l.client_id = c.id"
                        + " and lt.is_reversed = :" + reversed + " and lt.transaction_date >= :" + since + ")");
                conditions.add(
                        "not exists (select 1 from m_savings_account_transaction st join m_savings_account s on s.id = st.savings_account_id"
                                + " where s.client_id = c.id and st.is_reversed = :" + reversed + " and st.transaction_date >= :" + since
                                + ")");
            }
            default -> throw invalid("field", "Unknown audience filter " + filter.field() + ".");
        }
    }

    private String account(final int depositType) {
        return "select 1 from m_savings_account s where s.client_id = c.id and s.status_enum = :" + param(ACCOUNT_ACTIVE)
                + " and s.deposit_type_enum = :" + param(depositType);
    }

    private String in(final String column, final List<?> values, final boolean negate) {
        return column + (negate ? " not in (:" : " in (:") + param(values) + ")";
    }

    private static String exists(final boolean yes, final String subquery) {
        return (yes ? "exists (" : "not exists (") + subquery + ")";
    }

    private String param(final Object value) {
        final String name = "p" + index++;
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

    private static String first(final List<String> values, final String field) {
        if (values.isEmpty() || StringUtils.isBlank(values.get(0))) {
            throw invalid(field, "Choose a value for " + field + ".");
        }
        return values.get(0).trim();
    }

    private static boolean yes(final List<String> values, final String field) {
        return !"NO".equalsIgnoreCase(values.isEmpty() ? "YES" : values.get(0).trim());
    }

    private static int days(final List<String> values, final String field) {
        final Integer number = integer(values, 0, field);
        if (number == null || number < 0 || number > 3650) {
            throw invalid(field, "Give a number of days (or shares) from 0 to 3650.");
        }
        return number;
    }

    private static Integer integer(final List<String> values, final int at, final String field) {
        if (values.size() <= at || StringUtils.isBlank(values.get(at))) {
            return null;
        }
        try {
            return Integer.valueOf(values.get(at).trim());
        } catch (NumberFormatException e) {
            throw invalid(field, "Give whole numbers for " + field + ".", e);
        }
    }

    private static BigDecimal decimal(final List<String> values, final int at, final String field) {
        if (values.size() <= at || StringUtils.isBlank(values.get(at))) {
            return null;
        }
        try {
            return new BigDecimal(values.get(at).trim());
        } catch (NumberFormatException e) {
            throw invalid(field, "Give amounts as numbers for " + field + ".", e);
        }
    }

    private static PlatformApiDataValidationException invalid(final String field, final String message) {
        return invalid(field, message, null);
    }

    private static PlatformApiDataValidationException invalid(final String field, final String message, final Throwable cause) {
        return new PlatformApiDataValidationException("validation.msg.comm.audience." + field.toLowerCase(Locale.ROOT) + ".invalid",
                message, "audience", cause);
    }

    /** Fields the audience screen offers, in display order. */
    public static final List<Map<String, String>> FIELDS = List.of(Map.of("field", "CLIENT", "label", "Specific clients"),
            Map.of("field", "OFFICE", "label", "Office"), Map.of("field", "CLIENT_STATUS", "label", "Client status"),
            Map.of("field", "GENDER", "label", "Gender"), Map.of("field", "AGE", "label", "Age"),
            Map.of("field", "BIRTHDAY", "label", "Birthday"), Map.of("field", "CLIENT_TYPE", "label", "Client type"),
            Map.of("field", "CLASSIFICATION", "label", "Classification"), Map.of("field", "LEGAL_FORM", "label", "Legal form"),
            Map.of("field", "NEW_CLIENTS", "label", "New clients"), Map.of("field", "HAS_ACTIVE_LOAN", "label", "Has an active loan"),
            Map.of("field", "LOAN_PRODUCT", "label", "Loan product"), Map.of("field", "NEXT_DUE", "label", "Loan payment due within"),
            Map.of("field", "DELINQUENCY", "label", "Delinquency"), Map.of("field", "LOAN_REPAID", "label", "Loan repaid within"),
            Map.of("field", "HAS_SAVINGS", "label", "Has savings"), Map.of("field", "SAVINGS_BALANCE", "label", "Savings balance"),
            Map.of("field", "HAS_FIXED_DEPOSIT", "label", "Has fixed deposit"),
            Map.of("field", "FD_MATURING", "label", "Fixed deposit maturing within"),
            Map.of("field", "HAS_RECURRING_DEPOSIT", "label", "Has recurring deposit"),
            Map.of("field", "HAS_SHARES", "label", "Has shares"), Map.of("field", "SHARES", "label", "Shares at least"),
            Map.of("field", "MOBILE_APP", "label", "Mobile app"), Map.of("field", "INACTIVE", "label", "No transactions in"));
}
