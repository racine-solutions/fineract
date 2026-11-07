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

import org.apache.fineract.notification.data.SmsMessageData;
import org.apache.fineract.notification.data.SmsSendOutcome;
import org.apache.fineract.notification.data.SmsTypeEnum;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccount;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccountTransaction;

public interface SmsNotificationWritePlatformService {

    void sendSms(SmsMessageData messageData, SmsTypeEnum smsType);

    /**
     * Sends the message straight away and reports the outcome. The text is treated as secret: it is kept out of the log
     * and the message history.
     *
     * @return true only when the gateway accepted the message
     */
    boolean sendSmsNow(SmsMessageData messageData, SmsTypeEnum smsType);

    /**
     * Sends one campaign SMS straight away and says what happened, so the campaign can record a reason for each
     * recipient. The text is kept in the message history. A blank senderId uses the usual sender ID.
     */
    SmsSendOutcome sendCampaignSms(SmsMessageData messageData, String senderId);

    void processLoanSmsNotification(Loan loan, SmsTypeEnum smsType, LoanTransaction transaction);

    /**
     * Savings, fixed deposit and recurring deposit accounts. The transaction is only needed for deposits and
     * withdrawals; for a closed fixed or recurring deposit it is the payout, or null when nothing was paid out.
     */
    void processSavingsSmsNotification(SavingsAccount savingsAccount, SmsTypeEnum smsType, SavingsAccountTransaction transaction);

    /** The transaction is only needed for share purchases and redemptions. */
    void processShareSmsNotification(ShareAccount shareAccount, SmsTypeEnum smsType, ShareAccountTransaction transaction);
}
