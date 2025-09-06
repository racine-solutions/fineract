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
package org.apache.fineract.infrastructure.dataqueries.data;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class FineractAnalyticalDetails {

    // Loan stats
    private long activeLoansCount;
    private BigDecimal activeLoansAmount;
    private long pendingApprovalLoansCount;
    private BigDecimal pendingApprovalLoansAmount;
    private long closedLoansCount;
    private BigDecimal closedLoansAmount;
    private long rejectedLoansCount;
    private BigDecimal rejectedLoansAmount;
    private long loansInArrearsCount;
    private BigDecimal loansInArrearsAmount;
    private BigDecimal totalInterestExpected;
    private BigDecimal totalInterestPaid;
    private BigDecimal totalPenaltiesGenerated;
    private BigDecimal totalPenaltiesPaid;
    private BigDecimal totalPenaltiesWaived;

    // Client stats
    private long totalClients;
    private long activeClients;
    private long pendingClients;

    // Savings stats
    private long activeSavingsAccountsCount;
    private BigDecimal activeSavingsAccountsAmount;
    private long pendingApprovalSavingsAccountsCount;
    private BigDecimal pendingApprovalSavingsAccountsAmount;
    private long closedSavingsAccountsCount;
    private BigDecimal closedSavingsAccountsAmount;
    private long rejectedSavingsAccountsCount;
    private BigDecimal rejectedSavingsAccountsAmount;
}
