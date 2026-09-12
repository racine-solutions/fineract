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

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.lang.NonNull;

/**
 * Individual loan failures are skipped (not fatal to the step) so the rest of the portfolio still gets processed. This
 * listener marks the step - and therefore the job - as failed overall if any loan was skipped, matching the original
 * behaviour of surfacing a job failure whenever at least one loan could not be charged.
 */
public class ApplyChargeToOverdueLoanInstallmentStepExecutionListener implements StepExecutionListener {

    @Override
    @NonNull
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        if (stepExecution.getSkipCount() > 0) {
            return ExitStatus.FAILED;
        }
        return stepExecution.getExitStatus();
    }
}
