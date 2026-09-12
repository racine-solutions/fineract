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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepExecution;

public class OverdueLoanItemReaderTest {

    private ConfigurationDomainService configurationDomainService;
    private LoanReadPlatformService loanReadPlatformService;
    private OverdueLoanItemReader reader;

    @BeforeEach
    public void setUp() {
        configurationDomainService = mock(ConfigurationDomainService.class);
        loanReadPlatformService = mock(LoanReadPlatformService.class);
        reader = new OverdueLoanItemReader(configurationDomainService, loanReadPlatformService);
    }

    @Test
    public void testRead_ReturnsEachEligibleLoanIdThenNull() throws Exception {
        when(loanReadPlatformService.retrieveAllLoanIdsWithOverdueInstallments(anyLong(), anyBoolean())).thenReturn(List.of(1L, 2L));

        reader.beforeStep(mock(StepExecution.class));

        assertEquals(1L, reader.read());
        assertEquals(2L, reader.read());
        assertNull(reader.read());
    }

    @Test
    public void testRead_WhenNoLoansEligible_ReturnsNullImmediately() throws Exception {
        when(loanReadPlatformService.retrieveAllLoanIdsWithOverdueInstallments(anyLong(), anyBoolean())).thenReturn(List.of());

        reader.beforeStep(mock(StepExecution.class));

        assertNull(reader.read());
    }
}
