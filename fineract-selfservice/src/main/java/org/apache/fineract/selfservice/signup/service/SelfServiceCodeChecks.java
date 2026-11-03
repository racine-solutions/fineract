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

import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.CODE_VALIDITY;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.MAX_ATTEMPTS;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.MAX_SENDS_PER_WINDOW;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.RESEND_COOLDOWN;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.SEND_WINDOW;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistration;
import org.apache.fineract.selfservice.signup.exception.SelfServiceCodeException;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;

/** Checks shared by sign-up and forgot password: resend wait, hourly limit and code checking. */
final class SelfServiceCodeChecks {

    private SelfServiceCodeChecks() {}

    /** Refuses a send within the cooldown of the last one, or past the hourly limit across the given requests. */
    static void checkCanSend(final List<SelfServiceRegistration> recentRequests, final LocalDateTime now) {
        LocalDateTime lastSent = null;
        LocalDateTime oldestWindowEnd = null;
        int sends = 0;
        for (SelfServiceRegistration request : recentRequests) {
            if (request.getLastSentOn() != null && (lastSent == null || request.getLastSentOn().isAfter(lastSent))) {
                lastSent = request.getLastSentOn();
            }
            final int inWindow = request.sendsInWindow(now, SEND_WINDOW);
            if (inWindow > 0) {
                sends += inWindow;
                final LocalDateTime windowEnd = request.getSendWindowStart().plus(SEND_WINDOW);
                if (oldestWindowEnd == null || windowEnd.isBefore(oldestWindowEnd)) {
                    oldestWindowEnd = windowEnd;
                }
            }
        }
        if (lastSent != null && now.isBefore(lastSent.plus(RESEND_COOLDOWN))) {
            final long wait = secondsBetween(now, lastSent.plus(RESEND_COOLDOWN));
            throw new SelfServiceCodeException(429, "error.msg.code.resend.too.soon",
                    "Please wait " + wait + " seconds before asking for another code.", Map.of("retryAfterSeconds", wait));
        }
        if (sends >= MAX_SENDS_PER_WINDOW) {
            final long wait = oldestWindowEnd == null ? SEND_WINDOW.toSeconds() : secondsBetween(now, oldestWindowEnd);
            throw new SelfServiceCodeException(429, "error.msg.code.send.limit",
                    "Too many codes were requested. Try again in " + Math.max(1, wait / 60) + " minutes.",
                    Map.of("retryAfterSeconds", wait));
        }
    }

    /**
     * Checks the typed code against the request. A wrong code uses up a try; at the limit the code is thrown away.
     *
     * @param showTriesLeft
     *            false for forgot password, where the answer must not differ from a made-up request
     */
    static void checkCode(final SelfServiceRegistration request, final String typedCode, final LocalDateTime now,
            final boolean showTriesLeft) {
        if (!request.hasUsableCode(now, MAX_ATTEMPTS)) {
            throw expired();
        }
        if (SelfServiceVerificationCode.matches(typedCode, request.getAuthenticationToken())) {
            return;
        }
        final int left = request.recordFailedAttempt(MAX_ATTEMPTS);
        if (left == 0) {
            throw expired();
        }
        if (showTriesLeft) {
            throw new SelfServiceCodeException(400, "error.msg.code.invalid",
                    "That code is wrong. " + left + (left == 1 ? " try" : " tries") + " left.", Map.of("attemptsLeft", left));
        }
        throw new SelfServiceCodeException(400, "error.msg.code.invalid", "That code is wrong or has expired.");
    }

    static SelfServiceCodeException expired() {
        return new SelfServiceCodeException(410, "error.msg.code.expired",
                "This code has expired or was entered wrongly too many times. Send a new code.");
    }

    static SelfServiceCodeException deliveryFailed(final SelfServiceSupportContacts supportContacts, final Map<String, Object> extra) {
        final Map<String, Object> body = new LinkedHashMap<>(extra);
        body.put("supportPhoneNumbers", supportContacts.phoneNumbers());
        return new SelfServiceCodeException(502, "error.msg.code.delivery.failed",
                "We could not send your code by SMS or email. Try again later or " + supportContacts.callInstruction() + ".", body);
    }

    /** Fields every "code sent" answer carries. */
    static Map<String, Object> codeSentFields(final SelfServiceCodeSender.Delivery delivery) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", delivery.channel().name());
        body.put("destination", delivery.destination());
        body.put("smsFailed", delivery.smsFailed());
        body.put("codeLength", SelfServiceVerificationCode.LENGTH);
        body.put("codeExpiresInSeconds", CODE_VALIDITY.toSeconds());
        body.put("resendAfterSeconds", RESEND_COOLDOWN.toSeconds());
        return body;
    }

    static long secondsBetween(final LocalDateTime from, final LocalDateTime to) {
        return Math.max(0, (Duration.between(from, to).toMillis() + 999) / 1000);
    }

    static Supplier<LocalDateTime> systemClock() {
        return org.apache.fineract.infrastructure.core.service.DateUtils::getLocalDateTimeOfSystem;
    }
}
