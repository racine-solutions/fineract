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
package org.apache.fineract.racine.campaigns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.racine.campaigns.data.AudienceClientData;
import org.apache.fineract.racine.campaigns.domain.DeliveryReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

class AudienceServiceTest {

    private AudienceService service;

    @BeforeEach
    void setUp() {
        final List<AudienceService.Recipient> recipients = new ArrayList<>();
        for (int i = 1; i <= 520; i++) {
            recipients.add(new AudienceService.Recipient((long) i, String.format("%09d", i), "Client " + i, "Head Office", "0785" + i,
                    "256785" + i, i % 10 == 0 ? DeliveryReason.NO_PHONE : null));
        }
        service = spy(new AudienceService(mock(JdbcTemplate.class), mock(Environment.class)));
        doReturn(recipients).when(service).recipients(any(), any(), any());
    }

    @Test
    void pagesThroughHundredsOfClients() {
        final Page<AudienceClientData> page = service.clients(List.of(), ".1.", null, "ALL", null, 500, 25);
        assertThat(page.getTotalFilteredRecords()).isEqualTo(520);
        assertThat(page.getPageItems()).hasSize(20).first().extracting(AudienceClientData::clientId).isEqualTo(501L);
    }

    @Test
    void showsWhoGetsItAndWhoIsSkipped() {
        assertThat(service.clients(List.of(), ".1.", null, "RECIPIENTS", null, 0, 25).getTotalFilteredRecords()).isEqualTo(468);
        final Page<AudienceClientData> skipped = service.clients(List.of(), ".1.", null, "SKIPPED", null, 0, 25);
        assertThat(skipped.getTotalFilteredRecords()).isEqualTo(52);
        assertThat(skipped.getPageItems()).allMatch(client -> "NO_PHONE".equals(client.skipReason()));
    }

    @Test
    void searchesNameAccountAndPhone() {
        assertThat(service.clients(List.of(), ".1.", null, "ALL", "client 12", 0, 25).getTotalFilteredRecords()).isEqualTo(11);
        assertThat(service.clients(List.of(), ".1.", null, "ALL", "000000007", 0, 25).getPageItems())
                .extracting(AudienceClientData::clientId).containsExactly(7L);
    }

    @Test
    void aPageNeverRunsPastTheEnd() {
        assertThat(service.clients(List.of(), ".1.", null, "ALL", null, 9999, 25).getPageItems()).isEmpty();
    }
}
