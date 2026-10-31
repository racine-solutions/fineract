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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.selfservice.security.domain.SelfServiceAuthenticationToken;
import org.apache.fineract.selfservice.security.domain.SelfServiceAuthenticationTokenRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and validates self-service (mobile app) session tokens.
 *
 * <p>
 * A session is one access token and one refresh token. Both expire after the configured period of inactivity
 * ({@code self-service-session-timeout-minutes}, default 15): every authenticated request pushes both expiry times
 * forward, and once the session has been idle that long neither token works, so the client has to log in again. The
 * user's account itself is not changed.
 */
@Service
public class SelfServiceAuthenticationTokenService {

    private final SelfServiceAuthenticationTokenRepository repository;
    private final ConfigurationDomainService configurationDomainService;

    public record TokenPair(String accessToken, String refreshToken) {
    }

    public SelfServiceAuthenticationTokenService(SelfServiceAuthenticationTokenRepository repository,
            ConfigurationDomainService configurationDomainService) {
        this.repository = repository;
        this.configurationDomainService = configurationDomainService;
    }

    @Transactional
    public TokenPair generateTokens(Long userId, String username) {
        repository.deleteByUserId(userId); // single active session per user

        final LocalDateTime expiresAt = sessionExpiryFromNow();
        String accessToken = UUID.randomUUID().toString();
        repository.save(createTokenEntity(userId, username, accessToken, SelfServiceAuthenticationToken.TokenType.ACCESS, expiresAt));

        String refreshToken = UUID.randomUUID().toString();
        repository.save(createTokenEntity(userId, username, refreshToken, SelfServiceAuthenticationToken.TokenType.REFRESH, expiresAt));

        return new TokenPair(accessToken, refreshToken);
    }

    private SelfServiceAuthenticationToken createTokenEntity(Long userId, String username, String token,
            SelfServiceAuthenticationToken.TokenType type, LocalDateTime expiresAt) {
        SelfServiceAuthenticationToken entity = new SelfServiceAuthenticationToken();
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setToken(token);
        entity.setTokenType(type);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    /**
     * Returns the active (not expired) refresh token, so the caller can check the user's status before rotating it.
     */
    @Transactional(readOnly = true)
    public Optional<SelfServiceAuthenticationToken> findActiveRefreshToken(String rawRefreshToken) {
        return repository.findByTokenAndTokenTypeAndExpiresAtAfter(rawRefreshToken, SelfServiceAuthenticationToken.TokenType.REFRESH,
                LocalDateTime.now());
    }

    @Transactional
    public TokenPair refreshTokens(String rawRefreshToken) {
        SelfServiceAuthenticationToken refreshEntity = findActiveRefreshToken(rawRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        repository.deleteByUserId(refreshEntity.getUserId());
        return generateTokens(refreshEntity.getUserId(), refreshEntity.getUsername());
    }

    @Transactional(readOnly = true)
    public Optional<SelfServiceAuthenticationToken> findActiveAccessToken(String token) {
        return repository.findByTokenAndTokenTypeAndExpiresAtAfter(token, SelfServiceAuthenticationToken.TokenType.ACCESS,
                LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public String getUsernameForAccessToken(String token) {
        return findActiveAccessToken(token).map(SelfServiceAuthenticationToken::getUsername).orElse(null);
    }

    /**
     * Records activity on the user's session: pushes the expiry of the user's access and refresh tokens to the
     * configured inactivity timeout from now.
     */
    @Transactional
    public void extendSession(Long userId) {
        final LocalDateTime now = LocalDateTime.now();
        repository.extendActiveTokens(userId, sessionExpiryFromNow(), now);
    }

    /**
     * Ends every open session of the user (access and refresh tokens). Used when the user may no longer use the app.
     */
    @Transactional
    public void revokeAllSessions(Long userId) {
        repository.deleteByUserId(userId);
    }

    @Transactional
    public void invalidateToken(String token) {
        if (StringUtils.isNotBlank(token)) {
            repository.deleteByToken(token);
        }
    }

    private LocalDateTime sessionExpiryFromNow() {
        return LocalDateTime.now().plusMinutes(configurationDomainService.retrieveSelfServiceSessionTimeoutMinutes());
    }
}
