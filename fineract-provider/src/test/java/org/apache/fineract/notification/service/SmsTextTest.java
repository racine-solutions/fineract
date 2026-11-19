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
package org.apache.fineract.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SmsTextTest {

    private static final String HASHES = "\nx/MrSJkY/JS 3esZTVbW2qB";

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void aShortMessageIsSignedWithTheTenant() {
        assertThat(SmsText.fit("Hi Doe, your loan #0****42 is approved.", "Showroom"))
                .isEqualTo("Hi Doe, your loan #0****42 is approved. - Showroom");
    }

    @Test
    void theTenantIsLeftOffWhenItWouldNotFit() {
        String message = "x".repeat(155);
        assertThat(SmsText.fit(message, "Showroom")).isEqualTo(message);
        String exact = "x".repeat(160 - " - Showroom".length());
        assertThat(SmsText.fit(exact, "Showroom")).hasSize(160).endsWith(" - Showroom");
    }

    @Test
    void aLongMessageIsCutToOneSmsWithoutTheTenant() {
        String fitted = SmsText.fit("Hi Doe, " + "detail ".repeat(40), "Showroom");
        assertThat(fitted).hasSizeLessThanOrEqualTo(160).startsWith("Hi Doe, detail").doesNotContain("Showroom");
    }

    @Test
    void aCutNeverSplitsAWord() {
        String message = "Hi Doe, transfer of UGX 50000.00 to account #0****13 was successful. " + "Description words ".repeat(10);
        String fitted = SmsText.fit(message, "Showroom");
        assertThat(fitted).hasSizeLessThanOrEqualTo(160).endsWith("words").isEqualTo(message.substring(0, fitted.length()));
        // the next character in the original is the space after a whole word
        assertThat(message.charAt(fitted.length())).isEqualTo(' ');
    }

    @Test
    void aCutDropsADanglingCommaOrColon() {
        assertThat(SmsText.cutAtWord("Hi Doe, reason: something long", 15)).isEqualTo("Hi Doe, reason");
        assertThat(SmsText.cutAtWord("Hi Doe, reason: something long", 9)).isEqualTo("Hi Doe");
    }

    @Test
    void oneWordLongerThanTheRoomIsCutWhereTheRoomEnds() {
        assertThat(SmsText.cutAtWord("x".repeat(200), 160)).hasSize(160);
    }

    @Test
    void theAppHashStaysLastSoTheAppCanStillReadTheCode() {
        assertThat(SmsText.fit("Hi Doe, your sign-up code is 482913." + HASHES, "Showroom"))
                .isEqualTo("Hi Doe, your sign-up code is 482913. - Showroom" + HASHES);
        String cut = SmsText.fit("Hi Doe, your sign-up code is 482913. " + "x".repeat(200) + HASHES, "Showroom");
        assertThat(cut).isEqualTo("Hi Doe, your sign-up code is 482913." + HASHES);
    }

    @Test
    void withoutATenantNothingIsAdded() {
        assertThat(SmsText.fit("Hi Doe.", null)).isEqualTo("Hi Doe.");
        assertThat(SmsText.fit(null, "Showroom")).isNull();
    }

    @Test
    void theTenantShortNameIsTheFirstWordOfItsName() {
        ThreadLocalContextUtil.setTenant(
                FineractPlatformTenant.builder().id(1L).tenantIdentifier("default").name("  Showroom Sacco Association").build());
        assertThat(SmsText.currentTenantShortName()).isEqualTo("Showroom");
        assertThat(SmsText.fit("Hi Doe.")).isEqualTo("Hi Doe. - Showroom");
    }

    @Test
    void noTenantOnTheThreadMeansNoSignature() {
        ThreadLocalContextUtil.reset();
        assertThat(SmsText.currentTenantShortName()).isNull();
        assertThat(SmsText.firstWord(" ")).isNull();
    }

    @Test
    void aMessageThatAlreadyEndsWithTheInstitutionIsNotSignedTwice() {
        assertThat(SmsText.fit("Hi Test, your balance is UGX 0. Default", "Default")).isEqualTo("Hi Test, your balance is UGX 0. Default");
        assertThat(SmsText.fit("Thanks, Showroom!", "Showroom")).isEqualTo("Thanks, Showroom!");
        assertThat(SmsText.fit("Hi Test", "Default")).isEqualTo("Hi Test - Default");
    }

    @Test
    void staffNamesReadFirstNameFirst() {
        assertThat(SmsText.personName("Campaign", "Test Staff", "Test Staff, Campaign")).isEqualTo("Campaign Test Staff");
        assertThat(SmsText.personName(null, " ", "Test Staff, Campaign")).isEqualTo("Test Staff, Campaign");
    }
}
