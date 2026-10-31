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
package org.apache.fineract.selfservice.registration.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import org.apache.fineract.portfolio.client.domain.Client;
import org.junit.jupiter.api.Test;

class SelfServiceRegistrationTest {

    @Test
    void instance_shouldCreateRegistrationWithAllFields() {
        Client client = mock(Client.class);
        SelfServiceRegistration reg = SelfServiceRegistration.instance(client, "000000001", "Pedro", "Marmol", "Perez", "5522649498",
                "pedro@test.com", "1234", "external-token", "pedro.marmol", "SecurePass123#", SelfServiceRequestType.REGISTRATION,
                LocalDateTime.now().plusSeconds(30));

        assertEquals(client, reg.getClient());
        assertEquals("000000001", reg.getAccountNumber());
        assertEquals("Pedro", reg.getFirstName());
        assertEquals("Marmol", reg.getMiddleName());
        assertEquals("Perez", reg.getLastName());
        assertEquals("5522649498", reg.getMobileNumber());
        assertEquals("pedro@test.com", reg.getEmail());
        assertEquals("1234", reg.getAuthenticationToken());
        assertEquals("external-token", reg.getExternalAuthorizationToken());
        assertEquals("pedro.marmol", reg.getUsername());
        assertEquals("SecurePass123#", reg.getPassword());
        assertEquals(SelfServiceRequestType.REGISTRATION, reg.getRequestType());
        assertFalse(reg.isConsumed());
        assertNotNull(reg.getExpiresAt());
        assertNotNull(reg.getCreatedDate());
    }

    @Test
    void instance_shouldHandleNullMiddleName() {
        Client client = mock(Client.class);
        SelfServiceRegistration reg = SelfServiceRegistration.instance(client, "000000002", "John", null, "Doe", null, "john@test.com",
                "5678", "other-token", "john.doe", "Pass456#", SelfServiceRequestType.PASSWORD_RESET, LocalDateTime.now().plusSeconds(30));

        assertNull(reg.getMiddleName());
        assertNull(reg.getMobileNumber());
        assertEquals("John", reg.getFirstName());
    }

    @Test
    void defaultConstructor_shouldCreateEmptyInstance() {
        SelfServiceRegistration reg = new SelfServiceRegistration();
        assertNull(reg.getClient());
        assertNull(reg.getFirstName());
    }
}
