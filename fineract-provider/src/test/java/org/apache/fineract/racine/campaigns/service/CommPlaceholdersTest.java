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

import org.apache.fineract.racine.campaigns.data.MessageCheckData;
import org.apache.fineract.racine.campaigns.data.PlaceholderData;
import org.junit.jupiter.api.Test;

class CommPlaceholdersTest {

    @Test
    void longestLengthUsesTheLongestValueOfEachPlaceholder() {
        // 20 for the name, 17 for the amount
        assertThat(CommPlaceholders.longestLength("Hi {{client.name}}, pay {{loans.nextDueAmount}}."))
                .isEqualTo("Hi , pay .".length() + 20 + 17);
        assertThat(CommPlaceholders.longestLength("Hi {{ client.name }}")).isEqualTo(3 + 20);
    }

    @Test
    void unknownPlaceholdersAreReported() {
        assertThat(CommPlaceholders.unknown("Hi {{client.name}} {{client.salary}} {{client.salary}}")).containsExactly("client.salary");
    }

    @Test
    void aMessageThatCouldPass160IsFlagged() {
        final MessageCheckData check = CommPlaceholders.check("x".repeat(150) + "{{client.name}}", null);
        assertThat(check.longestLength()).isEqualTo(170);
        assertThat(check.limit()).isEqualTo(160);
        assertThat(check.fits()).isFalse();
    }

    @Test
    void accentedLettersCutTheLimitTo70() {
        final MessageCheckData check = CommPlaceholders.check("Hola, su código es listo", null);
        assertThat(check.unicode()).isTrue();
        assertThat(check.limit()).isEqualTo(70);
        assertThat(CommPlaceholders.check("Hi there, café closed", null).unicode()).isFalse();
    }

    @Test
    void theCheckShowsTheRenderedMessage() {
        final MessageCheckData check = CommPlaceholders.check("Hi {{client.name}}", "Hi Mwesigye");
        assertThat(check.message()).isEqualTo("Hi Mwesigye");
        assertThat(check.length()).isEqualTo(11);
        assertThat(check.fits()).isTrue();
    }

    @Test
    void everyPlaceholderHasAnExampleWithinItsLongestValueAndIsFilledIn() {
        assertThat(CommPlaceholders.ALL.stream().map(PlaceholderData::key).distinct().count()).isEqualTo(CommPlaceholders.ALL.size());
        CommPlaceholders.ALL.forEach(placeholder -> assertThat(placeholder.example().length()).as(placeholder.key())
                .isLessThanOrEqualTo(placeholder.maxLength()));
        final String text = CommPlaceholders.ALL.stream().map(placeholder -> "{{" + placeholder.key() + "}}")
                .collect(java.util.stream.Collectors.joining(" "));
        final CommMessageRenderer renderer = new CommMessageRenderer(null, null);
        final String rendered = renderer.render(renderer.compile(text, "all"), CommMessageRenderer.exampleScope());
        assertThat(rendered).doesNotContain("{{").contains("Business Loan", "7 Nov 2026", "Sarah Namuli");
    }

    @Test
    void amountsShowCentsWithTwoDigitsAndWholeAmountsWithout() {
        assertThat(CommMessageRenderer.money("USD", new java.math.BigDecimal("1126324.900000"))).isEqualTo("USD 1,126,324.90");
        assertThat(CommMessageRenderer.money("UGX", new java.math.BigDecimal("70000.000000"))).isEqualTo("UGX 70,000");
        assertThat(CommMessageRenderer.money("UGX", new java.math.BigDecimal("85000.5"))).isEqualTo("UGX 85,000.50");
        assertThat(CommMessageRenderer.money("UGX", null)).isEqualTo("UGX 0");
    }

    @Test
    void staffMessagesRefuseClientTagsAndClientMessagesRefuseStaffTags() {
        assertThat(CommPlaceholders.unsuitable("Hi {{staff.firstName}}, {{staff.loansInArrears}} loans are late. {{tenant.name}}", true))
                .isEmpty();
        assertThat(CommPlaceholders.unsuitable("Hi {{staff.name}}, pay {{loans.nextDueAmount}} by {{loans.nextDueDate}}", true))
                .containsExactly("loans.nextDueAmount", "loans.nextDueDate");
        assertThat(CommPlaceholders.unsuitable("Hi {{client.name}}, your officer is {{staff.name}}", false)).containsExactly("staff.name");
    }
}
