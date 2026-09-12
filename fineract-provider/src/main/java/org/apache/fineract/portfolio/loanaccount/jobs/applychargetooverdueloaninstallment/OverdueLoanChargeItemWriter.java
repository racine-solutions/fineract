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

import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformService;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;

/**
 * Applies the penalty charge(s) for each loan in the chunk. Exceptions are deliberately left to propagate (rather than
 * caught here) so that the step's fault-tolerant skip policy can isolate and skip the offending loan without discarding
 * the rest of the chunk.
 */
@RequiredArgsConstructor
public class OverdueLoanChargeItemWriter implements ItemWriter<OverdueLoanChargeItem> {

    private final LoanChargeWritePlatformService loanChargeWritePlatformService;

    @Override
    public void write(@NonNull Chunk<? extends OverdueLoanChargeItem> chunk) {
        for (final OverdueLoanChargeItem item : chunk) {
            loanChargeWritePlatformService.applyOverdueChargesForLoan(item.loanId(), item.overdueLoanScheduleData());
        }
    }
}
