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
package org.apache.fineract.notification.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;

/**
 * Pieces shared by every SMS the platform sends, core and self-service, so they read the same and fit in one 160
 * character message.
 */
public final class SmsText {

    /** One standard SMS. */
    public static final int MAX_LENGTH = 160;
    /** Longest name an SMS greets with, so a long name cannot push a message past one SMS. */
    public static final int MAX_NAME_LENGTH = 20;

    /** The Android app hashes on the last line of a code SMS: they must stay last or the app cannot read the code. */
    private static final Pattern APP_HASH_TRAILER = Pattern.compile("\n[A-Za-z0-9+/]{11}( [A-Za-z0-9+/]{11})*$");

    private SmsText() {}

    /**
     * The last check before a message goes to an SMS provider. A message over {@link #MAX_LENGTH} loses its last
     * characters; one with room left is signed with the tenant's short name ("... Thank you! - Showroom"). An app hash
     * trailer always stays at the very end.
     */
    public static String fit(String message, String tenantShortName) {
        if (message == null) {
            return null;
        }
        String body = message;
        String trailer = "";
        Matcher hash = APP_HASH_TRAILER.matcher(message);
        if (hash.find()) {
            body = message.substring(0, hash.start());
            trailer = hash.group();
        }
        int room = Math.max(0, MAX_LENGTH - trailer.length());
        if (body.length() > room) {
            return cutAtWord(body, room) + trailer;
        }
        if (StringUtils.isNotBlank(tenantShortName)) {
            String signed = body + " - " + tenantShortName;
            if (signed.length() <= room) {
                body = signed;
            }
        }
        return body + trailer;
    }

    /**
     * Cuts after the last whole word that fits, dropping a dangling comma or colon. A single word longer than the room
     * (no space to cut at) is cut where the room ends.
     */
    static String cutAtWord(String text, int room) {
        String cut = text.substring(0, room);
        if (!Character.isWhitespace(text.charAt(room))) {
            int lastSpace = cut.length() - 1;
            while (lastSpace >= 0 && !Character.isWhitespace(cut.charAt(lastSpace))) {
                lastSpace--;
            }
            if (lastSpace > 0) {
                cut = cut.substring(0, lastSpace);
            }
        }
        return StringUtils.stripEnd(cut, " \t\n,;:-");
    }

    /** {@link #fit(String, String)} signed with the current tenant's short name. */
    public static String fit(String message) {
        return fit(message, currentTenantShortName());
    }

    /**
     * The first word of the current tenant's name from the tenants table: "Showroom Sacco Association" gives
     * "Showroom".
     *
     * @return null when there is no tenant on this thread or it has no name
     */
    public static String currentTenantShortName() {
        FineractPlatformTenant tenant;
        try {
            tenant = ThreadLocalContextUtil.getTenant();
        } catch (IllegalStateException e) {
            return null;
        }
        return tenant == null ? null : firstWord(tenant.getName());
    }

    static String firstWord(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        return name.trim().split("\\s+")[0];
    }

    /**
     * The one name an SMS greets the member by.
     *
     * @return the last name, else the first name, trimmed and cut to {@link #MAX_NAME_LENGTH}; null when both are blank
     */
    public static String recipientName(String lastname, String firstname) {
        if (StringUtils.isNotBlank(lastname)) {
            return shorten(lastname);
        }
        if (StringUtils.isNotBlank(firstname)) {
            return shorten(firstname);
        }
        return null;
    }

    public static String shorten(String name) {
        return StringUtils.truncate(name.trim(), MAX_NAME_LENGTH).trim();
    }

    /**
     * Keeps the first digit and the last two, e.g. 000000213 becomes 0****13, so the full account number never goes out
     * in an SMS.
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return accountNumber.charAt(0) + "****" + accountNumber.substring(accountNumber.length() - 2);
    }
}
