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

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CampaignReadServiceTest {

    @Test
    void retryCoversFailuresThatCanSucceedAndMissingPhones() {
        assertThat(CampaignReadService.retryable("FAILED", "NO_SMS_CREDIT")).isTrue();
        assertThat(CampaignReadService.retryable("FAILED", "GATEWAY_REFUSED")).isTrue();
        assertThat(CampaignReadService.retryable("SKIPPED", "NO_PHONE")).isTrue();
        assertThat(CampaignReadService.retryable("SKIPPED", "INVALID_PHONE")).isTrue();
        assertThat(CampaignReadService.retryable("SKIPPED", "OPTED_OUT")).isFalse();
        assertThat(CampaignReadService.retryable("SKIPPED", "PHONE_SHARED")).isFalse();
        assertThat(CampaignReadService.retryable("SKIPPED", "STOPPED")).isFalse();
        assertThat(CampaignReadService.retryable("SENT", null)).isFalse();
    }

    @Test
    void listsArePagedWithASafeSize() {
        assertThat(CampaignReadService.size(null)).isEqualTo(25);
        assertThat(CampaignReadService.size(0)).isEqualTo(25);
        assertThat(CampaignReadService.size(100)).isEqualTo(100);
        assertThat(CampaignReadService.size(10_000)).isEqualTo(500);
        assertThat(CampaignReadService.from(null)).isZero();
        assertThat(CampaignReadService.from(-5)).isZero();
        assertThat(CampaignReadService.from(50)).isEqualTo(50);
    }

    @Test
    void moneyIsShownInFullWithTheCurrency() {
        assertThat(CommMessageRenderer.money("UGX", new BigDecimal("1250000.00"))).isEqualTo("UGX 1,250,000");
        assertThat(CommMessageRenderer.money("UGX", new BigDecimal("85000.5"))).isEqualTo("UGX 85,000.50");
        assertThat(CommMessageRenderer.money("UGX", null)).isEqualTo("UGX 0");
        assertThat(CommMessageRenderer.money("", BigDecimal.TEN)).isEqualTo("10");
    }

    @Test
    void messagesAreRenderedAsPlainText() {
        final CommMessageRenderer renderer = new CommMessageRenderer(null, null);
        final String text = renderer.render(renderer.compile("Hi {{client.name}} & welcome, {{missing.value}}!", "t"),
                Map.of("client", Map.of("name", "O'Neil <Grace>")));
        assertThat(text).isEqualTo("Hi O'Neil <Grace> & welcome, !");
    }
}
