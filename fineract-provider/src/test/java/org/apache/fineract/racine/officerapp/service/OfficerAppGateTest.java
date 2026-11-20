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
package org.apache.fineract.racine.officerapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OfficerAppGateTest {

    @Test
    void describesLoanCommandsWithAccountAndClient() {
        assertThat(OfficerAppGate.describe("REPAYMENT", "LOAN", 2231L, "Namubiru Grace", "000002231", null))
                .isEqualTo("Repayment on loan 000002231 · Namubiru Grace");
    }

    @Test
    void describesOtherCommandsWithResourceId() {
        assertThat(OfficerAppGate.describe("CREATE", "CLIENT", 1047L, "Waiswa Moses", null, null))
                .isEqualTo("Created client #1047 · Waiswa Moses");
        assertThat(OfficerAppGate.describe("CREATE", "DATATABLE_ENTRY", 5L, null, null, null)).isEqualTo("Created datatable entry #5");
    }

    @Test
    void readsAmountsFromCommandJson() {
        assertThat(OfficerAppGate.amountOf("{\"transactionAmount\":\"150,000\",\"locale\":\"en\"}")).isEqualByComparingTo("150000");
        assertThat(OfficerAppGate.amountOf("{\"principal\":1200000}")).isEqualByComparingTo(new BigDecimal("1200000"));
        assertThat(OfficerAppGate.amountOf("{\"note\":\"x\"}")).isNull();
        assertThat(OfficerAppGate.amountOf("not json")).isNull();
    }

    @Test
    void shortCodeIsStableAndFormatted() {
        final String code = OfficerAppSessionService.shortCode("6f1c2b8e-1d2a-4f0b-9a77-3c5e2d1f0a11");
        assertThat(code).matches("[0-9A-F]{4}-[0-9A-F]{4}")
                .isEqualTo(OfficerAppSessionService.shortCode("6f1c2b8e-1d2a-4f0b-9a77-3c5e2d1f0a11"));
        assertThat(OfficerAppSessionService.hashToken("abc")).hasSize(64);
    }

    @Test
    void onlineStatusFollowsSessionsAndLastSeen() {
        final LocalDateTime onlineSince = LocalDateTime.of(2026, 11, 20, 10, 0);
        assertThat(OfficerAppReadService.onlineStatus(0, onlineSince.plusMinutes(1), onlineSince)).isEqualTo("SIGNED_OUT");
        assertThat(OfficerAppReadService.onlineStatus(1, onlineSince.plusMinutes(1), onlineSince)).isEqualTo("ONLINE");
        assertThat(OfficerAppReadService.onlineStatus(1, onlineSince.minusMinutes(1), onlineSince)).isEqualTo("IDLE");
    }
}
