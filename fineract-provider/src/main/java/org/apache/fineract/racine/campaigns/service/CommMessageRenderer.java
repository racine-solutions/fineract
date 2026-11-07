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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.notification.service.SmsText;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Fills a campaign message for each client. The values for a whole batch of clients are read in a handful of queries
 * (not one REST call per client as the Templates merge does), so a campaign of thousands stays fast.
 */
@Service
@RequiredArgsConstructor
public class CommMessageRenderer {

    private static final int LOAN_ACTIVE = 300;
    private static final int ACCOUNT_ACTIVE = 300;
    private static final int SAVINGS = 100;
    private static final int FIXED_DEPOSIT = 200;

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;

    /** Plain text output: Mustache's HTML escaping would turn "&" into "&amp;" in an SMS. */
    private static final DefaultMustacheFactory MUSTACHE = new DefaultMustacheFactory() {

        @Override
        public void encode(final String value, final Writer writer) {
            try {
                writer.write(value);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    /** HTML output for email bodies: client details are escaped, so a name like "A & B" can't break the layout. */
    private static final DefaultMustacheFactory MUSTACHE_HTML = new DefaultMustacheFactory();

    public Mustache compile(final String text, final String name) {
        return MUSTACHE.compile(new StringReader(text), name);
    }

    public Mustache compileHtml(final String html, final String name) {
        return MUSTACHE_HTML.compile(new StringReader(asHtml(html)), name);
    }

    /**
     * HTML tags such as
     * <p>
     * or <br>
     * ; without any, an email body is plain text.
     */
    private static final java.util.regex.Pattern HTML_TAG = java.util.regex.Pattern.compile("</?[a-zA-Z][^>]*>");

    /** An email body as HTML: plain text keeps its line breaks and shows "&" and "<" as typed. */
    static String asHtml(final String text) {
        if (text == null || HTML_TAG.matcher(text).find()) {
            return text;
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\r\n", "\n").replace("\n", "<br>\n");
    }

    public String render(final Mustache mustache, final Map<String, Object> scope) {
        final StringWriter out = new StringWriter();
        mustache.execute(out, scope);
        return out.toString().trim();
    }

    /** Placeholder values for each client, keyed by client id; clients that do not exist are left out. */
    public Map<Long, Map<String, Object>> scopes(final Collection<Long> clientIds) {
        final Map<Long, Map<String, Object>> scopes = new HashMap<>();
        if (clientIds.isEmpty()) {
            return scopes;
        }
        final NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbcTemplate);
        final MapSqlParameterSource ids = new MapSqlParameterSource("ids", clientIds);
        final String currency = defaultCurrency();
        final String tenant = SmsText.currentTenantShortName();
        final LocalDate today = DateUtils.getBusinessLocalDate();

        named.query("select c.id, c.firstname, c.lastname, c.display_name, c.mobile_no, c.email_address, c.account_no, o.name office_name"
                + " from m_client c join m_office o on o.id = c.office_id where c.id in (:ids)", ids, rs -> {
                    final Map<String, Object> client = new HashMap<>();
                    final String name = SmsText.recipientName(rs.getString("lastname"), rs.getString("firstname"));
                    client.put("name",
                            name != null ? name : SmsText.shorten(StringUtils.defaultIfBlank(rs.getString("display_name"), "Customer")));
                    client.put("firstName", StringUtils.defaultIfBlank(rs.getString("firstname"), client.get("name").toString()));
                    client.put("fullName", StringUtils.defaultString(rs.getString("display_name")));
                    client.put("phone", StringUtils.defaultString(rs.getString("mobile_no")));
                    // Where a campaign email goes; not a placeholder
                    client.put("email", StringUtils.defaultString(rs.getString("email_address")));
                    client.put("accountNo", SmsText.maskAccountNumber(rs.getString("account_no")));
                    client.put("office", StringUtils.defaultString(rs.getString("office_name")));
                    final Map<String, Object> scope = new HashMap<>();
                    scope.put("client", client);
                    scope.put("loans", new HashMap<>(Map.of("totalOutstanding", money(currency, null), "nextDueDate", "-", "nextDueAmount",
                            money(currency, null), "daysInArrears", "0", "arrearsAmount", money(currency, null))));
                    scope.put("savings", new HashMap<>(Map.of("totalBalance", money(currency, null))));
                    scope.put("fixedDeposit", new HashMap<>(Map.of("nextMaturityDate", "-", "maturityAmount", money(currency, null))));
                    scope.put("shares", new HashMap<>(Map.of("count", "0", "value", money(currency, null))));
                    scope.put("tenant", Map.of("name", StringUtils.defaultString(tenant)));
                    scopes.put(rs.getLong("id"), scope);
                });

        final MapSqlParameterSource active = new MapSqlParameterSource("ids", clientIds).addValue("active", LOAN_ACTIVE);
        named.query(
                "select l.client_id, max(l.currency_code) currency_code, sum(l.total_outstanding_derived) outstanding from m_loan l"
                        + " where l.client_id in (:ids) and l.loan_status_id = :active group by l.client_id",
                active, (RowCallbackHandler) rs -> put(scopes, rs.getLong("client_id"), "loans", "totalOutstanding",
                        money(rs.getString("currency_code"), rs.getBigDecimal("outstanding"))));

        // Amount still due on each open instalment date; the earliest date per client is the next payment
        final Map<Long, Object[]> nextDue = new HashMap<>();
        named.query("select l.client_id, max(l.currency_code) currency_code, rs.duedate, sum("
                + " coalesce(rs.principal_amount, 0) - coalesce(rs.principal_completed_derived, 0) - coalesce(rs.principal_writtenoff_derived, 0)"
                + " + coalesce(rs.interest_amount, 0) - coalesce(rs.interest_completed_derived, 0) - coalesce(rs.interest_waived_derived, 0)"
                + " - coalesce(rs.interest_writtenoff_derived, 0)"
                + " + coalesce(rs.fee_charges_amount, 0) - coalesce(rs.fee_charges_completed_derived, 0) - coalesce(rs.fee_charges_waived_derived, 0)"
                + " - coalesce(rs.fee_charges_writtenoff_derived, 0)"
                + " + coalesce(rs.penalty_charges_amount, 0) - coalesce(rs.penalty_charges_completed_derived, 0)"
                + " - coalesce(rs.penalty_charges_waived_derived, 0) - coalesce(rs.penalty_charges_writtenoff_derived, 0)) due"
                + " from m_loan l join m_loan_repayment_schedule rs on rs.loan_id = l.id"
                + " where l.client_id in (:ids) and l.loan_status_id = :active and rs.completed_derived = :notCompleted"
                + " group by l.client_id, rs.duedate", active.addValue("notCompleted", false), rs -> {
                    final LocalDate date = rs.getObject("duedate", LocalDate.class);
                    final Object[] current = nextDue.get(rs.getLong("client_id"));
                    if (current == null || date.isBefore((LocalDate) current[0])) {
                        nextDue.put(rs.getLong("client_id"), new Object[] { date, rs.getString("currency_code"), rs.getBigDecimal("due") });
                    }
                });
        nextDue.forEach((clientId, due) -> {
            put(scopes, clientId, "loans", "nextDueDate", ((LocalDate) due[0]).format(CommSupport.MESSAGE_DATE));
            put(scopes, clientId, "loans", "nextDueAmount", money((String) due[1], (BigDecimal) due[2]));
        });

        named.query("select l.client_id, max(l.currency_code) currency_code, sum(a.total_overdue_derived) overdue,"
                + " min(a.overdue_since_date_derived) since from m_loan_arrears_aging a join m_loan l on l.id = a.loan_id"
                + " where l.client_id in (:ids) and l.loan_status_id = :active group by l.client_id", active, rs -> {
                    put(scopes, rs.getLong("client_id"), "loans", "arrearsAmount",
                            money(rs.getString("currency_code"), rs.getBigDecimal("overdue")));
                    final LocalDate since = rs.getObject("since", LocalDate.class);
                    if (since != null) {
                        put(scopes, rs.getLong("client_id"), "loans", "daysInArrears",
                                String.valueOf(Math.max(0, ChronoUnit.DAYS.between(since, today))));
                    }
                });

        final MapSqlParameterSource accounts = new MapSqlParameterSource("ids", clientIds).addValue("active", ACCOUNT_ACTIVE);
        named.query(
                "select s.client_id, max(s.currency_code) currency_code, sum(s.account_balance_derived) balance from m_savings_account s"
                        + " where s.client_id in (:ids) and s.status_enum = :active and s.deposit_type_enum = :savings group by s.client_id",
                accounts.addValue("savings", SAVINGS), (RowCallbackHandler) rs -> put(scopes, rs.getLong("client_id"), "savings",
                        "totalBalance", money(rs.getString("currency_code"), rs.getBigDecimal("balance"))));

        final Map<Long, Object[]> maturity = new HashMap<>();
        named.query(
                "select s.client_id, s.currency_code, t.maturity_date, t.maturity_amount from m_savings_account s"
                        + " join m_deposit_account_term_and_preclosure t on t.savings_account_id = s.id where s.client_id in (:ids)"
                        + " and s.status_enum = :active and s.deposit_type_enum = :fixed and t.maturity_date >= :today",
                accounts.addValue("fixed", FIXED_DEPOSIT).addValue("today", today), rs -> {
                    final LocalDate date = rs.getObject("maturity_date", LocalDate.class);
                    final Object[] current = maturity.get(rs.getLong("client_id"));
                    if (current == null || date.isBefore((LocalDate) current[0])) {
                        maturity.put(rs.getLong("client_id"),
                                new Object[] { date, rs.getString("currency_code"), rs.getBigDecimal("maturity_amount") });
                    }
                });
        maturity.forEach((clientId, row) -> {
            put(scopes, clientId, "fixedDeposit", "nextMaturityDate", ((LocalDate) row[0]).format(CommSupport.MESSAGE_DATE));
            put(scopes, clientId, "fixedDeposit", "maturityAmount", money((String) row[1], (BigDecimal) row[2]));
        });

        named.query("select sa.client_id, max(sa.currency_code) currency_code, sum(sa.total_approved_shares) shares,"
                + " sum(sa.total_approved_shares * sp.unit_price) share_value from m_share_account sa"
                + " join m_share_product sp on sp.id = sa.product_id where sa.client_id in (:ids) and sa.status_enum = :active"
                + " group by sa.client_id", accounts, rs -> {
                    put(scopes, rs.getLong("client_id"), "shares", "count", String.valueOf(rs.getLong("shares")));
                    put(scopes, rs.getLong("client_id"), "shares", "value",
                            money(rs.getString("currency_code"), rs.getBigDecimal("share_value")));
                });
        return scopes;
    }

    /** Example values, for a preview before any client is chosen. */
    public static Map<String, Object> exampleScope() {
        final Map<String, Object> scope = new LinkedHashMap<>();
        for (var placeholder : CommPlaceholders.ALL) {
            final String[] parts = placeholder.key().split("\\.");
            @SuppressWarnings("unchecked")
            final Map<String, Object> group = (Map<String, Object>) scope.computeIfAbsent(parts[0], key -> new HashMap<String, Object>());
            group.put(parts[1], placeholder.example());
        }
        return scope;
    }

    @SuppressWarnings("unchecked")
    private static void put(final Map<Long, Map<String, Object>> scopes, final Long clientId, final String group, final String key,
            final Object value) {
        final Map<String, Object> scope = scopes.get(clientId);
        if (scope != null) {
            ((Map<String, Object>) scope.get(group)).put(key, value);
        }
    }

    /** "UGX 1,250,000" or "UGX 85,000.50"; amounts are shown in full, never rounded to whole thousands. */
    static String money(final String currency, final BigDecimal amount) {
        final DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        final String number = format.format(amount == null ? BigDecimal.ZERO : amount);
        return StringUtils.isBlank(currency) ? number : currency + " " + number;
    }

    /** The currency most of the institution's loans and savings use, for clients with no account of a kind. */
    private String defaultCurrency() {
        final List<String> codes = jdbcTemplate.queryForList("select currency_code from (select currency_code, count(*) n from m_loan"
                + " group by currency_code union all select currency_code, count(*) n from m_savings_account group by currency_code) x"
                + " order by n desc " + sqlGenerator.limit(1), String.class);
        return codes.isEmpty() ? "" : codes.get(0);
    }
}
