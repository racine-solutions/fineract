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

import java.security.SecureRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 6-digit one-time codes for sign-up and forgot password. Only a bcrypt hash of the code is stored, so a database read
 * does not reveal live codes, and a code is always checked against one request: a code can never be looked up on its
 * own (six digits repeat across members).
 */
public final class SelfServiceVerificationCode {

    public static final int LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    private SelfServiceVerificationCode() {}

    /** A random code from 000000 to 999999. */
    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static String hash(final String code) {
        return ENCODER.encode(code);
    }

    /** True when the typed code matches the stored hash. Spaces and dashes the member typed are ignored. */
    public static boolean matches(final String typedCode, final String storedHash) {
        if (typedCode == null || storedHash == null) {
            return false;
        }
        final String cleaned = typedCode.replaceAll("[\\s-]", "");
        if (cleaned.length() != LENGTH || !cleaned.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return ENCODER.matches(cleaned, storedHash);
    }
}
