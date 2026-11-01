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

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SelfServiceVerificationCodeTest {

    @Test
    void codesAreSixDigits_andVary() {
        final Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            final String code = SelfServiceVerificationCode.generate();
            assertThat(code).matches("\\d{6}");
            seen.add(code);
        }
        assertThat(seen.size()).isGreaterThan(40);
    }

    @Test
    void onlyTheHashIsStored_andTheRightCodeMatches() {
        final String hash = SelfServiceVerificationCode.hash("048213");
        assertThat(hash).doesNotContain("048213");
        assertThat(SelfServiceVerificationCode.matches("048213", hash)).isTrue();
        assertThat(SelfServiceVerificationCode.matches("048 213", hash)).isTrue();
        assertThat(SelfServiceVerificationCode.matches("048-213", hash)).isTrue();
    }

    @Test
    void wrongOrMalformedCodesDoNotMatch() {
        final String hash = SelfServiceVerificationCode.hash("048213");
        assertThat(SelfServiceVerificationCode.matches("048214", hash)).isFalse();
        assertThat(SelfServiceVerificationCode.matches("48213", hash)).isFalse();
        assertThat(SelfServiceVerificationCode.matches("04821a", hash)).isFalse();
        assertThat(SelfServiceVerificationCode.matches(null, hash)).isFalse();
        assertThat(SelfServiceVerificationCode.matches("048213", null)).isFalse();
    }
}
