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
package org.apache.fineract.selfservice.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.junit.jupiter.api.Test;

class SelfServiceSupportContactsTest {

    private final ConfigurationDomainService configuration = mock(ConfigurationDomainService.class);
    private final SelfServiceSupportContacts contacts = new SelfServiceSupportContacts(configuration);

    @Test
    void severalNumbers_areListedInOrderWithoutBlanksOrRepeats() {
        when(configuration.retrieveSelfServiceSupportPhoneNumbers()).thenReturn(" +256 774 067 967 , ,+256 700 000 001;+256 774 067 967");

        assertThat(contacts.phoneNumbers()).containsExactly("+256 774 067 967", "+256 700 000 001");
        assertThat(contacts.callInstruction()).isEqualTo("call +256 774 067 967 or +256 700 000 001");
    }

    @Test
    void oneNumber_readsNaturally() {
        when(configuration.retrieveSelfServiceSupportPhoneNumbers()).thenReturn("+256 774 067 967");

        assertThat(contacts.callInstruction()).isEqualTo("call +256 774 067 967");
    }

    @Test
    void noNumbersOrUnreadableSetting_fallsBackToTheBranch() {
        when(configuration.retrieveSelfServiceSupportPhoneNumbers()).thenReturn(null);
        assertThat(contacts.callInstruction()).isEqualTo("contact your branch");

        when(configuration.retrieveSelfServiceSupportPhoneNumbers()).thenThrow(new IllegalStateException("missing"));
        assertThat(contacts.phoneNumbers()).isEmpty();
    }
}
