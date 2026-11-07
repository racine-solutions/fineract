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
package org.apache.fineract.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationProperty;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.external.repository.SmsEventConfigurationRepository;
import org.apache.fineract.infrastructure.event.external.repository.domain.SmsEventConfiguration;
import org.apache.fineract.notification.data.SmsMessageData;
import org.apache.fineract.notification.data.SmsTypeEnum;
import org.apache.fineract.notification.domain.SmsNotificationAccount;
import org.apache.fineract.notification.domain.SmsNotificationAccountRepository;
import org.apache.fineract.notification.domain.SmsNotificationMessageRepository;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.domain.FixedDepositAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccount;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccountTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class SMSNotificationWritePlatformServiceImplTest {

    @Test
    void egosmsStatusDecidesWhetherTheMessageWasAccepted() {
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("{\"Status\":\"OK\",\"Cost\":\"35\"}")).isTrue();
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("{\"Status\":\"Failed\",\"Message\":\"Low balance\"}"))
                .isFalse();
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("{\"status\":\"ok\"}")).isTrue();
    }

    @Test
    void bodiesWithoutAStatusAreTreatedAsAccepted() {
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("")).isTrue();
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("queued")).isTrue();
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("{\"id\":12}")).isTrue();
    }

    @Test
    void numbersAreMaskedInTheLog() {
        assertThat(SMSNotificationWritePlatformServiceImpl.maskNumber("256785154434")).isEqualTo("***434");
        assertThat(SMSNotificationWritePlatformServiceImpl.maskNumber(null)).isEqualTo("***");
    }

    @Test
    void accountNumbersKeepOnlyTheFirstDigitAndTheLastTwo() {
        assertThat(SmsText.maskAccountNumber("000000213")).isEqualTo("0****13");
        assertThat(SmsText.maskAccountNumber("123")).isEqualTo("****");
        assertThat(SmsText.maskAccountNumber(null)).isEqualTo("****");
    }

    @Test
    void smsGreetsByLastNameThenFirstNameThenDisplayName() {
        assertThat(SMSNotificationWritePlatformServiceImpl.recipientName(client("Jane", "Doe", "Jane Doe"))).isEqualTo("Doe");
        assertThat(SMSNotificationWritePlatformServiceImpl.recipientName(client("Jane", " ", "Jane"))).isEqualTo("Jane");
        assertThat(SMSNotificationWritePlatformServiceImpl.recipientName(client(null, null, "Acme Traders Ltd")))
                .isEqualTo("Acme Traders Ltd");
        assertThat(SMSNotificationWritePlatformServiceImpl.recipientName(client(null, null, null))).isEqualTo("Customer");
    }

    private static Client client(String firstname, String lastname, String displayName) {
        Client client = mock(Client.class);
        when(client.getFirstname()).thenReturn(firstname);
        when(client.getLastname()).thenReturn(lastname);
        when(client.getDisplayName()).thenReturn(displayName);
        return client;
    }

    @Test
    void clearanceSmsIsSentForAClosedLoan() {
        SmsEventConfigurationRepository events = mock(SmsEventConfigurationRepository.class);
        SMSNotificationWritePlatformServiceImpl service = serviceWith(events);
        when(events.findSmsEventConfigurationByTypeWithNotFoundDetection("LoanAccountClearanceEvent"))
                .thenReturn(new SmsEventConfiguration("LoanAccountClearanceEvent", true));

        service.processLoanSmsNotification(loan(LoanStatus.CLOSED_OBLIGATIONS_MET), SmsTypeEnum.LOAN_CLEARANCE, repayment());

        ArgumentCaptor<SmsMessageData> sent = ArgumentCaptor.forClass(SmsMessageData.class);
        verify(service).sendSms(sent.capture(), eq(SmsTypeEnum.LOAN_CLEARANCE));
        assertThat(sent.getValue().getNumber()).isEqualTo("256785154434");
        assertThat(sent.getValue().getMessage())
                .isEqualTo("Hi Doe, your loan #0****42 is fully paid and cleared. Date:2026-11-06. Thank you!");
    }

    @Test
    void clearanceSmsForAnOverpaidLoanStatesTheOverpaidAmount() {
        SmsEventConfigurationRepository events = mock(SmsEventConfigurationRepository.class);
        SMSNotificationWritePlatformServiceImpl service = serviceWith(events);
        when(events.findSmsEventConfigurationByTypeWithNotFoundDetection("LoanAccountClearanceEvent"))
                .thenReturn(new SmsEventConfiguration("LoanAccountClearanceEvent", true));

        service.processLoanSmsNotification(loan(LoanStatus.OVERPAID), SmsTypeEnum.LOAN_CLEARANCE, repayment());

        ArgumentCaptor<SmsMessageData> sent = ArgumentCaptor.forClass(SmsMessageData.class);
        verify(service).sendSms(sent.capture(), eq(SmsTypeEnum.LOAN_CLEARANCE));
        assertThat(sent.getValue().getMessage()).endsWith("Thank you! Overpaid: UGX 1500.00");
    }

    @Test
    void clearanceSmsIsNotSentWhenTheEventIsSwitchedOff() {
        SmsEventConfigurationRepository events = mock(SmsEventConfigurationRepository.class);
        SMSNotificationWritePlatformServiceImpl service = serviceWith(events);
        when(events.findSmsEventConfigurationByTypeWithNotFoundDetection("LoanAccountClearanceEvent"))
                .thenReturn(new SmsEventConfiguration("LoanAccountClearanceEvent", false));

        service.processLoanSmsNotification(loan(LoanStatus.CLOSED_OBLIGATIONS_MET), SmsTypeEnum.LOAN_CLEARANCE, repayment());

        verify(service, never()).sendSms(any(), any());
    }

    @Test
    void namesLongerThanTwentyCharactersAreCut() {
        assertThat(SmsText.recipientName("Nakawunde-Ssebunya-Kiwanuka", "Jane")).isEqualTo("Nakawunde-Ssebunya-K");
        assertThat(SMSNotificationWritePlatformServiceImpl.recipientName(client(null, null, "Kampala Women Savings Group Ltd")))
                .isEqualTo("Kampala Women Saving");
    }

    @ParameterizedTest
    @EnumSource(value = SmsTypeEnum.class, names = { "LOAN_SUBMISSION", "LOAN_APPROVAL", "LOAN_DISBURSEMENT", "LOAN_REJECTED",
            "LOAN_REPAYMENT", "LOAN_CLEARANCE" })
    void everyLoanSmsFitsInOneMessageAtWorstCase(SmsTypeEnum type) {
        SmsEventConfigurationRepository events = mock(SmsEventConfigurationRepository.class);
        SMSNotificationWritePlatformServiceImpl service = serviceWith(events);
        when(events.findSmsEventConfigurationByTypeWithNotFoundDetection(any())).thenReturn(new SmsEventConfiguration("any", true));
        Loan loan = loan(LoanStatus.OVERPAID);
        Client client = client("Jane", "Nakawunde-Ssebunya-Kiwanuka", null);
        when(client.getMobileNo()).thenReturn("256785154434");
        when(loan.client()).thenReturn(client);
        when(loan.getApprovedPrincipal()).thenReturn(WORST_AMOUNT);
        when(loan.getProposedPrincipal()).thenReturn(WORST_AMOUNT);
        when(loan.getTotalOverpaidAsMoney().getAmount()).thenReturn(WORST_AMOUNT);
        LoanTransaction transaction = repayment();
        when(transaction.getAmount()).thenReturn(WORST_AMOUNT);
        when(transaction.getId()).thenReturn(WORST_ID);

        try (MockedStatic<DateUtils> dates = mockStatic(DateUtils.class)) {
            dates.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2026, 11, 6));
            service.processLoanSmsNotification(loan, type, transaction);
        }

        assertFitsInOneSms(service, type);
    }

    @ParameterizedTest
    @EnumSource(value = SmsTypeEnum.class, names = { "SAVINGS_DEPOSIT", "SAVINGS_WITHDRAW" })
    void everySavingsSmsFitsInOneMessageAtWorstCase(SmsTypeEnum type) {
        SmsEventConfigurationRepository events = mock(SmsEventConfigurationRepository.class);
        SMSNotificationWritePlatformServiceImpl service = serviceWith(events);
        when(events.findSmsEventConfigurationByTypeWithNotFoundDetection(any())).thenReturn(new SmsEventConfiguration("any", true));
        Client client = client("Jane", "Nakawunde-Ssebunya-Kiwanuka", null);
        when(client.getMobileNo()).thenReturn("256785154434");
        MonetaryCurrency currency = mock(MonetaryCurrency.class);
        when(currency.getCode()).thenReturn("UGX");
        SavingsAccount account = mock(SavingsAccount.class);
        when(account.getClient()).thenReturn(client);
        when(account.getCurrency()).thenReturn(currency);
        when(account.getAccountNumber()).thenReturn("000000042");
        SavingsAccountTransaction transaction = mock(SavingsAccountTransaction.class);
        when(transaction.getAmount()).thenReturn(WORST_AMOUNT);
        when(transaction.getId()).thenReturn(WORST_ID);
        when(transaction.getTransactionDate()).thenReturn(LocalDate.of(2026, 11, 6));

        service.processSavingsSmsNotification(account, type, transaction);

        assertFitsInOneSms(service, type);
    }

    @Test
    void fixedDepositActivationStatesAmountAndMaturity() {
        FixedDepositAccount account = depositAccount(FixedDepositAccount.class, DepositAccountType.FIXED_DEPOSIT, "Doe");
        when(account.getDepositAmount()).thenReturn(new BigDecimal("1000000"));
        when(account.maturityDate()).thenReturn(LocalDate.of(2027, 11, 6));
        when(account.maturityAmount()).thenReturn(new BigDecimal("1120000"));

        assertThat(SMSNotificationWritePlatformServiceImpl.savingsMessage(account, SmsTypeEnum.FIXED_DEPOSIT_ACTIVATION, null))
                .isEqualTo("Hi Doe, your fixed deposit #0****42 of UGX 1000000.00 is active. Matures on 2027-11-06 with UGX 1120000.00.");
    }

    @Test
    void everyAccountTypeUsesTheSameWording() {
        SavingsAccount savings = depositAccount(SavingsAccount.class, DepositAccountType.SAVINGS_DEPOSIT, "Doe");
        SavingsAccount recurring = depositAccount(SavingsAccount.class, DepositAccountType.RECURRING_DEPOSIT, "Doe");

        assertThat(SMSNotificationWritePlatformServiceImpl.savingsMessage(savings, SmsTypeEnum.SAVINGS_SUBMISSION, null))
                .isEqualTo("Hi Doe, your savings account application #0****42 is under review. We will update you soon.");
        assertThat(SMSNotificationWritePlatformServiceImpl.savingsMessage(recurring, SmsTypeEnum.RECURRING_DEPOSIT_APPROVAL, null))
                .isEqualTo("Hi Doe, your recurring deposit #0****42 is approved.");
        assertThat(SMSNotificationWritePlatformServiceImpl.savingsMessage(recurring, SmsTypeEnum.RECURRING_DEPOSIT_DEPOSIT,
                savingsTransaction(new BigDecimal("50000"))))
                .isEqualTo("Hi Doe, deposit of UGX 50000.00 made to recurring deposit #0****42 #845 Date:2026-11-06");
        assertThat(SMSNotificationWritePlatformServiceImpl.savingsMessage(savings, SmsTypeEnum.SAVINGS_WITHDRAW,
                savingsTransaction(new BigDecimal("20000"))))
                .isEqualTo("Hi Doe, withdrawal of UGX 20000.00 made from savings account #0****42 #845 Date:2026-11-06");
    }

    @Test
    void aClosedDepositStatesWhatWasPaidOut() {
        FixedDepositAccount account = depositAccount(FixedDepositAccount.class, DepositAccountType.FIXED_DEPOSIT, "Doe");
        when(account.isClosed()).thenReturn(true);

        assertThat(SMSNotificationWritePlatformServiceImpl.savingsMessage(account, SmsTypeEnum.FIXED_DEPOSIT_WITHDRAW,
                savingsTransaction(new BigDecimal("1120000"))))
                .isEqualTo("Hi Doe, your fixed deposit #0****42 is closed and UGX 1120000.00 paid out. #845 Date:2026-11-06");
        try (MockedStatic<DateUtils> dates = mockStatic(DateUtils.class)) {
            dates.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2026, 11, 6));
            assertThat(SMSNotificationWritePlatformServiceImpl.savingsMessage(account, SmsTypeEnum.FIXED_DEPOSIT_WITHDRAW, null))
                    .isEqualTo("Hi Doe, your fixed deposit #0****42 is closed. Date:2026-11-06");
        }
    }

    @Test
    void sharePurchaseAndRedeemStateSharesAndAmount() {
        ShareAccount account = shareAccount("Doe");
        ShareAccountTransaction transaction = shareTransaction(25L, new BigDecimal("250000"));

        assertThat(SMSNotificationWritePlatformServiceImpl.shareMessage(account, SmsTypeEnum.SHARE_PURCHASE, transaction))
                .isEqualTo("Hi Doe, purchase of 25 shares for UGX 250000.00 on share account #0****42 #845 Date:2026-11-06");
        assertThat(SMSNotificationWritePlatformServiceImpl.shareMessage(account, SmsTypeEnum.SHARE_REDEEM, transaction))
                .isEqualTo("Hi Doe, 25 shares redeemed for UGX 250000.00 from share account #0****42 #845 Date:2026-11-06");
        assertThat(SMSNotificationWritePlatformServiceImpl.shareMessage(account, SmsTypeEnum.SHARE_ACTIVATION, null))
                .isEqualTo("Hi Doe, your share account #0****42 is now active with 100 shares.");
    }

    @ParameterizedTest
    @EnumSource(value = SmsTypeEnum.class, names = { "SAVINGS_SUBMISSION", "SAVINGS_APPROVAL", "SAVINGS_ACTIVATION", "SAVINGS_DEPOSIT",
            "SAVINGS_WITHDRAW", "FIXED_DEPOSIT_SUBMISSION", "FIXED_DEPOSIT_APPROVAL", "FIXED_DEPOSIT_ACTIVATION", "FIXED_DEPOSIT_WITHDRAW",
            "RECURRING_DEPOSIT_SUBMISSION", "RECURRING_DEPOSIT_APPROVAL", "RECURRING_DEPOSIT_ACTIVATION", "RECURRING_DEPOSIT_DEPOSIT",
            "RECURRING_DEPOSIT_WITHDRAW" })
    void everyDepositAccountSmsFitsInOneMessageAtWorstCase(SmsTypeEnum type) {
        FixedDepositAccount account = depositAccount(FixedDepositAccount.class,
                type.name().startsWith("RECURRING") ? DepositAccountType.RECURRING_DEPOSIT : DepositAccountType.FIXED_DEPOSIT,
                "Nakawunde-Ssebunya-Kiwanuka");
        when(account.getDepositAmount()).thenReturn(WORST_AMOUNT);
        when(account.maturityDate()).thenReturn(LocalDate.of(2027, 11, 6));
        when(account.maturityAmount()).thenReturn(WORST_AMOUNT);
        when(account.isClosed()).thenReturn(type.name().endsWith("WITHDRAW"));
        SavingsAccountTransaction transaction = savingsTransaction(WORST_AMOUNT);
        when(transaction.getId()).thenReturn(WORST_ID);

        assertThat(SMSNotificationWritePlatformServiceImpl.savingsMessage(account, type, transaction)).startsWith("Hi ")
                .hasSizeLessThanOrEqualTo(SmsText.MAX_LENGTH);
    }

    @ParameterizedTest
    @EnumSource(value = SmsTypeEnum.class, names = { "SHARE_SUBMISSION", "SHARE_APPROVAL", "SHARE_ACTIVATION", "SHARE_PURCHASE",
            "SHARE_REDEEM" })
    void everyShareSmsFitsInOneMessageAtWorstCase(SmsTypeEnum type) {
        ShareAccount account = shareAccount("Nakawunde-Ssebunya-Kiwanuka");
        when(account.getTotalApprovedShares()).thenReturn(9_999_999_999L);
        ShareAccountTransaction transaction = shareTransaction(9_999_999_999L, WORST_AMOUNT);
        when(transaction.getId()).thenReturn(WORST_ID);

        assertThat(SMSNotificationWritePlatformServiceImpl.shareMessage(account, type, transaction)).startsWith("Hi ")
                .hasSizeLessThanOrEqualTo(SmsText.MAX_LENGTH);
    }

    @Test
    void groupAccountsWithoutAClientGetNoSms() {
        SmsEventConfigurationRepository events = mock(SmsEventConfigurationRepository.class);
        SMSNotificationWritePlatformServiceImpl service = serviceWith(events);
        when(events.findSmsEventConfigurationByTypeWithNotFoundDetection(any())).thenReturn(new SmsEventConfiguration("any", true));

        service.processSavingsSmsNotification(mock(SavingsAccount.class), SmsTypeEnum.SAVINGS_DEPOSIT, savingsTransaction(BigDecimal.TEN));
        service.processShareSmsNotification(mock(ShareAccount.class), SmsTypeEnum.SHARE_APPROVAL, null);

        verify(service, never()).sendSms(any(), any());
    }

    @Test
    void noSmsCreditSkipsTheSmsWithoutFailingTheTransaction() {
        GlobalConfigurationRepositoryWrapper configuration = mock(GlobalConfigurationRepositoryWrapper.class);
        when(configuration.findOneByNameWithNotFoundDetection(any())).thenReturn(mock(GlobalConfigurationProperty.class));
        SmsNotificationAccountRepository accounts = mock(SmsNotificationAccountRepository.class);
        SmsNotificationAccount account = new SmsNotificationAccount().setIsActive(true).setSmsTotalBalance(0);
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        SmsNotificationMessageRepository messages = mock(SmsNotificationMessageRepository.class);
        SMSNotificationWritePlatformServiceImpl service = new SMSNotificationWritePlatformServiceImpl(configuration, accounts, messages,
                mock(SmsEventConfigurationRepository.class));
        ThreadLocalContextUtil.setTenant(FineractPlatformTenant.builder().id(1L).tenantIdentifier("default").name("Showroom").build());
        try {
            service.sendSms(new SmsMessageData("256785154434", "Hi Doe.", null), SmsTypeEnum.SAVINGS_DEPOSIT);
            assertThat(service.sendSmsNow(new SmsMessageData("256785154434", "Hi Doe.", null), SmsTypeEnum.SELF_SERVICE)).isFalse();
        } finally {
            ThreadLocalContextUtil.reset();
        }
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void anSmsProblemNeverReachesTheCaller() {
        SmsEventConfigurationRepository events = mock(SmsEventConfigurationRepository.class);
        when(events.findSmsEventConfigurationByTypeWithNotFoundDetection(any())).thenThrow(new IllegalStateException("no such event"));
        SMSNotificationWritePlatformServiceImpl service = serviceWith(events);

        service.processLoanSmsNotification(loan(LoanStatus.ACTIVE), SmsTypeEnum.LOAN_REPAYMENT, repayment());
        service.processSavingsSmsNotification(mock(SavingsAccount.class), SmsTypeEnum.SAVINGS_DEPOSIT, null);
        service.processShareSmsNotification(mock(ShareAccount.class), SmsTypeEnum.SHARE_REDEEM, null);

        verify(service, never()).sendSms(any(), any());
    }

    private static <T extends SavingsAccount> T depositAccount(Class<T> type, DepositAccountType depositType, String lastname) {
        T account = mock(type);
        Client client = client("Jane", lastname, null);
        when(client.getMobileNo()).thenReturn("256785154434");
        when(account.getClient()).thenReturn(client);
        when(account.getAccountNumber()).thenReturn("000000042");
        MonetaryCurrency currency = ugx();
        when(account.getCurrency()).thenReturn(currency);
        when(account.depositAccountType()).thenReturn(depositType);
        return account;
    }

    private static SavingsAccountTransaction savingsTransaction(BigDecimal amount) {
        SavingsAccountTransaction transaction = mock(SavingsAccountTransaction.class);
        when(transaction.getAmount()).thenReturn(amount);
        when(transaction.getId()).thenReturn(845L);
        when(transaction.getTransactionDate()).thenReturn(LocalDate.of(2026, 11, 6));
        return transaction;
    }

    private static ShareAccount shareAccount(String lastname) {
        ShareAccount account = mock(ShareAccount.class);
        Client client = client("Jane", lastname, null);
        when(account.getClient()).thenReturn(client);
        when(account.getAccountNumber()).thenReturn("000000042");
        MonetaryCurrency currency = ugx();
        when(account.getCurrency()).thenReturn(currency);
        when(account.getTotalApprovedShares()).thenReturn(100L);
        return account;
    }

    private static ShareAccountTransaction shareTransaction(Long shares, BigDecimal amount) {
        ShareAccountTransaction transaction = mock(ShareAccountTransaction.class);
        when(transaction.getTotalShares()).thenReturn(shares);
        when(transaction.amount()).thenReturn(amount);
        when(transaction.getId()).thenReturn(845L);
        when(transaction.getPurchasedDate()).thenReturn(LocalDate.of(2026, 11, 6));
        return transaction;
    }

    private static MonetaryCurrency ugx() {
        MonetaryCurrency currency = mock(MonetaryCurrency.class);
        when(currency.getCode()).thenReturn("UGX");
        return currency;
    }

    private static final BigDecimal WORST_AMOUNT = new BigDecimal("9999999999.99");
    private static final Long WORST_ID = 9_999_999_999L;

    private static void assertFitsInOneSms(SMSNotificationWritePlatformServiceImpl service, SmsTypeEnum type) {
        ArgumentCaptor<SmsMessageData> sent = ArgumentCaptor.forClass(SmsMessageData.class);
        verify(service).sendSms(sent.capture(), eq(type));
        assertThat(sent.getValue().getMessage()).hasSizeLessThanOrEqualTo(SmsText.MAX_LENGTH);
    }

    private static SMSNotificationWritePlatformServiceImpl serviceWith(SmsEventConfigurationRepository events) {
        SMSNotificationWritePlatformServiceImpl service = spy(
                new SMSNotificationWritePlatformServiceImpl(mock(GlobalConfigurationRepositoryWrapper.class),
                        mock(SmsNotificationAccountRepository.class), mock(SmsNotificationMessageRepository.class), events));
        doNothing().when(service).sendSms(any(), any());
        return service;
    }

    private static Loan loan(LoanStatus status) {
        Client client = mock(Client.class);
        when(client.getFirstname()).thenReturn("Jane");
        when(client.getLastname()).thenReturn("Doe");
        when(client.getMobileNo()).thenReturn("256785154434");
        Money overpaid = mock(Money.class);
        when(overpaid.getAmount()).thenReturn(new BigDecimal("1500"));
        Loan loan = mock(Loan.class);
        when(loan.client()).thenReturn(client);
        when(loan.getId()).thenReturn(42L);
        when(loan.getAccountNumber()).thenReturn("000000042");
        when(loan.getCurrencyCode()).thenReturn("UGX");
        when(loan.getStatus()).thenReturn(status);
        when(loan.getTotalOverpaidAsMoney()).thenReturn(overpaid);
        return loan;
    }

    private static LoanTransaction repayment() {
        LoanTransaction transaction = mock(LoanTransaction.class);
        when(transaction.getTransactionDate()).thenReturn(LocalDate.of(2026, 11, 6));
        return transaction;
    }
}
