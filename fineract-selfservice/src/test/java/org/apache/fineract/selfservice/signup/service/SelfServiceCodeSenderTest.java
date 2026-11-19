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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.service.SelfServicePluginEmailService;
import org.apache.fineract.notification.service.SmsNotificationWritePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class SelfServiceCodeSenderTest {

    private ConfigurationDomainService configuration;
    private SelfServiceCodeSender sender;

    @BeforeEach
    void setUp() {
        configuration = mock(ConfigurationDomainService.class);
        sender = new SelfServiceCodeSender(mock(SmsNotificationWritePlatformService.class), mock(SelfServicePluginEmailService.class),
                configuration, mock(Environment.class));
    }

    @Test
    void theSmsEndsWithEveryValidAppHash() {
        when(configuration.retrieveSelfServiceSmsAppHash()).thenReturn("x/MrSJkY/JS, 3esZTVbW2qB,not-a-hash");
        final String text = sender.smsText(SelfServiceCodeSender.Purpose.SIGNUP, "482913", "Mwesigye", 10);
        assertThat(text).startsWith("Hi Mwesigye, your mobile app sign-up code is 482913. It expires in 10 minutes.")
                .endsWith("\nx/MrSJkY/JS 3esZTVbW2qB");
        assertThat(text.length()).isLessThanOrEqualTo(160);
    }

    @Test
    void withoutAHashTheSmsIsJustTheMessage() {
        when(configuration.retrieveSelfServiceSmsAppHash()).thenReturn(null);
        assertThat(sender.smsText(SelfServiceCodeSender.Purpose.PASSWORD_RESET, "482913", "Doe", 10)).doesNotContain("\n")
                .startsWith("Hi Doe, your password reset code is 482913.");
    }

    @Test
    void withoutANameTheSmsHasNoGreeting() {
        when(configuration.retrieveSelfServiceSmsAppHash()).thenReturn(null);
        assertThat(sender.smsText(SelfServiceCodeSender.Purpose.SIGNUP, "482913", null, 10))
                .startsWith("Your mobile app sign-up code is 482913.");
    }

    @Test
    void aNameTooLongForOneSmsIsDroppedSoTheAppCanStillReadTheCode() {
        when(configuration.retrieveSelfServiceSmsAppHash()).thenReturn("x/MrSJkY/JS,3esZTVbW2qB");
        final String text = sender.smsText(SelfServiceCodeSender.Purpose.SIGNUP, "482913", "Abcdefghijklmnopqrstuvwxyz-Abcdefghijklmnopqrs", 10);
        assertThat(text).startsWith("Your mobile app sign-up code is 482913.").endsWith("\nx/MrSJkY/JS 3esZTVbW2qB");
        assertThat(text.length()).isLessThanOrEqualTo(160);
    }
}
