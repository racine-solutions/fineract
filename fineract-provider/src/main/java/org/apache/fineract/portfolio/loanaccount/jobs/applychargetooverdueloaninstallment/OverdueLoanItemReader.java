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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemReader;
import org.springframework.lang.NonNull;

/**
 * Reads the ids of loans that have overdue installments eligible for a penalty charge. Only the (lightweight) list
 * of matching ids is fetched up front; the actual schedule data for each one is looked up lazily, per loan, by
 * {@link OverdueLoanChargeItemProcessor} - so at no point is more than one loan's worth of data pulled into memory
 * beyond this id list, regardless of how many loans are eligible tenant-wide.
 */
@Slf4j
@RequiredArgsConstructor
public class OverdueLoanItemReader implements ItemReader<Long> {

    private final ConfigurationDomainService configurationDomainService;
    private final LoanReadPlatformService loanReadPlatformService;

    private Queue<Long> remainingLoanIds;

    @BeforeStep
    public void beforeStep(@NonNull StepExecution stepExecution) {
        final Long penaltyWaitPeriod = configurationDomainService.retrievePenaltyWaitPeriod();
        final Boolean backdatePenalties = configurationDomainService.isBackdatePenaltiesEnabled();
        remainingLoanIds = new ConcurrentLinkedQueue<>(
                loanReadPlatformService.retrieveAllLoanIdsWithOverdueInstallments(penaltyWaitPeriod, backdatePenalties));
        log.info("Found {} loan(s) with overdue installments to evaluate for penalty charges", remainingLoanIds.size());
    }

    @Override
    public Long read() {
        return remainingLoanIds.poll();
    }
}
