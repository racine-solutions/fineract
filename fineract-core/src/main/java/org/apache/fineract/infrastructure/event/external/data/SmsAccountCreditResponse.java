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
package org.apache.fineract.infrastructure.event.external.data;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmsAccountCreditResponse {

    private Long transactionId;

    /** SMS units credited (derived: floor(amount / smsCost)). */
    private Integer smsCredit;

    /** Balance on the account before this credit was applied. */
    private Integer previousBalance;

    /** New balance after applying the credit (previousBalance + smsCredit). */
    private Integer newBalance;

    /** Running total of all payment amounts ever loaded onto this account. */
    private BigDecimal totalPaymentAmount;
}
