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
package org.apache.fineract.notification.data;

import lombok.Getter;

@Getter
public enum SmsTypeEnum {

    LOAN_SUBMISSION(1, "SmsTypeEnum.loanSubmission", "Loan Submission"), //
    LOAN_APPROVAL(2, "SmsTypeEnum.loanApproval", "Loan Approval"), //
    LOAN_DISBURSEMENT(3, "SmsTypeEnum.loanDisbursement", "Loan Disbursement"), //
    LOAN_REPAYMENT(4, "SmsTypeEnum.loanRepayment", "Loan Repayment"), //
    LOAN_REJECTED(5, "SmsTypeEnum.loanRejected", "Loan Application Rejected"), //
    SAVINGS_DEPOSIT(6, "SmsTypeEnum.savingsDeposit", "Savings Account Deposit"), //
    SAVINGS_WITHDRAW(7, "SmsTypeEnum.savingsWithdraw", "Savings Account Withdraw"), //
    SELF_SERVICE(8, "SmsTypeEnum.selfService", "Mobile App (Self Service)"), //
    LOAN_CLEARANCE(9, "SmsTypeEnum.loanClearance", "Loan Clearance"), //
    SAVINGS_SUBMISSION(10, "SmsTypeEnum.savingsSubmission", "Savings Account Submission"), //
    SAVINGS_APPROVAL(11, "SmsTypeEnum.savingsApproval", "Savings Account Approval"), //
    SAVINGS_ACTIVATION(12, "SmsTypeEnum.savingsActivation", "Savings Account Activation"), //
    FIXED_DEPOSIT_SUBMISSION(13, "SmsTypeEnum.fixedDepositSubmission", "Fixed Deposit Submission"), //
    FIXED_DEPOSIT_APPROVAL(14, "SmsTypeEnum.fixedDepositApproval", "Fixed Deposit Approval"), //
    FIXED_DEPOSIT_ACTIVATION(15, "SmsTypeEnum.fixedDepositActivation", "Fixed Deposit Activation"), //
    FIXED_DEPOSIT_WITHDRAW(16, "SmsTypeEnum.fixedDepositWithdraw", "Fixed Deposit Withdraw"), //
    RECURRING_DEPOSIT_SUBMISSION(17, "SmsTypeEnum.recurringDepositSubmission", "Recurring Deposit Submission"), //
    RECURRING_DEPOSIT_APPROVAL(18, "SmsTypeEnum.recurringDepositApproval", "Recurring Deposit Approval"), //
    RECURRING_DEPOSIT_ACTIVATION(19, "SmsTypeEnum.recurringDepositActivation", "Recurring Deposit Activation"), //
    RECURRING_DEPOSIT_DEPOSIT(20, "SmsTypeEnum.recurringDepositDeposit", "Recurring Deposit Deposit"), //
    RECURRING_DEPOSIT_WITHDRAW(21, "SmsTypeEnum.recurringDepositWithdraw", "Recurring Deposit Withdraw"), //
    SHARE_SUBMISSION(22, "SmsTypeEnum.shareSubmission", "Share Account Submission"), //
    SHARE_APPROVAL(23, "SmsTypeEnum.shareApproval", "Share Account Approval"), //
    SHARE_ACTIVATION(24, "SmsTypeEnum.shareActivation", "Share Account Activation"), //
    SHARE_PURCHASE(25, "SmsTypeEnum.sharePurchase", "Share Purchase"), //
    SHARE_REDEEM(26, "SmsTypeEnum.shareRedeem", "Share Redeem"), //
    CAMPAIGN(27, "SmsTypeEnum.campaign", "Communication Campaign"), //
    ;

    private final Integer value;
    private final String code;
    private final String description;

    SmsTypeEnum(final Integer value, final String code, final String description) {
        this.value = value;
        this.code = code;
        this.description = description;
    }

}
