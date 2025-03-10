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
    LOAN_REJECTED(4, "SmsTypeEnum.loanRejected", "Loan Application Rejected"), //
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