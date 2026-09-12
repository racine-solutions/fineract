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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

public class OverdueLoanChargeItemWriterTest {

    private LoanChargeWritePlatformService loanChargeWritePlatformService;
    private OverdueLoanChargeItemWriter writer;

    @BeforeEach
    public void setUp() {
        loanChargeWritePlatformService = mock(LoanChargeWritePlatformService.class);
        writer = new OverdueLoanChargeItemWriter(loanChargeWritePlatformService);
    }

    @Test
    public void testWrite_AppliesChargesForEveryItemInTheChunk() throws Exception {
        OverdueLoanScheduleData overdueData = mock(OverdueLoanScheduleData.class);
        OverdueLoanChargeItem item1 = new OverdueLoanChargeItem(1L, List.of(overdueData));
        OverdueLoanChargeItem item2 = new OverdueLoanChargeItem(2L, List.of(overdueData));

        writer.write(new Chunk<>(List.of(item1, item2)));

        verify(loanChargeWritePlatformService, times(1)).applyOverdueChargesForLoan(1L, item1.overdueLoanScheduleData());
        verify(loanChargeWritePlatformService, times(1)).applyOverdueChargesForLoan(2L, item2.overdueLoanScheduleData());
    }

    @Test
    public void testWrite_WhenChunkEmpty_DoesNothing() throws Exception {
        writer.write(new Chunk<>(Collections.emptyList()));

        verify(loanChargeWritePlatformService, times(0)).applyOverdueChargesForLoan(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyCollection());
    }
}
