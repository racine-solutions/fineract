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

import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.batch.core.SkipListener;

@Slf4j
public class ApplyChargeToOverdueLoanInstallmentSkipListener implements SkipListener<Long, OverdueLoanChargeItem> {

    @Override
    public void onSkipInProcess(Long loanId, Throwable t) {
        logFailure(loanId, t);
    }

    @Override
    public void onSkipInWrite(OverdueLoanChargeItem item, Throwable t) {
        logFailure(item.loanId(), t);
    }

    private void logFailure(Long loanId, Throwable t) {
        if (t instanceof PlatformApiDataValidationException e) {
            for (final ApiParameterError error : e.getErrors()) {
                log.error("Apply Charges due for overdue loans failed for account {} with message: {}", loanId, error.getDeveloperMessage(),
                        e);
            }
        } else if (t instanceof AbstractPlatformDomainRuleException e) {
            log.error("Apply Charges due for overdue loans failed for account {} with message: {}", loanId, e.getDefaultUserMessage(), e);
        } else {
            log.error("Apply Charges due for overdue loans failed for account {}", loanId, t);
        }
    }
}
