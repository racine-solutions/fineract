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
package org.apache.fineract.racine.officerapp.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compares app versions such as 2026.12.1 or 2025.12.2-beta.0.18 by their leading dotted numbers. */
public final class OfficerAppVersion {

    private static final Pattern LEADING_NUMBERS = Pattern.compile("^\\s*v?(\\d+(?:\\.\\d+)*)");

    private OfficerAppVersion() {}

    /**
     * True when {@code version} is older than {@code minimum}. Unknown versions count as older; no minimum means none.
     */
    public static boolean isOlder(final String version, final String minimum) {
        final int[] min = parse(minimum);
        if (min.length == 0) {
            return false;
        }
        final int[] actual = parse(version);
        if (actual.length == 0) {
            return true;
        }
        for (int i = 0; i < Math.max(min.length, actual.length); i++) {
            final int a = i < actual.length ? actual[i] : 0;
            final int m = i < min.length ? min[i] : 0;
            if (a != m) {
                return a < m;
            }
        }
        return false;
    }

    static int[] parse(final String version) {
        if (version == null) {
            return new int[0];
        }
        final Matcher matcher = LEADING_NUMBERS.matcher(version);
        if (!matcher.find()) {
            return new int[0];
        }
        final String[] parts = matcher.group(1).split("\\.");
        final int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                numbers[i] = Integer.MAX_VALUE;
            }
        }
        return numbers;
    }
}
