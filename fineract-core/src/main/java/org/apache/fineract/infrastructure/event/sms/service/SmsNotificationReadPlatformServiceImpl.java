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
package org.apache.fineract.infrastructure.event.sms.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.event.external.data.SmsCreditStatusData;
import org.apache.fineract.infrastructure.event.external.data.SmsNotificationAccountData;
import org.apache.fineract.infrastructure.event.external.data.SmsNotificationMessageData;
import org.apache.fineract.infrastructure.event.external.data.SmsWalletTransactionData;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsNotificationReadPlatformServiceImpl implements SmsNotificationReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final PaginationHelper paginationHelper;
    private final DatabaseSpecificSQLGenerator sqlGenerator;

    private static final class SmsAccountRowMapper implements RowMapper<SmsNotificationAccountData> {

        @Override
        public SmsNotificationAccountData mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long id = rs.getLong("id");
            java.math.BigDecimal totalPaymentAmount = rs.getBigDecimal("total_payment_amount");
            Integer smsTotalCredit = rs.getInt("sms_total_credit");
            Integer smsTotalBalance = rs.getInt("sms_total_balance");
            boolean isActive = rs.getBoolean("is_active");
            return new SmsNotificationAccountData(id, totalPaymentAmount, smsTotalCredit, smsTotalBalance, isActive);
        }
    }

    private static final class SmsMessageRowMapper implements RowMapper<SmsNotificationMessageData> {

        @Override
        public SmsNotificationMessageData mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long id = rs.getLong("id");
            String message = rs.getString("message");
            String number = rs.getString("number");
            String senderid = rs.getString("sender_id");
            String smsResponse = rs.getString("sms_response");
            String smsTypeEnum = rs.getString("sms_type_enum");
            boolean hasPassed = rs.getBoolean("has_passed");
            OffsetDateTime createdOnUtc = null;
            Timestamp ts = rs.getTimestamp("created_on_utc");
            if (ts != null) {
                createdOnUtc = ts.toInstant().atOffset(ZoneOffset.UTC);
            }
            return new SmsNotificationMessageData(id, message, number, senderid, smsResponse, smsTypeEnum, hasPassed, createdOnUtc);
        }
    }

    @Override
    public SmsNotificationAccountData retrieveSmsAccount() {
        try {
            final String sql = "SELECT id, total_payment_amount, sms_total_credit, sms_total_balance, is_active "
                    + "FROM m_sms_notification_account WHERE id = 1";
            return jdbcTemplate.queryForObject(sql, new SmsAccountRowMapper()); // NOSONAR
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public SmsCreditStatusData retrieveCreditStatus() {
        final SmsNotificationAccountData account = retrieveSmsAccount();
        final int balance = account == null || account.getSmsTotalBalance() == null ? 0 : account.getSmsTotalBalance();

        // The balance right after the last top-up is what "used" is measured against
        final String lastTopUp = "SELECT previous_balance, sms_credit, sms_cost FROM m_sms_notification_account_transaction "
                + "ORDER BY id DESC " + sqlGenerator.limit(1);
        final Object[] topUp = jdbcTemplate.query(lastTopUp,
                rs -> rs.next() ? new Object[] { rs.getInt("previous_balance") + rs.getInt("sms_credit"), rs.getBigDecimal("sms_cost") }
                        : new Object[] { account == null || account.getSmsTotalCredit() == null ? 0 : account.getSmsTotalCredit(), null });
        final int topUpBalance = (Integer) topUp[0];
        final BigDecimal smsCost = (BigDecimal) topUp[1];

        final SmsCreditStatusData.OffReason offReason = smsOffReason(account);
        if (offReason != null) {
            return SmsCreditStatusData.smsOff(offReason, balance, topUpBalance, smsCost);
        }
        final Integer lowAt = alertLevel(SmsCreditStatusData.LOW_ALERT_CONFIG, SmsCreditStatusData.DEFAULT_LOW_AT_PERCENT_USED);
        final Integer criticalAt = alertLevel(SmsCreditStatusData.CRITICAL_ALERT_CONFIG,
                SmsCreditStatusData.DEFAULT_CRITICAL_AT_PERCENT_USED);
        return SmsCreditStatusData.of(true, balance, topUpBalance, smsCost, lowAt, criticalAt);
    }

    /** Why no SMS reaches clients, or null when SMS is working. */
    private SmsCreditStatusData.OffReason smsOffReason(final SmsNotificationAccountData account) {
        final Boolean switchedOn = jdbcTemplate.query("SELECT enabled FROM c_configuration WHERE name = 'enable-sms-notifications'",
                rs -> rs.next() ? rs.getBoolean("enabled") : Boolean.FALSE);
        if (!Boolean.TRUE.equals(switchedOn)) {
            return SmsCreditStatusData.OffReason.SMS_NOTIFICATIONS_DISABLED;
        }
        if (account == null || !Boolean.TRUE.equals(account.getIsActive())) {
            return SmsCreditStatusData.OffReason.SMS_ACCOUNT_INACTIVE;
        }
        final Integer eventsOn = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_sms_event_configuration WHERE enabled = ?",
                Integer.class, true);
        return eventsOn == null || eventsOn == 0 ? SmsCreditStatusData.OffReason.ALL_EVENTS_DISABLED : null;
    }

    /** The alert level set under System > Configurations; see {@link SmsCreditStatusData#alertLevel}. */
    private Integer alertLevel(final String configName, final int defaultValue) {
        return jdbcTemplate.query("SELECT enabled, value FROM c_configuration WHERE name = ?", rs -> {
            if (!rs.next()) {
                return defaultValue;
            }
            final long value = rs.getLong("value");
            final Long configured = rs.wasNull() ? null : value;
            return SmsCreditStatusData.alertLevel(rs.getBoolean("enabled"), configured, defaultValue);
        }, configName);
    }

    @Override
    public Page<SmsNotificationMessageData> retrieveMessages(final SearchParameters searchParameters) {
        final StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT ").append(sqlGenerator.calcFoundRows()).append(" ");
        sqlBuilder.append("id, message, number, sender_id, sms_response, sms_type_enum, has_passed, created_on_utc ");
        sqlBuilder.append("FROM m_sms_notification_account_message ");
        sqlBuilder.append("ORDER BY created_on_utc DESC");

        if (searchParameters.hasLimit()) {
            sqlBuilder.append(" ");
            if (searchParameters.hasOffset()) {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
            } else {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit()));
            }
        }

        return paginationHelper.fetchPage(jdbcTemplate, sqlBuilder.toString(), new Object[] {}, new SmsMessageRowMapper());
    }

    private static final class WalletTransactionRowMapper implements RowMapper<SmsWalletTransactionData> {

        @Override
        public SmsWalletTransactionData mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long id = rs.getLong("id");
            java.math.BigDecimal amount = rs.getBigDecimal("amount");
            java.math.BigDecimal smsCost = rs.getBigDecimal("sms_cost");
            Integer smsCredit = rs.getInt("sms_credit");
            Integer previousBalance = rs.getInt("previous_balance");
            String note = rs.getString("note");
            OffsetDateTime createdOnUtc = null;
            Timestamp ts = rs.getTimestamp("created_on_utc");
            if (ts != null) {
                createdOnUtc = ts.toInstant().atOffset(ZoneOffset.UTC);
            }
            return new SmsWalletTransactionData(id, amount, smsCost, smsCredit, previousBalance, note, createdOnUtc);
        }
    }

    @Override
    public Page<SmsWalletTransactionData> retrieveWalletTransactions(final SearchParameters searchParameters) {
        final StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT ").append(sqlGenerator.calcFoundRows()).append(" ");
        sqlBuilder.append("id, amount, sms_cost, sms_credit, previous_balance, note, created_on_utc ");
        sqlBuilder.append("FROM m_sms_notification_account_transaction ");
        sqlBuilder.append("ORDER BY created_on_utc DESC");

        if (searchParameters.hasLimit()) {
            sqlBuilder.append(" ");
            if (searchParameters.hasOffset()) {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
            } else {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit()));
            }
        }

        return paginationHelper.fetchPage(jdbcTemplate, sqlBuilder.toString(), new Object[] {}, new WalletTransactionRowMapper());
    }
}
