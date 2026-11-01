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
package org.apache.fineract.selfservice.security.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SelfServiceAuthenticationTokenRepository extends JpaRepository<SelfServiceAuthenticationToken, Long> {

    Optional<SelfServiceAuthenticationToken> findByTokenAndExpiresAtAfter(String token, LocalDateTime now);

    void deleteByUserId(Long userId);

    // Sliding inactivity timeout: push out the expiry of every token in the user's session
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SelfServiceAuthenticationToken t set t.expiresAt = :expiresAt where t.userId = :userId and t.expiresAt > :now")
    int extendActiveTokens(@Param("userId") Long userId, @Param("expiresAt") LocalDateTime expiresAt, @Param("now") LocalDateTime now);

    void deleteByExpiresAtBefore(LocalDateTime now);

    // Logout
    void deleteByToken(String token);

    // Update methods to filter by Token Type
    Optional<SelfServiceAuthenticationToken> findByTokenAndTokenTypeAndExpiresAtAfter(String token,
            SelfServiceAuthenticationToken.TokenType tokenType, LocalDateTime now);
}
