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
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.event.external.repository.SmsEventConfigurationRepository;
import org.apache.fineract.infrastructure.event.external.repository.domain.SmsEventConfiguration;
import org.apache.fineract.notification.data.SmsMessageData;
import org.apache.fineract.notification.data.SmsTypeEnum;
import org.apache.fineract.notification.domain.SmsNotificationAccountRepository;
import org.apache.fineract.notification.domain.SmsNotificationMessageRepository;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
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
