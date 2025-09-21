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

import java.util.HashMap;
import java.util.Map;
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
                "client_stats.totalClients, client_stats.activeClients, client_stats.pendingClients, " +
                "savings_stats.activeSavingsAccountsCount, savings_stats.activeSavingsAccountsAmount, " +
                "savings_stats.pendingApprovalSavingsAccountsCount, savings_stats.pendingApprovalSavingsAccountsAmount, " +
                "savings_stats.closedSavingsAccountsCount, savings_stats.closedSavingsAccountsAmount, " +
                "savings_stats.rejectedSavingsAccountsCount, savings_stats.rejectedSavingsAccountsAmount, " +
                "arrears_aging_stats.totalOverduePrincipal, arrears_aging_stats.totalOverdueInterest, " +
                "arrears_aging_stats.totalOverdueFees, arrears_aging_stats.totalOverduePenalties, " +
                "arrears_aging_stats.averageOverdueDays, arrears_aging_stats.maxOverdueDays, " +
                "arrears_aging_stats.highRiskCount, arrears_aging_stats.mediumRiskCount, arrears_aging_stats.lowRiskCount, " +
                "todays_stats.loansActivatedTodayCount, todays_stats.loansActivatedTodayAmount, " +
                "todays_stats.savingsActivatedTodayCount, todays_stats.savingsActivatedTodayAmount, " +
                "client_growth_stats.newClientsThisMonth, client_growth_stats.newClientsThisQuarter, " +
                "client_growth_stats.newClientsLast7Days, client_growth_stats.newClientsLast30Days, client_growth_stats.newClientsLast90Days, " +
                "client_growth_stats.clientGrowthRateLast30Days, " +
                "client_activity_stats.clientsWithActiveLoan, client_activity_stats.clientsWithActiveSavingsAccount, client_activity_stats.clientsWithBothLoanAndSavings " +
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
                "        COUNT(CASE WHEN c.status_enum = 300 THEN 1 END) AS activeClients, " +
                "        COUNT(CASE WHEN c.status_enum = 100 THEN 1 END) AS pendingClients " +
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
                "    FROM m_savings_account s) AS savings_stats " +
                "CROSS JOIN " +
                "    (SELECT " +
                "        SUM(laa.principal_overdue_derived) AS totalOverduePrincipal, " +
                "        SUM(laa.interest_overdue_derived) AS totalOverdueInterest, " +
                "        SUM(laa.fee_charges_overdue_derived) AS totalOverdueFees, " +
                "        SUM(laa.penalty_charges_overdue_derived) AS totalOverduePenalties, " +
                "        AVG(DATEDIFF(CURDATE(), laa.overdue_since_date_derived)) AS averageOverdueDays, " +
                "        MAX(DATEDIFF(CURDATE(), laa.overdue_since_date_derived)) AS maxOverdueDays, " +
                "        COUNT(CASE WHEN DATEDIFF(CURDATE(), laa.overdue_since_date_derived) > 90 THEN 1 END) AS highRiskCount, " +
                "        COUNT(CASE WHEN DATEDIFF(CURDATE(), laa.overdue_since_date_derived) BETWEEN 31 AND 90 THEN 1 END) AS mediumRiskCount, " +
                "        COUNT(CASE WHEN DATEDIFF(CURDATE(), laa.overdue_since_date_derived) BETWEEN 1 AND 30 THEN 1 END) AS lowRiskCount " +
                "    FROM m_loan_arrears_aging laa) AS arrears_aging_stats " +
                "CROSS JOIN " +
                "    (SELECT " +
                "        COUNT(CASE WHEN l.disbursedon_date = CURDATE() THEN 1 END) as loansActivatedTodayCount, " +
                "        SUM(CASE WHEN l.disbursedon_date = CURDATE() THEN l.principal_amount END) as loansActivatedTodayAmount, " +
                "        (SELECT COUNT(CASE WHEN s.activatedon_date = CURDATE() THEN 1 END) FROM m_savings_account s) as savingsActivatedTodayCount, " +
                "        (SELECT SUM(CASE WHEN s.activatedon_date = CURDATE() THEN s.account_balance_derived END) FROM m_savings_account s) as savingsActivatedTodayAmount " +
                "    FROM m_loan l) as todays_stats " +
                "CROSS JOIN " +
                "    (SELECT " +
                "        COUNT(CASE WHEN c.submittedon_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01') THEN 1 END) as newClientsThisMonth, " +
                "        COUNT(CASE WHEN c.submittedon_date >= DATE_FORMAT(CURDATE(), '%Y-%q-01') THEN 1 END) as newClientsThisQuarter, " +
                "        COUNT(CASE WHEN c.submittedon_date >= CURDATE() - INTERVAL 7 DAY THEN 1 END) as newClientsLast7Days, " +
                "        COUNT(CASE WHEN c.submittedon_date >= CURDATE() - INTERVAL 30 DAY THEN 1 END) as newClientsLast30Days, " +
                "        COUNT(CASE WHEN c.submittedon_date >= CURDATE() - INTERVAL 90 DAY THEN 1 END) as newClientsLast90Days, " +
                "        (COUNT(CASE WHEN c.submittedon_date >= CURDATE() - INTERVAL 30 DAY THEN 1 END) - COUNT(CASE WHEN c.submittedon_date >= CURDATE() - INTERVAL 60 DAY AND c.submittedon_date < CURDATE() - INTERVAL 30 DAY THEN 1 END)) / NULLIF(COUNT(CASE WHEN c.submittedon_date >= CURDATE() - INTERVAL 60 DAY AND c.submittedon_date < CURDATE() - INTERVAL 30 DAY THEN 1 END), 0) * 100 as clientGrowthRateLast30Days " +
                "    FROM m_client c) as client_growth_stats " +
                "CROSS JOIN " +
                "    (SELECT " +
                "        COUNT(DISTINCT l.client_id) as clientsWithActiveLoan, " +
                "        (SELECT COUNT(DISTINCT s.client_id) FROM m_savings_account s WHERE s.status_enum = 300) as clientsWithActiveSavingsAccount, " +
                "        COUNT(DISTINCT CASE WHEN l.client_id IS NOT NULL AND s.client_id IS NOT NULL THEN l.client_id END) as clientsWithBothLoanAndSavings " +
                "    FROM m_loan l " +
                "    INNER JOIN m_savings_account s ON l.client_id = s.client_id " +
                "    WHERE l.loan_status_id = 300 AND s.status_enum = 300) as client_activity_stats";

        jdbcTemplate.query(sql, (rs) -> {
            if (rs.next()) {
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
                details.setPendingClients(rs.getLong("pendingClients"));

                details.setActiveSavingsAccountsCount(rs.getLong("activeSavingsAccountsCount"));
                details.setActiveSavingsAccountsAmount(rs.getBigDecimal("activeSavingsAccountsAmount"));
                details.setPendingApprovalSavingsAccountsCount(rs.getLong("pendingApprovalSavingsAccountsCount"));
                details.setPendingApprovalSavingsAccountsAmount(rs.getBigDecimal("pendingApprovalSavingsAccountsAmount"));
                details.setClosedSavingsAccountsCount(rs.getLong("closedSavingsAccountsCount"));
                details.setClosedSavingsAccountsAmount(rs.getBigDecimal("closedSavingsAccountsAmount"));
                details.setRejectedSavingsAccountsCount(rs.getLong("rejectedSavingsAccountsCount"));
                details.setRejectedSavingsAccountsAmount(rs.getBigDecimal("rejectedSavingsAccountsAmount"));

                details.setTotalOverduePrincipal(rs.getBigDecimal("totalOverduePrincipal"));
                details.setTotalOverdueInterest(rs.getBigDecimal("totalOverdueInterest"));
                details.setTotalOverdueFees(rs.getBigDecimal("totalOverdueFees"));
                details.setTotalOverduePenalties(rs.getBigDecimal("totalOverduePenalties"));
                details.setAverageOverdueDays(rs.getDouble("averageOverdueDays"));
                details.setMaxOverdueDays(rs.getLong("maxOverdueDays"));

                final Map<String, Long> overdueLoansByRiskLevel = new java.util.HashMap<>();
                overdueLoansByRiskLevel.put("High", rs.getLong("highRiskCount"));
                overdueLoansByRiskLevel.put("Medium", rs.getLong("mediumRiskCount"));
                overdueLoansByRiskLevel.put("Low", rs.getLong("lowRiskCount"));
                details.setOverdueLoansByRiskLevel(overdueLoansByRiskLevel);

                details.setNewClientsThisMonth(rs.getLong("newClientsThisMonth"));
                details.setNewClientsThisQuarter(rs.getLong("newClientsThisQuarter"));
                details.setNewClientsLast7Days(rs.getLong("newClientsLast7Days"));
                details.setNewClientsLast30Days(rs.getLong("newClientsLast30Days"));
                details.setNewClientsLast90Days(rs.getLong("newClientsLast90Days"));
                details.setClientGrowthRateLast30Days(rs.getBigDecimal("clientGrowthRateLast30Days"));

                details.setClientsWithActiveLoan(rs.getLong("clientsWithActiveLoan"));
                details.setClientsWithActiveSavingsAccount(rs.getLong("clientsWithActiveSavingsAccount"));
                details.setClientsWithBothLoanAndSavings(rs.getLong("clientsWithBothLoanAndSavings"));

                details.setLoansActivatedTodayCount(rs.getLong("loansActivatedTodayCount"));
                details.setLoansActivatedTodayAmount(rs.getBigDecimal("loansActivatedTodayAmount"));
                details.setSavingsActivatedTodayCount(rs.getLong("savingsActivatedTodayCount"));
                details.setSavingsActivatedTodayAmount(rs.getBigDecimal("savingsActivatedTodayAmount"));
            }
            return details;
        });

        final String genderSql = "SELECT cv.code_value, COUNT(c.id) as count FROM m_client c LEFT JOIN m_code_value cv ON c.gender_cv_id = cv.id GROUP BY c.gender_cv_id";
        final Map<String, Long> clientsByGender = new HashMap<>();
        jdbcTemplate.query(genderSql, (rs) -> {
            while (rs.next()) {
                String gender = rs.getString("code_value");
                if (gender == null) {
                    gender = "N/A";
                }
                clientsByGender.put(gender, rs.getLong("count"));
            }
            return clientsByGender;
        });
        details.setClientsByGender(clientsByGender);

        final String ageSql = "SELECT " +
                "SUM(CASE WHEN DATEDIFF(CURDATE(), c.date_of_birth)/365 < 18 THEN 1 ELSE 0 END) as '<18', " +
                "SUM(CASE WHEN DATEDIFF(CURDATE(), c.date_of_birth)/365 BETWEEN 18 AND 25 THEN 1 ELSE 0 END) as '18-25', " +
                "SUM(CASE WHEN DATEDIFF(CURDATE(), c.date_of_birth)/365 BETWEEN 26 AND 35 THEN 1 ELSE 0 END) as '26-35', " +
                "SUM(CASE WHEN DATEDIFF(CURDATE(), c.date_of_birth)/365 BETWEEN 36 AND 45 THEN 1 ELSE 0 END) as '36-45', " +
                "SUM(CASE WHEN DATEDIFF(CURDATE(), c.date_of_birth)/365 BETWEEN 46 AND 55 THEN 1 ELSE 0 END) as '46-55', " +
                "SUM(CASE WHEN DATEDIFF(CURDATE(), c.date_of_birth)/365 > 55 THEN 1 ELSE 0 END) as '>55', " +
                "SUM(CASE WHEN c.date_of_birth IS NULL THEN 1 ELSE 0 END) as 'N/A' " +
                "FROM m_client c";
        final Map<String, Long> clientsByAgeGroup = new HashMap<>();
        jdbcTemplate.query(ageSql, (rs) -> {
            if (rs.next()) {
                clientsByAgeGroup.put("<18", rs.getLong("<18"));
                clientsByAgeGroup.put("18-25", rs.getLong("18-25"));
                clientsByAgeGroup.put("26-35", rs.getLong("26-35"));
                clientsByAgeGroup.put("36-45", rs.getLong("36-45"));
                clientsByAgeGroup.put("46-55", rs.getLong("46-55"));
                clientsByAgeGroup.put(">55", rs.getLong(">55"));
                clientsByAgeGroup.put("N/A", rs.getLong("N/A"));
            }
            return clientsByAgeGroup;
        });
        details.setClientsByAgeGroup(clientsByAgeGroup);

        final String loanProductSql = "SELECT p.name, COUNT(l.id) as count FROM m_loan l JOIN m_product_loan p ON l.product_id = p.id WHERE l.loan_status_id = 300 GROUP BY p.name";
        final Map<String, Long> activeLoansByProduct = new HashMap<>();
        jdbcTemplate.query(loanProductSql, (rs) -> {
            while (rs.next()) {
                activeLoansByProduct.put(rs.getString("name"), rs.getLong("count"));
            }
            return activeLoansByProduct;
        });
        details.setActiveLoansByProduct(activeLoansByProduct);

        final String savingsProductSql = "SELECT p.name, COUNT(s.id) as count FROM m_savings_account s JOIN m_savings_product p ON s.product_id = p.id WHERE s.status_enum = 300 GROUP BY p.name";
        final Map<String, Long> activeSavingsByProduct = new HashMap<>();
        jdbcTemplate.query(savingsProductSql, (rs) -> {
            while (rs.next()) {
                activeSavingsByProduct.put(rs.getString("name"), rs.getLong("count"));
            }
            return activeSavingsByProduct;
        });
        details.setActiveSavingsByProduct(activeSavingsByProduct);

        return details;
    }
}
