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
package org.apache.fineract.selfservice.signup.service;

import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/**
 * Phone and email helpers. Phone numbers are stored in many formats (0785 154 434, 256785154434, +256 785 154 434), so
 * two numbers are the same when their last nine digits are equal.
 */
public final class SelfServiceContactFormats {

    static final int SIGNIFICANT_DIGITS = 9;

    private SelfServiceContactFormats() {}

    /** The last nine digits, or null when the value has fewer than nine digits. */
    public static String phoneKey(final String phone) {
        if (phone == null) {
            return null;
        }
        final String digits = phone.replaceAll("\\D", "");
        return digits.length() < SIGNIFICANT_DIGITS ? null : digits.substring(digits.length() - SIGNIFICANT_DIGITS);
    }

    public static boolean samePhone(final String a, final String b) {
        final String keyA = phoneKey(a);
        return keyA != null && keyA.equals(phoneKey(b));
    }

    /** Only digits, spaces, dashes, brackets and a leading +, with at least nine digits. */
    public static boolean looksLikePhone(final String value) {
        return value != null && value.trim().matches("\\+?[\\d\\s()-]+") && phoneKey(value) != null;
    }

    public static boolean looksLikeEmail(final String value) {
        return value != null && value.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }

    public static String normaliseEmail(final String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The number for the SMS gateway in international format, digits only: 0785154434 or 785154434 becomes 256785154434
     * with country code 256, 00256... drops the 00, and +256 785 154 434 drops the + and spaces.
     */
    public static String smsNumber(final String phone, final String countryCode) {
        if (phone == null) {
            return null;
        }
        final String digits = phone.replaceAll("\\D", "");
        final String code = countryCode == null ? "" : countryCode.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            return digits.substring(2);
        }
        if (code.isEmpty() || digits.startsWith(code) && digits.length() > SIGNIFICANT_DIGITS) {
            return digits;
        }
        if (digits.startsWith("0")) {
            return code + digits.substring(1);
        }
        return digits.length() == SIGNIFICANT_DIGITS ? code + digits : digits;
    }

    /** 2567•••••434: shows enough for the member to recognise their number and no more. */
    public static String maskPhone(final String phone) {
        final String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "•••";
        }
        final int shownStart = digits.length() >= 10 ? 4 : 2;
        return digits.substring(0, shownStart) + "•".repeat(digits.length() - shownStart - 3) + digits.substring(digits.length() - 3);
    }

    /** j•••@gmail.com */
    public static String maskEmail(final String email) {
        if (StringUtils.isBlank(email) || !email.contains("@")) {
            return "•••";
        }
        final int at = email.indexOf('@');
        return email.charAt(0) + "•••" + email.substring(at);
    }
}
