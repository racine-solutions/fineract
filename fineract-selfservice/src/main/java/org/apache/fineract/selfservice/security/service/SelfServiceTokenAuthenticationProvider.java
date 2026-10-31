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

import org.apache.fineract.selfservice.security.filter.SelfServiceTokenAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

public class SelfServiceTokenAuthenticationProvider implements AuthenticationProvider {

    private final SelfServiceAuthenticationTokenService tokenService;
    private final UserDetailsService userDetailsService;

    public SelfServiceTokenAuthenticationProvider(SelfServiceAuthenticationTokenService tokenService,
            UserDetailsService userDetailsService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        SelfServiceTokenAuthenticationToken authToken = (SelfServiceTokenAuthenticationToken) authentication;
        String token = (String) authToken.getCredentials();

        // 1. Validate it's an ACCESS token
        String username = tokenService.getUsernameForAccessToken(token);
        if (username == null) {
            throw new BadCredentialsException("Invalid or expired access token");
        }

        // 2. Sliding Expiration: Extend the token life on every successful request
        // This ensures the current frontend never gets logged out while active
        tokenService.extendAccessTokenExpiry(token, 7);

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

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
