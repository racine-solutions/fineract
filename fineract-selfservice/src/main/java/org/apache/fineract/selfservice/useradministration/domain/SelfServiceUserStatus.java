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
package org.apache.fineract.selfservice.useradministration.domain;

import java.util.Arrays;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

/**
 * Back-office status of a self-service (mobile app) user. "Locked" is not a status: it is the separate
 * {@code nonlocked} flag, set after failed logins and cleared by staff with "unlock".
 */
public enum SelfServiceUserStatus {

    PENDING(100, "selfServiceUserStatus.pending", "Pending"), //
    ACTIVE(300, "selfServiceUserStatus.active", "Active"), //
    VIEW_ONLY(400, "selfServiceUserStatus.viewOnly", "View only"), //
    SUSPENDED(500, "selfServiceUserStatus.suspended", "Suspended"), //
    CLOSED(600, "selfServiceUserStatus.closed", "Closed");

    private final int value;
    private final String code;
    private final String label;

    SelfServiceUserStatus(final int value, final String code, final String label) {
        this.value = value;
        this.code = code;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getCode() {
        return code;
    }

    /** True when the user may log in (subject to the lock flag): Active or View only. */
    public boolean canLogin() {
        return this == ACTIVE || this == VIEW_ONLY;
    }

    public EnumOptionData toEnumOptionData() {
        return new EnumOptionData((long) value, code, label);
    }

    public static SelfServiceUserStatus fromInt(final Integer value) {
        if (value == null) {
            return ACTIVE;
        }
        return Arrays.stream(values()).filter(s -> s.value == value).findFirst().orElse(ACTIVE);
    }
}
