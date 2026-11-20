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
package org.apache.fineract.racine.officerapp.service;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.security.service.LoginAttemptRecorder;
import org.apache.fineract.racine.officerapp.domain.OfficerAppEvent;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.service.AppUserConstants;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Locks a staff account after too many wrong passwords at sign-in (System › Configurations › max-login-retry-attempts),
 * for every staff user except the built-in system user, and tells the user straight away. The lock stops both the
 * web-app and the field officer app until an admin unlocks the account.
 */
@Slf4j
@Service
public class StaffAccountLock implements LoginAttemptRecorder {

    private static final List<String> USER_CACHES = List.of("users", "usersByUsername");

    private final JdbcTemplate jdbcTemplate;
    private final ConfigurationDomainService configurations;
    private final CacheManager cacheManager;
    private final OfficerAppNotifier notifier;
    private final OfficerAppEventRecorder recorder;
    private final TransactionTemplate newTransaction;

    public StaffAccountLock(final JdbcTemplate jdbcTemplate, final ConfigurationDomainService configurations,
            final CacheManager cacheManager, final OfficerAppNotifier notifier, final OfficerAppEventRecorder recorder,
            final PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.configurations = configurations;
        this.cacheManager = cacheManager;
        this.notifier = notifier;
        this.recorder = recorder;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void failed(final String username, final AuthenticationException reason) {
        // Only a wrong password counts; an account that is already locked or disabled is left alone
        if (!(reason instanceof BadCredentialsException) || username == null
                || AppUserConstants.SYSTEM_USER_NAME.equalsIgnoreCase(username.trim())) {
            return;
        }
        try {
            if (!configurations.isMaxLoginRetriesEnabled()) {
                return;
            }
            final Integer maxRetries = configurations.retrieveMaxLoginRetries();
            if (maxRetries == null || maxRetries <= 0) {
                return;
            }
            final Locked locked = newTransaction.execute(status -> countFailure(username.trim(), maxRetries));
            evictUserCaches();
            if (locked != null) {
                log.warn("Staff user {} locked after {} failed sign-in attempts", locked.userId(), locked.attempts());
                recorder.record(OfficerAppEvent.builder().appUserId(locked.userId()).username(username.trim()).eventType("ACCOUNT_LOCKED")
                        .category(OfficerAppEvent.CATEGORY_ACCESS).result(OfficerAppEvent.RESULT_SUCCESS)
                        .actor(OfficerAppEvent.ACTOR_SYSTEM)
                        .description("Account locked after " + locked.attempts() + " failed sign-in attempts").build());
                // Straight away, from this request: the change is already saved
                notifier.sendNow(locked.userId(), OfficerAppNotifier.accountLocked(locked.attempts()));
            }
        } catch (RuntimeException e) {
            // Never turn a wrong password into a server error
            log.error("Could not count the failed sign-in of {}", username, e);
        }
    }

    @Override
    public void succeeded(final AppUser user) {
        if (user == null || user.getFailedLoginAttempts() <= 0) {
            return;
        }
        try {
            newTransaction.executeWithoutResult(
                    status -> jdbcTemplate.update("update m_appuser set failed_login_attempts = 0 where id = ?", user.getId()));
            evictUserCaches();
        } catch (RuntimeException e) {
            log.error("Could not reset the failed sign-in count of user {}", user.getId(), e);
        }
    }

    /** Unlocks the account and clears the failed sign-in count. */
    public void unlock(final Long userId) {
        jdbcTemplate.update("update m_appuser set nonlocked = true, failed_login_attempts = 0 where id = ?", userId);
        evictUserCaches();
    }

    private record Locked(Long userId, int attempts) {
    }

    /** Adds one failed attempt; returns the user when this attempt locked the account. */
    private Locked countFailure(final String username, final int maxRetries) {
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, nonlocked, failed_login_attempts from m_appuser where username = ? and is_deleted = false", username);
        if (rows.isEmpty()) {
            return null;
        }
        final Map<String, Object> row = rows.get(0);
        final Long userId = ((Number) row.get("id")).longValue();
        if (!isTrue(row.get("nonlocked"))) {
            return null;
        }
        final int attempts = ((Number) row.get("failed_login_attempts")).intValue() + 1;
        if (attempts >= maxRetries) {
            jdbcTemplate.update("update m_appuser set nonlocked = false, failed_login_attempts = ? where id = ?", attempts, userId);
            return new Locked(userId, attempts);
        }
        jdbcTemplate.update("update m_appuser set failed_login_attempts = ? where id = ?", attempts, userId);
        return null;
    }

    private static boolean isTrue(final Object value) {
        return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() != 0;
    }

    private void evictUserCaches() {
        for (final String name : USER_CACHES) {
            final Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
