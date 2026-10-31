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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.selfservice.security.domain.SelfServiceAuthenticationToken;
import org.apache.fineract.selfservice.security.domain.SelfServiceAuthenticationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SelfServiceAuthenticationTokenServiceTest {

    private SelfServiceAuthenticationTokenRepository repository;
    private ConfigurationDomainService configurationDomainService;
    private SelfServiceAuthenticationTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(SelfServiceAuthenticationTokenRepository.class);
        configurationDomainService = mock(ConfigurationDomainService.class);
        when(configurationDomainService.retrieveSelfServiceSessionTimeoutMinutes()).thenReturn(15);
        service = new SelfServiceAuthenticationTokenService(repository, configurationDomainService);
    }

    @Test
    void generateTokens_replacesOldSessionAndExpiresBothTokensAfterTheInactivityTimeout() {
        LocalDateTime before = LocalDateTime.now();

        service.generateTokens(7L, "client@mail.com");

        verify(repository).deleteByUserId(7L);
        ArgumentCaptor<SelfServiceAuthenticationToken> saved = ArgumentCaptor.forClass(SelfServiceAuthenticationToken.class);
        verify(repository, times(2)).save(saved.capture());
        List<SelfServiceAuthenticationToken> tokens = saved.getAllValues();
        assertThat(tokens).extracting(SelfServiceAuthenticationToken::getTokenType)
                .containsExactly(SelfServiceAuthenticationToken.TokenType.ACCESS, SelfServiceAuthenticationToken.TokenType.REFRESH);
        for (SelfServiceAuthenticationToken token : tokens) {
            assertThat(Duration.between(before, token.getExpiresAt())).isBetween(Duration.ofMinutes(15), Duration.ofMinutes(16));
        }
    }

    @Test
    void extendSession_pushesExpiryToConfiguredMinutesFromNow() {
        when(configurationDomainService.retrieveSelfServiceSessionTimeoutMinutes()).thenReturn(30);
        LocalDateTime before = LocalDateTime.now();

        service.extendSession(7L);

        ArgumentCaptor<LocalDateTime> expiresAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).extendActiveTokens(eq(7L), expiresAt.capture(), any(LocalDateTime.class));
        assertThat(Duration.between(before, expiresAt.getValue())).isBetween(Duration.ofMinutes(30), Duration.ofMinutes(31));
    }

    @Test
    void revokeAllSessions_deletesEveryTokenOfTheUser() {
        service.revokeAllSessions(7L);

        verify(repository).deleteByUserId(7L);
    }
}
