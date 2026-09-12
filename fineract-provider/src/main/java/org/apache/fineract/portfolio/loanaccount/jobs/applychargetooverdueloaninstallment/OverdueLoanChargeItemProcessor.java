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
package org.apache.fineract.portfolio.loanaccount.jobs.applychargetooverdueloaninstallment;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

/**
 * Uses the same SQL-based eligibility predicate as {@link OverdueLoanItemReader} (scoped to a single loan id) so
 * there is only one source of truth for "is this installment overdue", rather than re-deriving the date window in
 * Java against a separately-loaded {@link org.apache.fineract.portfolio.loanaccount.domain.Loan}.
 */
@RequiredArgsConstructor
public class OverdueLoanChargeItemProcessor implements ItemProcessor<Long, OverdueLoanChargeItem> {

    private final ConfigurationDomainService configurationDomainService;
    private final LoanReadPlatformService loanReadPlatformService;

    private Long penaltyWaitPeriod;
    private Boolean backdatePenalties;

    @BeforeStep
    public void beforeStep(@NonNull StepExecution stepExecution) {
        penaltyWaitPeriod = configurationDomainService.retrievePenaltyWaitPeriod();
        backdatePenalties = configurationDomainService.isBackdatePenaltiesEnabled();
    }

    @Override
    public OverdueLoanChargeItem process(@NonNull Long loanId) {
        final Collection<OverdueLoanScheduleData> overdueLoanScheduleData = loanReadPlatformService
                .retrieveOverdueInstallmentsForLoanId(loanId, penaltyWaitPeriod, backdatePenalties);
        if (overdueLoanScheduleData.isEmpty()) {
            return null;
        }
        return new OverdueLoanChargeItem(loanId, overdueLoanScheduleData);
    }
}
