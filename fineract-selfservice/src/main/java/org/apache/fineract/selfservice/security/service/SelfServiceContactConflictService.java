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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.selfservice.audit.data.SelfServiceEventEntry;
import org.apache.fineract.selfservice.audit.service.SelfServiceEventRecorder;
import org.apache.fineract.selfservice.security.exception.SelfServiceContactConflictException;
import org.apache.fineract.selfservice.signup.service.SelfServiceContactFormats;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Blocks the mobile app for a member whose phone number or email is also on another client's record (or on another app
 * user), so one member can never receive codes for, or log in to, another member's data. Nothing is fixed
 * automatically: staff correct the client records, and access works again on its own.
 *
 * <p>
 * The member is told plainly why and gets a reference to give the SACCO. The reference and the clashing clients are
 * written to the app activity log for staff; the member never sees the other member's details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfServiceContactConflictService {

    public static final String EVENT_TYPE = "CONTACT_CONFLICT";

    /** One of the member's own contact details and the clients it was also found on. */
    record Conflict(Long clientId, String accountNumber, Set<Long> otherClientIds, List<String> otherAccountNumbers,
            List<Long> otherAppUserIds) {

        boolean exists() {
            return !otherClientIds.isEmpty() || !otherAppUserIds.isEmpty();
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final SelfServiceSupportContacts supportContacts;
    private final SelfServiceEventRecorder eventRecorder;

    /**
     * Before sign-up: the client's phone and email, and the email the member typed (their future username), must not be
     * on any other client or app user.
     */
    public void checkSignup(final Long clientId, final String accountNumber, final String phone, final String clientEmail,
            final String typedEmail) {
        final Conflict conflict = find(clientId, accountNumber, phone, emails(clientEmail, typedEmail), null);
        if (conflict.exists()) {
            throw block(conflict, null, typedEmail, "sign-up");
        }
    }

    /** Before login and on every request: the app user's linked client and own email/username must be unique. */
    public void checkUser(final AppSelfServiceUser user) {
        if (user == null || user.getId() == null) {
            return;
        }
        final Conflict conflict = findForUser(user);
        if (conflict.exists()) {
            throw block(conflict, user.getId(), user.getUsername(), "login");
        }
    }

    /** True when the user would be blocked; records nothing (for answers that must stay neutral). */
    public boolean hasConflict(final AppSelfServiceUser user) {
        return user != null && user.getId() != null && findForUser(user).exists();
    }

    private Conflict findForUser(final AppSelfServiceUser user) {
        final List<Map<String, Object>> linked = jdbcTemplate
                .queryForList("select c.id, c.account_no, c.mobile_no, c.email_address from m_selfservice_user_client_mapping m"
                        + " join m_client c on c.id = m.client_id where m.appuser_id = ?", user.getId());
        final Map<String, Object> client = linked.isEmpty() ? Map.of() : linked.get(0);
        final Long clientId = client.get("id") instanceof Number number ? number.longValue() : null;
        final Set<String> emails = emails((String) client.get("email_address"), user.getEmail(),
                SelfServiceContactFormats.looksLikeEmail(user.getUsername()) ? user.getUsername() : null);
        return find(clientId, (String) client.get("account_no"), (String) client.get("mobile_no"), emails, user.getId());
    }

    Conflict find(final Long clientId, final String accountNumber, final String phone, final Set<String> emails, final Long appUserId) {
        final Set<Long> otherClientIds = new LinkedHashSet<>();
        final List<String> otherAccounts = new ArrayList<>();
        final long ownClient = clientId == null ? -1L : clientId;

        final String phoneKey = SelfServiceContactFormats.phoneKey(phone);
        if (phoneKey != null) {
            jdbcTemplate.query("select id, account_no, mobile_no from m_client where id <> ? and mobile_no like ?", rs -> {
                if (phoneKey.equals(SelfServiceContactFormats.phoneKey(rs.getString("mobile_no")))
                        && otherClientIds.add(rs.getLong("id"))) {
                    otherAccounts.add(rs.getString("account_no"));
                }
            }, ownClient, "%" + phoneKey.substring(phoneKey.length() - 3));
        }
        for (String email : emails) {
            jdbcTemplate.query("select id, account_no from m_client where id <> ? and lower(trim(email_address)) = ?", rs -> {
                if (otherClientIds.add(rs.getLong("id"))) {
                    otherAccounts.add(rs.getString("account_no"));
                }
            }, ownClient, email);
        }

        // Another app user (not linked to this client) using one of these emails as username or email
        final List<Long> otherUsers = new ArrayList<>();
        final long ownUser = appUserId == null ? -1L : appUserId;
        for (String email : emails) {
            otherUsers.addAll(jdbcTemplate.queryForList("select u.id from m_appselfservice_user u where u.is_deleted = false and u.id <> ?"
                    + " and (lower(u.username) = ? or lower(trim(u.email)) = ?)"
                    + " and not exists (select 1 from m_selfservice_user_client_mapping m where m.appuser_id = u.id and m.client_id = ?)",
                    Long.class, ownUser, email, email, ownClient));
        }
        return new Conflict(clientId, accountNumber, otherClientIds, otherAccounts, otherUsers.stream().distinct().toList());
    }

    private SelfServiceContactConflictException block(final Conflict conflict, final Long appUserId, final String username,
            final String during) {
        final String reference = reference(conflict, appUserId);
        log.warn("Mobile app {} blocked for client {} / app user {}: contact details also on clients {} and app users {} (ref {})", during,
                conflict.clientId(), appUserId, conflict.otherClientIds(), conflict.otherAppUserIds(), reference);
        eventRecorder
                .record(SelfServiceEventEntry.builder().appUserId(appUserId).username(username).clientId(conflict.clientId())
                        .eventType(EVENT_TYPE).category(SelfServiceEventEntry.CATEGORY_ACCESS).result(SelfServiceEventEntry.RESULT_REFUSED)
                        .actor(SelfServiceEventEntry.ACTOR_CLIENT).description("Blocked at " + during + " (ref " + reference
                                + "): phone or email also on " + describe(conflict) + ". Correct the client records to restore access.")
                        .build());
        return new SelfServiceContactConflictException("Your phone number or email is also on another member's record, so the mobile app"
                + " is blocked to protect your account. Please contact your SACCO and give them reference " + reference + ". "
                + StringUtils.capitalize(supportContacts.callInstruction()) + ".", reference, supportContacts.phoneNumbers());
    }

    /** Stable for the same member, so it reads the same each time: DUP-{account no} or DUP-U{app user id}. */
    static String reference(final Conflict conflict, final Long appUserId) {
        if (StringUtils.isNotBlank(conflict.accountNumber())) {
            return "DUP-" + conflict.accountNumber();
        }
        return conflict.clientId() != null ? "DUP-C" + conflict.clientId() : "DUP-U" + appUserId;
    }

    private static String describe(final Conflict conflict) {
        final List<String> parts = new ArrayList<>();
        if (!conflict.otherAccountNumbers().isEmpty()) {
            parts.add("client account(s) " + String.join(", ", conflict.otherAccountNumbers()));
        }
        if (!conflict.otherAppUserIds().isEmpty()) {
            parts.add("app user id(s) " + conflict.otherAppUserIds());
        }
        return String.join(" and ", parts);
    }

    private static Set<String> emails(final String... values) {
        final Set<String> emails = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                emails.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return emails;
    }
}
