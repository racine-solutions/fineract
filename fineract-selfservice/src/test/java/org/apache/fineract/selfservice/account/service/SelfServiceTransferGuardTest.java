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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.savings.exception.SavingsAccountNotFoundException;
import org.apache.fineract.selfservice.account.data.SelfAccountTemplateData;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SelfServiceTransferGuardTest {

    private static final Long USER_ID = 42L;
    private static final Long OWN_ACCOUNT = 4L;
    private static final Integer SAVINGS = 2;

    private JdbcTemplate jdbcTemplate;
    private SelfAccountTransferReadService readService;
    private SelfServiceTransferGuard guard;
    private AppSelfServiceUser user;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default Tenant", "UTC", null));
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 31));
        ThreadLocalContextUtil.setBusinessDates(businessDates);

        jdbcTemplate = mock(JdbcTemplate.class);
        readService = mock(SelfAccountTransferReadService.class);
        guard = new SelfServiceTransferGuard(jdbcTemplate, readService);
        user = mock(AppSelfServiceUser.class);
        when(user.getId()).thenReturn(USER_ID);

        SelfAccountTemplateData own = mock(SelfAccountTemplateData.class);
        when(own.getAccountId()).thenReturn(OWN_ACCOUNT);
        when(own.getAccountType()).thenReturn(SAVINGS);
        when(readService.retrieveSelfAccountTemplateData(user)).thenReturn(List.of(own));
        when(jdbcTemplate.queryForList(startsWith("select currency_code from m_savings_account"), eq(String.class), eq(OWN_ACCOUNT)))
                .thenReturn(List.of("UGX"));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void someoneElsesAccount_isRefused() {
        assertThatThrownBy(
                () -> guard.check(user, 99L, SAVINGS, new BigDecimal("10"), SelfServiceTransferGuard.Channel.CONFIRMED_TRANSFER, null))
                .isInstanceOf(SavingsAccountNotFoundException.class);
    }

    @Test
    void ownAccountWithoutLimits_isAllowedAndReturnsAccountCurrency() {
        when(jdbcTemplate.queryForList(startsWith("select per_transfer_limit"), eq(USER_ID), eq("UGX"))).thenReturn(List.of());

        assertThat(guard.check(user, OWN_ACCOUNT, SAVINGS, new BigDecimal("10"), SelfServiceTransferGuard.Channel.ACCOUNT_TRANSFER, null))
                .isEqualTo("UGX");
    }

    @Test
    void amountAbovePerTransferLimit_isRefused() {
        givenLimits(new BigDecimal("1000"), null);

        assertThatThrownBy(() -> guard.check(user, OWN_ACCOUNT, SAVINGS, new BigDecimal("1500"),
                SelfServiceTransferGuard.Channel.ACCOUNT_TRANSFER, null)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("per transfer");
    }

    @Test
    void amountAboveWhatIsLeftForToday_isRefused() {
        givenLimits(null, new BigDecimal("2000"));
        givenUsedToday(new BigDecimal("1500"));

        assertThatThrownBy(() -> guard.check(user, OWN_ACCOUNT, SAVINGS, new BigDecimal("600"),
                SelfServiceTransferGuard.Channel.ACCOUNT_TRANSFER, null)).isInstanceOf(GeneralPlatformDomainRuleException.class)
                .hasMessageContaining("500");
    }

    @Test
    void amountWithinDailyLimit_isAllowed() {
        givenLimits(new BigDecimal("1000"), new BigDecimal("2000"));
        givenUsedToday(new BigDecimal("1000"));

        assertThat(guard.check(user, OWN_ACCOUNT, SAVINGS, new BigDecimal("1000"), SelfServiceTransferGuard.Channel.ACCOUNT_TRANSFER, null))
                .isEqualTo("UGX");
    }

    @Test
    void sinpeTransfer_usesGivenCurrencyWithoutInternalOwnershipCheck() {
        when(jdbcTemplate.queryForList(startsWith("select per_transfer_limit"), eq(USER_ID), eq("CRC"))).thenReturn(List.of());

        assertThat(guard.check(user, null, null, new BigDecimal("10"), SelfServiceTransferGuard.Channel.SINPE, null)).isEqualTo("CRC");
        verify(readService, never()).retrieveSelfAccountTemplateData(any());
    }

    @Test
    void record_storesTransferForTheBusinessDate() {
        guard.record(user, SelfServiceTransferGuard.Channel.ACCOUNT_TRANSFER, "UGX", new BigDecimal("10"), "4", 2, "8", 2, 77L);

        verify(jdbcTemplate).update(startsWith("insert into m_selfservice_transfer_log"), eq(USER_ID), any(), eq("ACCOUNT_TRANSFER"),
                eq("UGX"), eq(new BigDecimal("10")), eq(LocalDate.of(2026, 10, 31)), eq("4"), eq(2), eq("8"), eq(2), eq(77L), any());
    }

    private void givenLimits(final BigDecimal perTransfer, final BigDecimal daily) {
        Map<String, Object> row = new HashMap<>();
        row.put("per_transfer_limit", perTransfer);
        row.put("daily_limit", daily);
        when(jdbcTemplate.queryForList(startsWith("select per_transfer_limit"), eq(USER_ID), eq("UGX"))).thenReturn(List.of(row));
    }

    private void givenUsedToday(final BigDecimal used) {
        when(jdbcTemplate.queryForObject(startsWith("select coalesce(sum(amount), 0)"), eq(BigDecimal.class), eq(USER_ID), eq("UGX"),
                eq(LocalDate.of(2026, 10, 31)))).thenReturn(used);
    }
}
