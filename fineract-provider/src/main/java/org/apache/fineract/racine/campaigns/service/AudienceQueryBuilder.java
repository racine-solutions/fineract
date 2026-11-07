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
    private static final int LOAN_SUBMITTED = 100;
    private static final int LOAN_APPROVED = 200;
    private static final int LOAN_ACTIVE = 300;
    private static final int LOAN_CLOSED_REPAID = 600;
    private static final int ACCOUNT_ACTIVE = 300;
    private static final int SAVINGS = 100;
    private static final int FIXED_DEPOSIT = 200;
    private static final int RECURRING_DEPOSIT = 300;
    private static final int APP_ACTIVE = 300;
    private static final int APP_VIEW_ONLY = 400;
    private static final int APP_CLOSED = 600;
    private static final int SAVINGS_DEPOSIT = 1;

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
            case StaffAudienceQueryBuilder.RECIPIENTS, "IS_STAFF" -> {
                // The audience type, and a phone and email match made after the query (AudienceService)
            }
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
            case "LOAN_OFFICER" -> conditions.add(in("c.staff_id", ids(values, field), negate));
            case "MEMBER_FOR" -> {
                final Integer min = integer(values, 0, field);
                final Integer max = integer(values, 1, field);
                if (min == null && max == null) {
                    throw invalid(field, "Give the fewest years, the most years or both.");
                }
                if (min != null) {
                    conditions.add("c.activation_date <= :" + param(today.minusYears(min)));
                }
                if (max != null) {
                    conditions.add("c.activation_date > :" + param(today.minusYears(max + 1L)));
                }
            }
            case "HAS_PHONE" -> conditions.add(
                    yes(values, field) ? "(c.mobile_no is not null and c.mobile_no <> '')" : "(c.mobile_no is null or c.mobile_no = '')");
            case "HAS_EMAIL" -> conditions.add(yes(values, field) ? "(c.email_address is not null and c.email_address <> '')"
                    : "(c.email_address is null or c.email_address = '')");
            case "GROUP" -> conditions.add(exists(!negate,
                    "select 1 from m_group_client gc where gc.client_id = c.id and gc.group_id in (:" + param(ids(values, field)) + ")"));
            case "IN_GROUP" -> conditions.add(exists(yes(values, field), "select 1 from m_group_client gc where gc.client_id = c.id"));
            case "LOAN_BALANCE" -> range(values, field, "(select coalesce(sum(l.total_outstanding_derived), 0) from m_loan l"
                    + " where l.client_id = c.id and l.loan_status_id = :" + param(LOAN_ACTIVE) + ")");
            case "DAYS_IN_ARREARS" -> {
                final Integer min = integer(values, 0, field);
                final Integer max = integer(values, 1, field);
                if (min == null && max == null) {
                    throw invalid(field, "Give the fewest days, the most days or both.");
                }
                final StringBuilder late = new StringBuilder("select 1 from m_loan_arrears_aging a join m_loan l on l.id = a.loan_id"
                        + " where l.client_id = c.id and l.loan_status_id = :" + param(LOAN_ACTIVE)
                        + " and a.overdue_since_date_derived is not null");
                if (min != null) {
                    late.append(" and a.overdue_since_date_derived <= :").append(param(today.minusDays(min)));
                }
                if (max != null) {
                    late.append(" and a.overdue_since_date_derived >= :").append(param(today.minusDays(max)));
                }
                conditions.add("exists (" + late + ")");
            }
            case "LOAN_ENDING" -> conditions.add("exists (select 1 from m_loan l where l.client_id = c.id and l.loan_status_id = :"
                    + param(LOAN_ACTIVE) + " and l.expected_maturedon_date between :" + param(today) + " and :"
                    + param(today.plusDays(days(values, field))) + ")");
            case "LOAN_DISBURSED" -> conditions.add("exists (select 1 from m_loan l where l.client_id = c.id and l.disbursedon_date >= :"
                    + param(today.minusDays(days(values, field))) + ")");
            case "LOANS_TAKEN" ->
                range(values, field, "(select count(*) from m_loan l where l.client_id = c.id and l.disbursedon_date is not null)");
            case "LOAN_PENDING" -> conditions.add(exists(yes(values, field), "select 1 from m_loan l where l.client_id = c.id"
                    + " and l.loan_status_id in (:" + param(List.of(LOAN_SUBMITTED, LOAN_APPROVED)) + ")"));
            case "LOAN_PURPOSE" -> conditions.add(exists(!negate, "select 1 from m_loan l where l.client_id = c.id and l.loan_status_id = :"
                    + param(LOAN_ACTIVE) + " and l.loanpurpose_cv_id in (:" + param(ids(values, field)) + ")"));
            case "SAVINGS_PRODUCT" ->
                conditions.add(exists(!negate, account(SAVINGS) + " and s.product_id in (:" + param(ids(values, field)) + ")"));
            case "NO_DEPOSIT_IN" -> {
                conditions.add(exists(true, account(SAVINGS)));
                conditions.add("not exists (select 1 from m_savings_account_transaction st join m_savings_account s"
                        + " on s.id = st.savings_account_id where s.client_id = c.id and st.transaction_type_enum = :"
                        + param(SAVINGS_DEPOSIT) + " and st.is_reversed = :" + param(false) + " and st.transaction_date >= :"
                        + param(today.minusDays(days(values, field))) + ")");
            }
            default -> throw invalid("field", "Unknown audience filter " + filter.field() + ".");
        }
    }

    /** "At least" and/or "at most" on a number worked out per client. */
    private void range(final List<String> values, final String field, final String expression) {
        final BigDecimal min = decimal(values, 0, field);
        final BigDecimal max = decimal(values, 1, field);
        if (min == null && max == null) {
            throw invalid(field, "Give a lowest value, a highest value or both.");
        }
        if (min != null) {
            conditions.add(expression + " >= :" + param(min));
        }
        if (max != null) {
            conditions.add(expression + " <= :" + param(max));
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
    public static final List<Map<String, String>> FIELDS = List.of(
            // Client
            field("CLIENT", "Specific clients"), field("OFFICE", "Office"), field("LOAN_OFFICER", "Loan officer"), field("GROUP", "Group"),
            field("IN_GROUP", "In a group"), field("CLIENT_STATUS", "Client status"), field("GENDER", "Gender"), field("AGE", "Age"),
            field("BIRTHDAY", "Birthday"), field("CLIENT_TYPE", "Client type"), field("CLASSIFICATION", "Classification"),
            field("LEGAL_FORM", "Legal form"), field("NEW_CLIENTS", "New clients"), field("MEMBER_FOR", "Member for"),
            field("HAS_PHONE", "Has a phone number"), field("HAS_EMAIL", "Has an email"), field("IS_STAFF", "Is a staff member"),
            field("MOBILE_APP", "Mobile app"), field("INACTIVE", "No transactions in"),
            // Loans
            field("HAS_ACTIVE_LOAN", "Has an active loan"), field("LOAN_PRODUCT", "Loan product"), field("LOAN_PURPOSE", "Loan purpose"),
            field("LOAN_BALANCE", "Loan balance"), field("NEXT_DUE", "Loan payment due within"),
            field("DAYS_IN_ARREARS", "Days in arrears"), field("DELINQUENCY", "Delinquency"), field("LOAN_ENDING", "Loan ending within"),
            field("LOAN_DISBURSED", "Loan disbursed within"), field("LOANS_TAKEN", "Loans taken"),
            field("LOAN_PENDING", "Loan application pending"), field("LOAN_REPAID", "Loan repaid within"),
            // Savings, deposits and shares
            field("HAS_SAVINGS", "Has savings"), field("SAVINGS_PRODUCT", "Savings product"), field("SAVINGS_BALANCE", "Savings balance"),
            field("NO_DEPOSIT_IN", "No savings deposit in"), field("HAS_FIXED_DEPOSIT", "Has fixed deposit"),
            field("FD_MATURING", "Fixed deposit maturing within"), field("HAS_RECURRING_DEPOSIT", "Has recurring deposit"),
            field("HAS_SHARES", "Has shares"), field("SHARES", "Shares at least"));

    private static Map<String, String> field(final String field, final String label) {
        return Map.of("field", field, "label", label);
    }
}
