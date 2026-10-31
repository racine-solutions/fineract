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

import org.apache.fineract.selfservice.security.domain.SelfServiceAuthenticationToken;
import org.apache.fineract.selfservice.security.filter.SelfServiceTokenAuthenticationToken;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Authenticates self-service requests that carry a session (access) token.
 *
 * <p>
 * The user's account status is checked on every request, with the same rules as login, so a user who is disabled,
 * locked or expired is stopped immediately even while logged in; all of that user's sessions are then ended. On success
 * the session's inactivity timeout is extended.
 */
public class SelfServiceTokenAuthenticationProvider implements AuthenticationProvider {

    private final SelfServiceAuthenticationTokenService tokenService;
    private final UserDetailsService userDetailsService;
    private final UserDetailsChecker userDetailsChecker;

    public SelfServiceTokenAuthenticationProvider(SelfServiceAuthenticationTokenService tokenService, UserDetailsService userDetailsService,
            UserDetailsChecker userDetailsChecker) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
        this.userDetailsChecker = userDetailsChecker;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        SelfServiceTokenAuthenticationToken authToken = (SelfServiceTokenAuthenticationToken) authentication;
        String token = (String) authToken.getCredentials();

        // 1. The token must be an active (not expired, not revoked) ACCESS token
        SelfServiceAuthenticationToken session = tokenService.findActiveAccessToken(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired access token"));

        // 2. The account must still be allowed to use the app (same checks as login)
        UserDetails userDetails = userDetailsService.loadUserByUsername(session.getUsername());
        try {
            userDetailsChecker.check(userDetails);
        } catch (AccountStatusException e) {
            tokenService.revokeAllSessions(session.getUserId());
            throw e;
        }

        // 3. Activity: push the inactivity timeout forward
        tokenService.extendSession(session.getUserId());

        UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(),
                userDetails.getAuthorities());
        result.setDetails(authToken.getDetails());
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return SelfServiceTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
