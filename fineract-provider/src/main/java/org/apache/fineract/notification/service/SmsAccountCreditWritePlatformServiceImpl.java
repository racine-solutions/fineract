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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.event.sms.service.SmsAccountCreditWritePlatformService;
import org.apache.fineract.notification.domain.SmsNotificationAccount;
import org.apache.fineract.notification.domain.SmsNotificationAccountRepository;
import org.apache.fineract.notification.domain.SmsNotificationAccountTransaction;
import org.apache.fineract.notification.domain.SmsNotificationAccountTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsAccountCreditWritePlatformServiceImpl implements SmsAccountCreditWritePlatformService {

    private static final Long ACCOUNT_ID = 1L;

    private final SmsNotificationAccountRepository accountRepository;
    private final SmsNotificationAccountTransactionRepository transactionRepository;

    @Override
    @Transactional
    public CommandProcessingResult creditAccount(final JsonCommand command) {

        final BigDecimal amount = command.bigDecimalValueOfParameterNamed("amount");
        final BigDecimal smsCost = command.bigDecimalValueOfParameterNamed("smsCost");
        final String note = command.stringValueOfParameterNamedAllowingNull("note");

        validateInputs(amount, smsCost);

        // ── 1. Load the account ──────────────────────────────────────────────
        final SmsNotificationAccount account = accountRepository.findById(ACCOUNT_ID)
                .orElseThrow(() -> new PlatformApiDataValidationException(
                        "error.msg.sms.account.not.found",
                        "SMS notification account does not exist. Please create one first.",
                        "accountId"));

        // ── 2. Derive SMS credit units ───────────────────────────────────────
        final int smsCredit = amount.divide(smsCost, 0, RoundingMode.FLOOR).intValue();

        if (smsCredit <= 0) {
            throw new PlatformApiDataValidationException(
                    "error.msg.sms.credit.zero",
                    "The supplied amount and smsCost result in zero SMS credits. Please review the values.",
                    "smsCredit");
        }

        // ── 3. Capture previous balance for audit ────────────────────────────
        final int previousBalance = account.getSmsTotalBalance() != null ? account.getSmsTotalBalance() : 0;

        // ── 4. Persist the transaction record ───────────────────────────────
        final SmsNotificationAccountTransaction transaction = new SmsNotificationAccountTransaction()
                .setAmount(amount)
                .setSmsCost(smsCost)
                .setSmsCredit(smsCredit)
                .setPreviousBalance(previousBalance)
                .setNote(note);

        final SmsNotificationAccountTransaction saved = transactionRepository.saveAndFlush(transaction);

        // ── 5. Update the account ────────────────────────────────────────────
        final BigDecimal currentTotal = account.getTotalPaymentAmount() != null ? account.getTotalPaymentAmount() : BigDecimal.ZERO;
        final int currentCredit = account.getSmsTotalCredit() != null ? account.getSmsTotalCredit() : 0;
        final int newBalance = previousBalance + smsCredit;

        account.setTotalPaymentAmount(currentTotal.add(amount));
        account.setSmsTotalCredit(currentCredit + smsCredit);
        account.setSmsTotalBalance(newBalance);
        accountRepository.saveAndFlush(account);

        log.info("SMS account credited: +{} units. New balance: {}. Transaction id: {}", smsCredit, newBalance, saved.getId());

        final Map<String, Object> changes = new HashMap<>();
        changes.put("smsCredit", smsCredit);
        changes.put("previousBalance", previousBalance);
        changes.put("newBalance", newBalance);
        changes.put("totalPaymentAmount", account.getTotalPaymentAmount());

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(saved.getId())
                .with(changes)
                .build();
    }

    private void validateInputs(final BigDecimal amount, final BigDecimal smsCost) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlatformApiDataValidationException(
                    "error.msg.sms.credit.amount.invalid",
                    "Amount must be a positive value.",
                    "amount");
        }
        if (smsCost == null || smsCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PlatformApiDataValidationException(
                    "error.msg.sms.credit.smsCost.invalid",
                    "smsCost must be a positive value.",
                    "smsCost");
        }
    }
}
