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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.selfservice.security.domain.SelfServiceAuthenticationToken;
import org.apache.fineract.selfservice.security.filter.SelfServiceTokenAuthenticationToken;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;

class SelfServiceTokenAuthenticationProviderTest {

    private static final Long USER_ID = 7L;
    private static final String USERNAME = "client@mail.com";
    private static final String TOKEN = "access-token";

    private SelfServiceAuthenticationTokenService tokenService;
    private UserDetailsService userDetailsService;
    private SelfServiceTokenAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        tokenService = mock(SelfServiceAuthenticationTokenService.class);
        userDetailsService = mock(UserDetailsService.class);
        provider = new SelfServiceTokenAuthenticationProvider(tokenService, userDetailsService,
                SelfServiceUserDetailsChecker.accountStatusChecker());
    }

    @Test
    void activeUser_isAuthenticatedAndSessionExtended() {
        givenSession();
        givenUser(true, true);

        Authentication result = provider.authenticate(tokenAuth());

        assertThat(result.isAuthenticated()).isTrue();
        verify(tokenService).extendSession(USER_ID);
        verify(tokenService, never()).revokeAllSessions(anyLong());
    }

    @Test
    void disabledUser_isRejectedAndAllSessionsRevoked() {
        givenSession();
        givenUser(false, true);

        assertThatThrownBy(() -> provider.authenticate(tokenAuth())).isInstanceOf(DisabledException.class);
        verify(tokenService).revokeAllSessions(USER_ID);
        verify(tokenService, never()).extendSession(anyLong());
    }

    @Test
    void lockedUser_isRejectedAndAllSessionsRevoked() {
        givenSession();
        givenUser(true, false);

        assertThatThrownBy(() -> provider.authenticate(tokenAuth())).isInstanceOf(LockedException.class);
        verify(tokenService).revokeAllSessions(USER_ID);
        verify(tokenService, never()).extendSession(anyLong());
    }

    @Test
    void expiredOrUnknownToken_isRejectedWithoutLoadingUser() {
        when(tokenService.findActiveAccessToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(tokenAuth())).isInstanceOf(BadCredentialsException.class);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(tokenService, never()).extendSession(anyLong());
    }

    private void givenSession() {
        SelfServiceAuthenticationToken session = new SelfServiceAuthenticationToken();
        session.setUserId(USER_ID);
        session.setUsername(USERNAME);
        session.setToken(TOKEN);
        session.setTokenType(SelfServiceAuthenticationToken.TokenType.ACCESS);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenService.findActiveAccessToken(TOKEN)).thenReturn(Optional.of(session));
    }

    private void givenUser(boolean enabled, boolean nonLocked) {
        AppSelfServiceUser user = mock(AppSelfServiceUser.class);
        when(user.getUsername()).thenReturn(USERNAME);
        when(user.isEnabled()).thenReturn(enabled);
        when(user.isAccountNonLocked()).thenReturn(nonLocked);
        when(user.isAccountNonExpired()).thenReturn(true);
        when(user.isCredentialsNonExpired()).thenReturn(true);
        when(user.getAuthorities()).thenReturn((java.util.Collection) List.of());
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(user);
    }

    private SelfServiceTokenAuthenticationToken tokenAuth() {
        return new SelfServiceTokenAuthenticationToken(TOKEN);
    }
}
