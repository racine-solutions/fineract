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
package org.apache.fineract.racine.campaigns.service;

import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/** Checks client email addresses before a campaign email goes to them. */
public final class EmailAddresses {

    /** One address: something@domain.tld, no spaces, no second @. */
    private static final Pattern VALID = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[A-Za-z]{2,}$");

    private EmailAddresses() {}

    /** The trimmed address when it looks deliverable, else null. */
    public static String clean(final String raw) {
        final String trimmed = StringUtils.trimToNull(raw);
        return trimmed != null && trimmed.length() <= 150 && VALID.matcher(trimmed).matches() ? trimmed : null;
    }

    /** Case-insensitive form, to spot two clients sharing one address. */
    public static String key(final String raw) {
        final String clean = clean(raw);
        return clean == null ? null : clean.toLowerCase(Locale.ROOT);
    }
}
