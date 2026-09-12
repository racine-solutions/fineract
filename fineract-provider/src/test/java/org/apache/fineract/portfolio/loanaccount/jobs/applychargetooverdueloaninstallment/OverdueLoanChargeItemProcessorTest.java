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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepExecution;

public class OverdueLoanChargeItemProcessorTest {

    private ConfigurationDomainService configurationDomainService;
    private LoanReadPlatformService loanReadPlatformService;
    private OverdueLoanChargeItemProcessor processor;

    @BeforeEach
    public void setUp() {
        configurationDomainService = mock(ConfigurationDomainService.class);
        loanReadPlatformService = mock(LoanReadPlatformService.class);
        processor = new OverdueLoanChargeItemProcessor(configurationDomainService, loanReadPlatformService);
        processor.beforeStep(mock(StepExecution.class));
    }

    @Test
    public void testProcess_WhenNoOverdueInstallments_ReturnsNullToSkipTheLoan() {
        when(loanReadPlatformService.retrieveOverdueInstallmentsForLoanId(anyLong(), any(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        assertNull(processor.process(42L));
    }

    @Test
    public void testProcess_WhenOverdueInstallmentsExist_ReturnsChargeItem() {
        OverdueLoanScheduleData overdueData = mock(OverdueLoanScheduleData.class);
        List<OverdueLoanScheduleData> overdueList = List.of(overdueData);
        when(loanReadPlatformService.retrieveOverdueInstallmentsForLoanId(anyLong(), any(), anyBoolean())).thenReturn(overdueList);

        OverdueLoanChargeItem item = processor.process(42L);

        assertEquals(42L, item.loanId());
        assertEquals(overdueList, item.overdueLoanScheduleData());
    }
}
