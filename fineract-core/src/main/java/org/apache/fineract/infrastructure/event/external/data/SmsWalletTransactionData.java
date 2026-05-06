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
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmsWalletTransactionData {

    private Long id;

    /** Payment amount loaded onto the account. */
    private BigDecimal amount;

    /** Cost per single SMS unit at the time of the top-up. */
    private BigDecimal smsCost;

    /** SMS units credited — derived as floor(amount / smsCost). */
    private Integer smsCredit;

    /** Account balance snapshot taken immediately before this credit. */
    private Integer previousBalance;

    /** Optional note / payment reference. */
    private String note;

    /** UTC timestamp when the transaction was recorded. */
    private OffsetDateTime createdOnUtc;
}
