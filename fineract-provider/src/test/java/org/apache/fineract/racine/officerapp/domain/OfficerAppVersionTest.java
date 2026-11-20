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
package org.apache.fineract.racine.officerapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OfficerAppVersionTest {

    @Test
    void olderWhenAnyLeadingNumberIsLower() {
        assertThat(OfficerAppVersion.isOlder("2025.12.2-beta.0.18", "2026.12.1")).isTrue();
        assertThat(OfficerAppVersion.isOlder("2026.11.9", "2026.12.1")).isTrue();
        assertThat(OfficerAppVersion.isOlder("2026.12", "2026.12.1")).isTrue();
    }

    @Test
    void notOlderWhenEqualOrNewer() {
        assertThat(OfficerAppVersion.isOlder("2026.12.1", "2026.12.1")).isFalse();
        assertThat(OfficerAppVersion.isOlder("2026.12.1 (61)", "2026.12.1")).isFalse();
        assertThat(OfficerAppVersion.isOlder("2026.12.10", "2026.12.9")).isFalse();
        assertThat(OfficerAppVersion.isOlder("v2027.1.0", "2026.12.1")).isFalse();
    }

    @Test
    void noMinimumMeansNothingIsOld() {
        assertThat(OfficerAppVersion.isOlder(null, null)).isFalse();
        assertThat(OfficerAppVersion.isOlder("1.0", " ")).isFalse();
    }

    @Test
    void unknownVersionIsOlderThanAMinimum() {
        assertThat(OfficerAppVersion.isOlder(null, "2026.12.1")).isTrue();
        assertThat(OfficerAppVersion.isOlder("beta", "2026.12.1")).isTrue();
    }

    @Test
    void deviceLabelAvoidsRepeatingTheMaker() {
        assertThat(DeviceInfo.label("TECNO", "TECNO Spark 20")).isEqualTo("TECNO Spark 20");
        assertThat(DeviceInfo.label("samsung", "Galaxy A15")).isEqualTo("Samsung Galaxy A15");
        assertThat(DeviceInfo.label(null, null)).isEqualTo("Unknown device");
    }
}
