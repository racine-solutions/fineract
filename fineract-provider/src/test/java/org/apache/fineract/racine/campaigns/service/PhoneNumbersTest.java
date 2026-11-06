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

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void localNumbersGetTheCountryCode() {
        assertThat(PhoneNumbers.international("0785 154 434", "256")).isEqualTo("256785154434");
        assertThat(PhoneNumbers.international("785154434", "256")).isEqualTo("256785154434");
        assertThat(PhoneNumbers.international("+256-785-154434", "256")).isEqualTo("256785154434");
        assertThat(PhoneNumbers.international("00256785154434", "256")).isEqualTo("256785154434");
    }

    @Test
    void numbersThatCannotBePhonesAreRefused() {
        assertThat(PhoneNumbers.international("07851544", "256")).isNull();
        assertThat(PhoneNumbers.international("not a phone", "256")).isNull();
        assertThat(PhoneNumbers.international(null, "256")).isNull();
    }

    @Test
    void theSamePhoneTypedDifferentlyHasOneKey() {
        assertThat(PhoneNumbers.key("0785154434")).isEqualTo(PhoneNumbers.key("+256 785 154434")).isEqualTo("785154434");
        assertThat(PhoneNumbers.key("1234")).isNull();
    }
}
