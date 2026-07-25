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
import java.util.Map;
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

    // Loan arrears aging stats
    private BigDecimal totalOverduePrincipal;
    private BigDecimal totalOverdueInterest;
    private BigDecimal totalOverdueFees;
    private BigDecimal totalOverduePenalties;
    private double averageOverdueDays;
    private long maxOverdueDays;
    private Map<String, Long> overdueLoansByRiskLevel;

    // Client stats
    private long newClientsThisMonth;
    private long newClientsThisQuarter;
    private long newClientsLast7Days;
    private long newClientsLast30Days;
    private long newClientsLast90Days;
    private BigDecimal clientGrowthRateLast30Days;
    private BigDecimal clientGrowthRate;
    private Map<String, Long> clientsByGender;
    private Map<String, Long> clientsByAgeGroup;
    private long clientsWithActiveLoan;
    private long clientsWithActiveSavingsAccount;
    private long clientsWithBothLoanAndSavings;

    // Today's stats
    private long loansActivatedTodayCount;
    private BigDecimal loansActivatedTodayAmount;
    private long savingsActivatedTodayCount;
    private BigDecimal savingsActivatedTodayAmount;

    // Product stats
    private Map<String, Long> activeLoansByProduct;
    private Map<String, Long> activeSavingsByProduct;
}
