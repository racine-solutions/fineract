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
package org.apache.fineract.racine.campaigns.domain;

import java.util.EnumSet;
import java.util.Set;

/** Why a message failed or was skipped, and whether retrying it can help. */
public enum DeliveryReason {

    NO_PHONE(true), INVALID_PHONE(true), PHONE_SHARED(false), OPTED_OUT(false), NO_SMS_CREDIT(true), SMS_OFF(true), SMS_ACCOUNT_INACTIVE(
            true), GATEWAY_REFUSED(true), SEND_ERROR(true), STOPPED(false),

    // Email
    NO_EMAIL(true), INVALID_EMAIL(true), EMAIL_SHARED(false), EMAIL_OFF(true), EMAIL_REFUSED(true), HARD_BOUNCE(true), SOFT_BOUNCE(
            true), BLOCKED(true), SPAM(false);

    /** Skip reasons a retry checks again, since staff may have fixed the client's phone or email since. */
    public static final Set<DeliveryReason> RECHECKED_WHEN_SKIPPED = EnumSet.of(NO_PHONE, INVALID_PHONE, NO_EMAIL, INVALID_EMAIL);

    private final boolean retryable;

    DeliveryReason(final boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * True when a retry can succeed: straight away, or once the client record or the SMS account is fixed (a retry
     * reads the client's current phone or email). A client who marked a campaign email as spam is not emailed again by
     * a retry.
     */
    public boolean isRetryable() {
        return retryable;
    }
}
