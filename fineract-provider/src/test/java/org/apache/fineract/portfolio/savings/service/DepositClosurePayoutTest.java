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
package org.apache.fineract.portfolio.savings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.junit.jupiter.api.Test;

class DepositClosurePayoutTest {

    private static final LocalDate CLOSED_ON = LocalDate.of(2026, 11, 6);

    @Test
    void thePayoutIsTheWithdrawalMadeOnClosure() {
        SavingsAccountTransaction earlierWithdrawal = withdrawal(1L, CLOSED_ON.minusMonths(2), false);
        SavingsAccountTransaction reversedPayout = withdrawal(3L, CLOSED_ON, true);
        SavingsAccountTransaction payout = withdrawal(2L, CLOSED_ON, false);

        assertThat(DepositAccountWritePlatformServiceJpaRepositoryImpl
                .closurePayout(account(List.of(earlierWithdrawal, reversedPayout, payout)))).isSameAs(payout);
    }

    @Test
    void nothingIsPaidOutWhenTheMoneyWasReinvested() {
        SavingsAccountTransaction earlierWithdrawal = withdrawal(1L, CLOSED_ON.minusMonths(2), false);

        assertThat(DepositAccountWritePlatformServiceJpaRepositoryImpl.closurePayout(account(List.of(earlierWithdrawal)))).isNull();
    }

    private static SavingsAccount account(List<SavingsAccountTransaction> transactions) {
        SavingsAccount account = mock(SavingsAccount.class);
        when(account.getClosedOnDate()).thenReturn(CLOSED_ON);
        when(account.getTransactions()).thenReturn(transactions);
        return account;
    }

    private static SavingsAccountTransaction withdrawal(Long id, LocalDate date, boolean reversed) {
        SavingsAccountTransaction transaction = mock(SavingsAccountTransaction.class);
        when(transaction.getId()).thenReturn(id);
        when(transaction.getTransactionDate()).thenReturn(date);
        when(transaction.isWithdrawal()).thenReturn(true);
        when(transaction.isNotReversed()).thenReturn(!reversed);
        return transaction;
    }
}
