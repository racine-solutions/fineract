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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;

public class ApplyChargeToOverdueLoanInstallmentStepExecutionListenerTest {

    private final ApplyChargeToOverdueLoanInstallmentStepExecutionListener listener = new ApplyChargeToOverdueLoanInstallmentStepExecutionListener();

    @Test
    public void testAfterStep_WhenNoSkips_KeepsOriginalExitStatus() {
        StepExecution stepExecution = newStepExecution();
        stepExecution.setExitStatus(ExitStatus.COMPLETED);

        assertEquals(ExitStatus.COMPLETED, listener.afterStep(stepExecution));
    }

    @Test
    public void testAfterStep_WhenLoansWereSkipped_MarksStepFailed() {
        StepExecution stepExecution = newStepExecution();
        stepExecution.setExitStatus(ExitStatus.COMPLETED);
        stepExecution.setProcessSkipCount(1);

        assertEquals(ExitStatus.FAILED, listener.afterStep(stepExecution));
    }

    private StepExecution newStepExecution() {
        JobInstance jobInstance = new JobInstance(1L, "APPLY_CHARGE_TO_OVERDUE_LOAN_INSTALLMENT");
        JobExecution jobExecution = new JobExecution(jobInstance, 1L, null);
        jobExecution.setStatus(BatchStatus.STARTED);
        return new StepExecution("applyChargeToOverdueLoanInstallmentStep", jobExecution);
    }
}
