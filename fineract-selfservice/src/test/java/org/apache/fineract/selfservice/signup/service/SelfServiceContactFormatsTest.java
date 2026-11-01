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
package org.apache.fineract.selfservice.signup.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SelfServiceContactFormatsTest {

    @Test
    void theSamePhoneInDifferentFormatsMatches() {
        assertThat(SelfServiceContactFormats.samePhone("0785154434", "256785154434")).isTrue();
        assertThat(SelfServiceContactFormats.samePhone("+256 785 154 434", "0785-154-434")).isTrue();
        assertThat(SelfServiceContactFormats.samePhone("0785154434", "0785154435")).isFalse();
        assertThat(SelfServiceContactFormats.samePhone("12345", "12345")).isFalse();
        assertThat(SelfServiceContactFormats.samePhone(null, "0785154434")).isFalse();
    }

    @Test
    void phonesAndEmailsAreRecognised() {
        assertThat(SelfServiceContactFormats.looksLikePhone("+256 785 154 434")).isTrue();
        assertThat(SelfServiceContactFormats.looksLikePhone("0785154434")).isTrue();
        assertThat(SelfServiceContactFormats.looksLikePhone("jdoe")).isFalse();
        assertThat(SelfServiceContactFormats.looksLikePhone("12345")).isFalse();
        assertThat(SelfServiceContactFormats.looksLikeEmail("j.doe@gmail.com")).isTrue();
        assertThat(SelfServiceContactFormats.looksLikeEmail("jdoe")).isFalse();
        assertThat(SelfServiceContactFormats.normaliseEmail(" J.Doe@Gmail.com ")).isEqualTo("j.doe@gmail.com");
    }

    @Test
    void maskingShowsOnlyEnoughToRecogniseTheDestination() {
        assertThat(SelfServiceContactFormats.maskPhone("256785154434")).isEqualTo("2567•••••434");
        assertThat(SelfServiceContactFormats.maskPhone("0785154434")).isEqualTo("0785•••434");
        assertThat(SelfServiceContactFormats.maskPhone("12")).isEqualTo("•••");
        assertThat(SelfServiceContactFormats.maskEmail("john@gmail.com")).isEqualTo("j•••@gmail.com");
    }

    @Test
    void smsNumbersAreSentInInternationalFormat() {
        assertThat(SelfServiceContactFormats.smsNumber("+256 785 154 434", "256")).isEqualTo("256785154434");
        assertThat(SelfServiceContactFormats.smsNumber("256785154434", "256")).isEqualTo("256785154434");
        assertThat(SelfServiceContactFormats.smsNumber("0785154434", "256")).isEqualTo("256785154434");
        assertThat(SelfServiceContactFormats.smsNumber("785152234", "256")).isEqualTo("256785152234");
        assertThat(SelfServiceContactFormats.smsNumber("00254712345678", "256")).isEqualTo("254712345678");
    }
}
