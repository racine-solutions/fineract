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
package org.apache.fineract.infrastructure.core.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.configuration.data.SMTPCredentialsData;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelfServicePluginEmailServiceTest {

    @Mock
    private ExternalServicesPropertiesReadPlatformService externalServicesReadPlatformService;

    private SelfServicePluginEmailService service;

    @BeforeEach
    void setUp() {
        service = new SelfServicePluginEmailService(externalServicesReadPlatformService);
    }

    @Test
    void sendDefinedEmail_callsExternalServiceForCredentials() {
        SMTPCredentialsData credentials = new SMTPCredentialsData().setHost("smtp.example.com").setPort("587").setUsername("user")
                .setPassword("pass").setFromEmail("noreply@example.com");

        when(externalServicesReadPlatformService.getSMTPCredentials()).thenReturn(credentials);

        EmailDetail emailDetail = new EmailDetail("Subject", "Body", "to@example.com", "Recipient");

        // Since we don't have a real SMTP server running, sending will throw MailSendException,
        // which gets wrapped in PlatformEmailSendException.
        assertThrows(PlatformEmailSendException.class, () -> service.sendDefinedEmail(emailDetail));

        verify(externalServicesReadPlatformService).getSMTPCredentials();
    }

    @Test
    void sendFormattedEmail_callsExternalServiceForCredentials() {
        SMTPCredentialsData credentials = new SMTPCredentialsData().setHost("smtp.example.com").setPort("587").setUsername("user")
                .setPassword("pass").setFromEmail("noreply@example.com");

        when(externalServicesReadPlatformService.getSMTPCredentials()).thenReturn(credentials);

        EmailDetail emailDetail = new EmailDetail("Subject", "<html>Body</html>", "to@example.com", "Recipient");

        assertThrows(PlatformEmailSendException.class, () -> service.sendFormattedEmail(emailDetail));

        verify(externalServicesReadPlatformService).getSMTPCredentials();
    }
}
