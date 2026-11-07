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

/** Phone numbers as EgoSMS wants them: digits only, with the country code. */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    /**
     * @return the number in international form (e.g. 256785154434), or null when it is blank or cannot be a phone
     *         number
     */
    public static String international(final String raw, final String countryCode) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 10 && digits.startsWith("0")) {
            digits = countryCode + digits.substring(1);
        } else if (digits.length() == 9) {
            digits = countryCode + digits;
        }
        return digits.length() >= 11 && digits.length() <= 15 ? digits : null;
    }

    public static boolean isBlank(final String raw) {
        return raw == null || raw.isBlank();
    }

    /** Last nine digits: the part that identifies a phone whatever prefix it was typed with. */
    public static String key(final String raw) {
        if (raw == null) {
            return null;
        }
        final String digits = raw.replaceAll("[^0-9]", "");
        return digits.length() < 9 ? null : digits.substring(digits.length() - 9);
    }
}
