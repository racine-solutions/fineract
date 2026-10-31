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
package org.apache.fineract.selfservice.account.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.savings.exception.SavingsAccountNotFoundException;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserClientMapping;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Checks every money movement made in the mobile app before it runs, and records it afterwards.
 *
 * <ul>
 * <li>The source account must belong to one of the user's linked clients (internal transfers).</li>
 * <li>The amount must be within the user's per-transfer and daily limits for the account's currency, if staff set
 * them.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SelfServiceTransferGuard {

    /** How the transfer was made in the app. Stored with each recorded transfer. */
    public enum Channel {
        ACCOUNT_TRANSFER, CONFIRMED_TRANSFER, SINPE
    }

    private final JdbcTemplate jdbcTemplate;
    private final SelfAccountTransferReadService selfAccountTransferReadService;

    /**
     * Validates a transfer that is about to be made and returns the currency it is made in.
     *
     * @param currencyCode
     *            currency of an external transfer (SINPE); ignored for internal transfers, which use the account's
     *            currency
     */
    public String check(final AppSelfServiceUser user, final Long fromAccountId, final Integer fromAccountType, final BigDecimal amount,
            final Channel channel, final String currencyCode) {
        final String currency;
        if (channel == Channel.SINPE) {
            currency = StringUtils.defaultIfBlank(currencyCode, "CRC");
        } else {
            validateOwnership(user, fromAccountId, fromAccountType);
            currency = accountCurrency(fromAccountId, fromAccountType);
        }
        validateLimits(user, currency, amount);
        return currency;
    }

    /** Records a completed transfer; it then counts towards the user's daily limit. */
    public void record(final AppSelfServiceUser user, final Channel channel, final String currency, final BigDecimal amount,
            final String fromAccountId, final Integer fromAccountType, final String toAccountId, final Integer toAccountType,
            final Long referenceId) {
        jdbcTemplate.update(
                "insert into m_selfservice_transfer_log (appuser_id, client_id, channel, currency_code, amount, transfer_date,"
                        + " from_account_id, from_account_type, to_account_id, to_account_type, reference_id, created_on_utc)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                user.getId(), primaryClientId(user), channel.name(), currency, amount, DateUtils.getBusinessLocalDate(), fromAccountId,
                fromAccountType, toAccountId, toAccountType, referenceId, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
    }

    private void validateOwnership(final AppSelfServiceUser user, final Long accountId, final Integer accountType) {
        if (accountId == null || accountType == null) {
            throw new SavingsAccountNotFoundException(accountId);
        }
        final boolean owned = selfAccountTransferReadService.retrieveSelfAccountTemplateData(user).stream()
                .anyMatch(account -> accountId.equals(account.getAccountId()) && accountType.equals(account.getAccountType()));
        if (owned) {
            return;
        }
        if (PortfolioAccountType.LOAN.getValue().equals(accountType)) {
            throw new LoanNotFoundException(accountId);
        }
        throw new SavingsAccountNotFoundException(accountId);
    }

    private String accountCurrency(final Long accountId, final Integer accountType) {
        final String table = PortfolioAccountType.LOAN.getValue().equals(accountType) ? "m_loan" : "m_savings_account";
        final List<String> currencies = jdbcTemplate.queryForList("select currency_code from " + table + " where id = ?", String.class, // NOSONAR
                accountId);
        return currencies.isEmpty() ? null : currencies.get(0);
    }

    private void validateLimits(final AppSelfServiceUser user, final String currency, final BigDecimal amount) {
        if (currency == null || amount == null) {
            return;
        }
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select per_transfer_limit, daily_limit from m_selfservice_user_limit where appuser_id = ? and currency_code = ?",
                user.getId(), currency);
        if (rows.isEmpty()) {
            return;
        }
        final BigDecimal perTransfer = (BigDecimal) rows.get(0).get("per_transfer_limit");
        final BigDecimal daily = (BigDecimal) rows.get(0).get("daily_limit");

        if (perTransfer != null && amount.compareTo(perTransfer) > 0) {
            throw new GeneralPlatformDomainRuleException("error.msg.selfservice.transfer.limit.per.transfer.exceeded",
                    "The amount is above your limit of " + perTransfer.stripTrailingZeros().toPlainString() + " " + currency
                            + " per transfer. Contact your branch to change it.",
                    amount, perTransfer, currency);
        }
        if (daily != null) {
            final BigDecimal usedToday = usedOn(user.getId(), currency, DateUtils.getBusinessLocalDate());
            if (usedToday.add(amount).compareTo(daily) > 0) {
                final BigDecimal remaining = daily.subtract(usedToday).max(BigDecimal.ZERO);
                throw new GeneralPlatformDomainRuleException("error.msg.selfservice.transfer.limit.daily.exceeded",
                        "The amount is above what you can still transfer today: " + remaining.stripTrailingZeros().toPlainString() + " "
                                + currency + " of your daily limit of " + daily.stripTrailingZeros().toPlainString() + " " + currency + ".",
                        amount, remaining, daily, currency);
            }
        }
    }

    /** Total transferred in the app by the user on the given day, in one currency. */
    public BigDecimal usedOn(final Long userId, final String currency, final LocalDate day) {
        final BigDecimal used = jdbcTemplate.queryForObject(
                "select coalesce(sum(amount), 0) from m_selfservice_transfer_log where appuser_id = ? and currency_code = ? and transfer_date = ?",
                BigDecimal.class, userId, currency, day);
        return used == null ? BigDecimal.ZERO : used;
    }

    private static Long primaryClientId(final AppSelfServiceUser user) {
        if (user.getAppUserClientMappings() == null) {
            return null;
        }
        return user.getAppUserClientMappings().stream().map(AppSelfServiceUserClientMapping::getClient).filter(c -> c != null)
                .map(c -> c.getId()).filter(id -> id != null).min(Long::compare).orElse(null);
    }
}
