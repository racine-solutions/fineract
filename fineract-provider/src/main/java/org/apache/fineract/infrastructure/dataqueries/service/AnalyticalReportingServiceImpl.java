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
package org.apache.fineract.infrastructure.dataqueries.service;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.dataqueries.data.FineractAnalyticalDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticalReportingServiceImpl implements AnalyticalReportingService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public FineractAnalyticalDetails getAnalyticalDetails() {
        final FineractAnalyticalDetails details = new FineractAnalyticalDetails();
        final String sql = "SELECT " +
                "loan_stats.activeLoansCount, loan_stats.activeLoansAmount, " +
                "loan_stats.pendingApprovalLoansCount, loan_stats.pendingApprovalLoansAmount, " +
                "loan_stats.closedLoansCount, loan_stats.closedLoansAmount, " +
                "loan_stats.rejectedLoansCount, loan_stats.rejectedLoansAmount, " +
                "loan_stats.totalInterestExpected, loan_stats.totalInterestPaid, " +
                "loan_stats.totalPenaltiesGenerated, loan_stats.totalPenaltiesPaid, loan_stats.totalPenaltiesWaived, " +
                "client_stats.totalClients, client_stats.activeClients, " +
                "savings_stats.activeSavingsAccountsCount, savings_stats.activeSavingsAccountsAmount, " +
                "savings_stats.pendingApprovalSavingsAccountsCount, savings_stats.pendingApprovalSavingsAccountsAmount, " +
                "savings_stats.closedSavingsAccountsCount, savings_stats.closedSavingsAccountsAmount, " +
                "savings_stats.rejectedSavingsAccountsCount, savings_stats.rejectedSavingsAccountsAmount " +
                "FROM (" +
                "    SELECT " +
                "        COUNT(CASE WHEN l.loan_status_id = 300 THEN 1 END) AS activeLoansCount, " +
                "        SUM(CASE WHEN l.loan_status_id = 300 THEN l.principal_amount END) AS activeLoansAmount, " +
                "        COUNT(CASE WHEN l.loan_status_id = 100 THEN 1 END) AS pendingApprovalLoansCount, " +
                "        SUM(CASE WHEN l.loan_status_id = 100 THEN l.principal_amount END) AS pendingApprovalLoansAmount, " +
                "        COUNT(CASE WHEN l.loan_status_id IN (600, 601, 602) THEN 1 END) AS closedLoansCount, " +
                "        SUM(CASE WHEN l.loan_status_id IN (600, 601, 602) THEN l.principal_amount END) AS closedLoansAmount, " +
                "        COUNT(CASE WHEN l.loan_status_id = 500 THEN 1 END) AS rejectedLoansCount, " +
                "        SUM(CASE WHEN l.loan_status_id = 500 THEN l.principal_amount END) AS rejectedLoansAmount, " +

                "        SUM(l.interest_charged_derived) AS totalInterestExpected, " +
                "        SUM(l.interest_repaid_derived) AS totalInterestPaid, " +

                "        SUM(l.penalty_charges_charged_derived) AS totalPenaltiesGenerated, " +
                "        SUM(l.penalty_charges_repaid_derived) AS totalPenaltiesPaid, " +

                "        SUM(l.penalty_charges_waived_derived) AS totalPenaltiesWaived " +
                "    FROM m_loan l) AS loan_stats " +
                "CROSS JOIN " +
                "    (SELECT " +
                "        COUNT(*) AS totalClients, " +
                "        COUNT(CASE WHEN c.status_enum = 300 THEN 1 END) AS activeClients " +
                "    FROM m_client c) AS client_stats " +
                "CROSS JOIN " +
                "    (SELECT " +
                "        COUNT(CASE WHEN s.status_enum = 300 THEN 1 END) AS activeSavingsAccountsCount, " +
                "        SUM(CASE WHEN s.status_enum = 300 THEN s.account_balance_derived END) AS activeSavingsAccountsAmount, " +
                "        COUNT(CASE WHEN s.status_enum = 100 THEN 1 END) AS pendingApprovalSavingsAccountsCount, " +
                "        SUM(CASE WHEN s.status_enum = 100 THEN s.account_balance_derived END) AS pendingApprovalSavingsAccountsAmount, " +
                "        COUNT(CASE WHEN s.status_enum = 600 THEN 1 END) AS closedSavingsAccountsCount, " +
                "        SUM(CASE WHEN s.status_enum = 600 THEN s.account_balance_derived END) AS closedSavingsAccountsAmount, " +
                "        COUNT(CASE WHEN s.status_enum = 500 THEN 1 END) AS rejectedSavingsAccountsCount, " +
                "        SUM(CASE WHEN s.status_enum = 500 THEN s.account_balance_derived END) AS rejectedSavingsAccountsAmount " +
                "    FROM m_savings_account s) AS savings_stats";

        jdbcTemplate.query(sql, rs -> {
            details.setActiveLoansCount(rs.getLong("activeLoansCount"));
            details.setActiveLoansAmount(rs.getBigDecimal("activeLoansAmount"));
            details.setPendingApprovalLoansCount(rs.getLong("pendingApprovalLoansCount"));
            details.setPendingApprovalLoansAmount(rs.getBigDecimal("pendingApprovalLoansAmount"));
            details.setClosedLoansCount(rs.getLong("closedLoansCount"));
            details.setClosedLoansAmount(rs.getBigDecimal("closedLoansAmount"));
            details.setRejectedLoansCount(rs.getLong("rejectedLoansCount"));
            details.setRejectedLoansAmount(rs.getBigDecimal("rejectedLoansAmount"));
            details.setTotalInterestExpected(rs.getBigDecimal("totalInterestExpected"));
            details.setTotalInterestPaid(rs.getBigDecimal("totalInterestPaid"));
            details.setTotalPenaltiesGenerated(rs.getBigDecimal("totalPenaltiesGenerated"));
            details.setTotalPenaltiesPaid(rs.getBigDecimal("totalPenaltiesPaid"));
            details.setTotalPenaltiesWaived(rs.getBigDecimal("totalPenaltiesWaived"));

            details.setTotalClients(rs.getLong("totalClients"));
            details.setActiveClients(rs.getLong("activeClients"));

            details.setActiveSavingsAccountsCount(rs.getLong("activeSavingsAccountsCount"));
            details.setActiveSavingsAccountsAmount(rs.getBigDecimal("activeSavingsAccountsAmount"));
            details.setPendingApprovalSavingsAccountsCount(rs.getLong("pendingApprovalSavingsAccountsCount"));
            details.setPendingApprovalSavingsAccountsAmount(rs.getBigDecimal("pendingApprovalSavingsAccountsAmount"));
            details.setClosedSavingsAccountsCount(rs.getLong("closedSavingsAccountsCount"));
            details.setClosedSavingsAccountsAmount(rs.getBigDecimal("closedSavingsAccountsAmount"));
            details.setRejectedSavingsAccountsCount(rs.getLong("rejectedSavingsAccountsCount"));
            details.setRejectedSavingsAccountsAmount(rs.getBigDecimal("rejectedSavingsAccountsAmount"));
        });

        return details;
    }
}
