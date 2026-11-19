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

class EmailAddressesTest {

    @Test
    void ordinaryAddressesAreKeptTrimmed() {
        assertThat(EmailAddresses.clean("  john.bosco@racine.co.ug ")).isEqualTo("john.bosco@racine.co.ug");
        assertThat(EmailAddresses.clean("a+b@example.com")).isEqualTo("a+b@example.com");
    }

    @Test
    void addressesThatCannotBeDeliveredAreRefused() {
        assertThat(EmailAddresses.clean(null)).isNull();
        assertThat(EmailAddresses.clean(" ")).isNull();
        assertThat(EmailAddresses.clean("john")).isNull();
        assertThat(EmailAddresses.clean("john@example")).isNull();
        assertThat(EmailAddresses.clean("john @example.com")).isNull();
        assertThat(EmailAddresses.clean("a@example.com, b@example.com")).isNull();
        assertThat(EmailAddresses.clean("a@b@example.com")).isNull();
    }

    @Test
    void theSameAddressInAnotherCaseHasOneKey() {
        assertThat(EmailAddresses.key("John@Example.com")).isEqualTo(EmailAddresses.key(" john@example.COM"));
        assertThat(EmailAddresses.key("not an email")).isNull();
    }
}
