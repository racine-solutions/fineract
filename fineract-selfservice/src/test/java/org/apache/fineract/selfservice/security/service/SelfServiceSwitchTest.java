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
package org.apache.fineract.selfservice.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationChangedEvent;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.selfservice.audit.data.SelfServiceEventEntry;
import org.apache.fineract.selfservice.audit.service.SelfServiceEventRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SelfServiceSwitchTest {

    private ConfigurationDomainService configuration;
    private SelfServiceAuthenticationTokenService tokenService;
    private SelfServiceEventRecorder recorder;
    private SelfServiceSwitch selfServiceSwitch;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default Tenant", "UTC", null));
        configuration = mock(ConfigurationDomainService.class);
        tokenService = mock(SelfServiceAuthenticationTokenService.class);
        recorder = mock(SelfServiceEventRecorder.class);
        selfServiceSwitch = new SelfServiceSwitch(configuration, tokenService, recorder, mock(PlatformSecurityContext.class));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void unreadableSwitch_countsAsOff() {
        when(configuration.isSelfServiceEnabled()).thenThrow(new IllegalStateException("not found"));

        assertThat(selfServiceSwitch.isOn()).isFalse();
    }

    @Test
    void switchedOffInSettings_endsEverySessionAtOnceAndIsRecorded() {
        selfServiceSwitch.onConfigurationChanged(new GlobalConfigurationChangedEvent(this,
                GlobalConfigurationConstants.ENABLE_RACINE_SELF_SERVICE, false, ThreadLocalContextUtil.getTenant()));

        verify(tokenService).revokeAllSessionsOfAllUsers();
        ArgumentCaptor<SelfServiceEventEntry> captor = ArgumentCaptor.forClass(SelfServiceEventEntry.class);
        verify(recorder).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(SelfServiceSwitch.SWITCHED_OFF_EVENT);
    }

    @Test
    void switchedOffOnAWorkerThreadWithoutTenant_usesTheEventsTenant() {
        final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        ThreadLocalContextUtil.reset();
        when(tokenService.revokeAllSessionsOfAllUsers()).thenAnswer(invocation -> {
            assertThat(ThreadLocalContextUtil.getTenant()).isSameAs(tenant);
            return 2L;
        });

        selfServiceSwitch.onConfigurationChanged(
                new GlobalConfigurationChangedEvent(this, GlobalConfigurationConstants.ENABLE_RACINE_SELF_SERVICE, false, tenant));

        verify(tokenService).revokeAllSessionsOfAllUsers();
        assertThat(ThreadLocalContextUtil.getTenant()).isNull();
    }

    @Test
    void requestsWhileOff_endSessionsOnlyOncePerOffPeriod() {
        selfServiceSwitch.refusedWhileOff();
        selfServiceSwitch.refusedWhileOff();
        verify(tokenService, times(1)).revokeAllSessionsOfAllUsers();

        selfServiceSwitch.allowedWhileOn();
        selfServiceSwitch.refusedWhileOff();
        verify(tokenService, times(2)).revokeAllSessionsOfAllUsers();
    }

    @Test
    void otherSettings_areIgnored() {
        selfServiceSwitch.onConfigurationChanged(new GlobalConfigurationChangedEvent(this, "enable-business-date", false, null));

        verify(tokenService, never()).revokeAllSessionsOfAllUsers();
        verify(recorder, never()).record(any());
    }

    @Test
    void failureToEndSessions_isRetriedOnTheNextRefusedRequest() {
        when(tokenService.revokeAllSessionsOfAllUsers()).thenThrow(new IllegalStateException("db")).thenReturn(3L);

        selfServiceSwitch.refusedWhileOff();
        selfServiceSwitch.refusedWhileOff();

        verify(tokenService, times(2)).revokeAllSessionsOfAllUsers();
    }
}
