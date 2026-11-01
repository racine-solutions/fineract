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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.selfservice.signup.exception.SelfServiceCodeException;
import org.apache.fineract.selfservice.signup.service.SelfServiceContactFormats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Finds the app user a member means when they log in or reset their password with their username, email or phone.
 *
 * <p>
 * A phone number or email only counts when it points to exactly one app user; when it fits more than one, the member is
 * asked to use their username, so nobody is ever sent into another member's account. Phone numbers are matched on the
 * phone of the linked client record, by their last nine digits (0785 154 434 = 256785154434).
 */
@Service
@RequiredArgsConstructor
public class SelfServiceUserIdentifierResolver {

    public record Match(Long userId, String username) {
    }

    private final JdbcTemplate jdbcTemplate;

    /**
     * The username to log in with. An exact username wins; otherwise a unique email or phone match. Anything else is
     * returned unchanged, so the normal "wrong username or password" answer follows.
     *
     * @throws SelfServiceCodeException
     *             401 when the email or phone fits more than one app user
     */
    public String resolveLoginUsername(final String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return identifier;
        }
        final String trimmed = identifier.trim();
        final List<String> exact = jdbcTemplate.queryForList(
                "select username from m_appselfservice_user where username = ? and is_deleted = false", String.class, trimmed);
        if (exact.size() == 1) {
            return exact.get(0);
        }
        final List<Match> matches = candidates(trimmed);
        if (matches.size() > 1) {
            throw ambiguous(trimmed);
        }
        return matches.size() == 1 ? matches.get(0).username() : trimmed;
    }

    /** Every non-deleted app user the identifier could mean: by username, email, or linked client phone. */
    public List<Match> candidates(final String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return List.of();
        }
        final String trimmed = identifier.trim();
        if (SelfServiceContactFormats.looksLikeEmail(trimmed)) {
            final String email = trimmed.toLowerCase(Locale.ROOT);
            return distinct(jdbcTemplate.query(
                    "select id, username from m_appselfservice_user where is_deleted = false and (lower(username) = ? or lower(email) = ?)",
                    (rs, i) -> new Match(rs.getLong("id"), rs.getString("username")), email, email));
        }
        if (SelfServiceContactFormats.looksLikePhone(trimmed)) {
            final String key = SelfServiceContactFormats.phoneKey(trimmed);
            final List<Match> matches = new ArrayList<>();
            jdbcTemplate.query("select u.id, u.username, c.mobile_no from m_appselfservice_user u"
                    + " join m_selfservice_user_client_mapping m on m.appuser_id = u.id join m_client c on c.id = m.client_id"
                    + " where u.is_deleted = false and c.mobile_no like ?", rs -> {
                        if (key.equals(SelfServiceContactFormats.phoneKey(rs.getString("mobile_no")))) {
                            matches.add(new Match(rs.getLong("id"), rs.getString("username")));
                        }
                    }, "%" + key.substring(key.length() - 3));
            return distinct(matches);
        }
        return jdbcTemplate.query("select id, username from m_appselfservice_user where is_deleted = false and username = ?",
                (rs, i) -> new Match(rs.getLong("id"), rs.getString("username")), trimmed);
    }

    private static List<Match> distinct(final List<Match> matches) {
        final Map<Long, Match> byId = new LinkedHashMap<>();
        matches.forEach(match -> byId.putIfAbsent(match.userId(), match));
        return new ArrayList<>(byId.values());
    }

    private static SelfServiceCodeException ambiguous(final String identifier) {
        final String what = SelfServiceContactFormats.looksLikePhone(identifier) ? "phone number" : "email";
        return new SelfServiceCodeException(401, "error.msg.login.identifier.ambiguous",
                "This " + what + " is linked to more than one account. Log in with your username instead.");
    }
}
