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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.service.SelfServicePluginEmailService;
import org.apache.fineract.notification.data.SmsMessageData;
import org.apache.fineract.notification.data.SmsTypeEnum;
import org.apache.fineract.notification.service.SmsNotificationWritePlatformService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Sends a one-time code by SMS or email and reports which one worked. Callers pass only contact details taken from the
 * bank's records (client record or existing app user), never details typed on the request, so a code cannot be sent to
 * someone else's phone or inbox. The code is never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfServiceCodeSender {

    public enum Channel {

        SMS, EMAIL;

        public static Channel parse(final String value, final Channel fallback) {
            if (StringUtils.isBlank(value)) {
                return fallback;
            }
            try {
                return Channel.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("channel must be SMS or EMAIL");
            }
        }
    }

    public enum Purpose {

        SIGNUP("mobile app sign-up", "Your mobile app sign-up code"), PASSWORD_RESET("password reset", "Your password reset code");

        private final String smsName;
        private final String emailSubject;

        Purpose(final String smsName, final String emailSubject) {
            this.smsName = smsName;
            this.emailSubject = emailSubject;
        }
    }

    /** Where the code went: the channel and the masked phone or email to show the member. */
    public record Delivery(Channel channel, String destination, boolean smsFailed) {
    }

    static final String APP_HASH_PATTERN = "[A-Za-z0-9+/]{11}";

    private final SmsNotificationWritePlatformService smsService;
    private final SelfServicePluginEmailService emailService;
    private final ConfigurationDomainService configurationDomainService;
    private final Environment environment;

    /**
     * Tries the preferred channel first and the other one when it fails or is not available.
     *
     * @return where the code went, or null when neither channel worked
     */
    public Delivery send(final Purpose purpose, final String code, final String phone, final String email, final String recipientName,
            final Channel preferred, final int validMinutes) {
        boolean smsFailed = false;
        if (preferred == Channel.SMS) {
            if (StringUtils.isNotBlank(phone)) {
                if (sendSms(purpose, code, phone, validMinutes)) {
                    return new Delivery(Channel.SMS, SelfServiceContactFormats.maskPhone(phone), false);
                }
                smsFailed = true;
            }
            if (StringUtils.isNotBlank(email) && sendEmail(purpose, code, email, recipientName, validMinutes)) {
                return new Delivery(Channel.EMAIL, SelfServiceContactFormats.maskEmail(email), smsFailed);
            }
            return null;
        }
        if (StringUtils.isNotBlank(email) && sendEmail(purpose, code, email, recipientName, validMinutes)) {
            return new Delivery(Channel.EMAIL, SelfServiceContactFormats.maskEmail(email), false);
        }
        if (StringUtils.isNotBlank(phone) && sendSms(purpose, code, phone, validMinutes)) {
            return new Delivery(Channel.SMS, SelfServiceContactFormats.maskPhone(phone), false);
        }
        return null;
    }

    String smsText(final Purpose purpose, final String code, final int validMinutes) {
        final StringBuilder text = new StringBuilder("Your ").append(purpose.smsName).append(" code is ").append(code)
                .append(". It expires in ").append(validMinutes).append(" minutes. Never share it with anyone, including staff.");
        final String appHash = appHash();
        if (appHash != null) {
            // Lets the Android app read the code from this SMS by itself (SMS Retriever API)
            text.append('\n').append(appHash);
        }
        return text.toString();
    }

    private boolean sendSms(final Purpose purpose, final String code, final String phone, final int validMinutes) {
        try {
            final boolean sent = smsService.sendSmsNow(new SmsMessageData(SelfServiceContactFormats.smsNumber(phone, countryCode()),
                    smsText(purpose, code, validMinutes), null), SmsTypeEnum.SELF_SERVICE);
            if (!sent) {
                log.warn("{} code SMS to {} was not accepted", purpose, SelfServiceContactFormats.maskPhone(phone));
            }
            return sent;
        } catch (RuntimeException e) {
            log.warn("{} code SMS to {} failed: {}", purpose, SelfServiceContactFormats.maskPhone(phone), e.getMessage());
            return false;
        }
    }

    private boolean sendEmail(final Purpose purpose, final String code, final String email, final String recipientName,
            final int validMinutes) {
        final String name = StringUtils.defaultIfBlank(recipientName, "member");
        final String body = "<p>Hello " + escape(name) + ",</p>" + "<p>Your " + purpose.smsName + " code is:</p>"
                + "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px\">" + code + "</p>" + "<p>It expires in " + validMinutes
                + " minutes. Never share it with anyone, including staff.</p>"
                + "<p>If you did not ask for this code, you can ignore this email; nothing changes without it.</p>";
        try {
            emailService.sendFormattedEmail(new EmailDetail(purpose.emailSubject, body, email, name));
            return true;
        } catch (RuntimeException e) {
            log.warn("{} code email to {} failed: {}", purpose, SelfServiceContactFormats.maskEmail(email), e.getMessage());
            return false;
        }
    }

    private String countryCode() {
        return environment.getProperty("sms.default.country.code", "256");
    }

    /**
     * The Android app hashes to end the SMS with, separated by spaces. The setting may list several (comma separated),
     * one per build: debug and release apps are signed with different keys. Values that are not 11-character hashes are
     * skipped.
     */
    String appHash() {
        final String value;
        try {
            value = configurationDomainService.retrieveSelfServiceSmsAppHash();
        } catch (RuntimeException e) {
            return null;
        }
        if (StringUtils.isBlank(value)) {
            return null;
        }
        final String hashes = java.util.Arrays.stream(value.split("[,;\\s]+")).map(String::trim)
                .filter(hash -> hash.matches(APP_HASH_PATTERN)).distinct().collect(java.util.stream.Collectors.joining(" "));
        return hashes.isEmpty() ? null : hashes;
    }

    private static String escape(final String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
