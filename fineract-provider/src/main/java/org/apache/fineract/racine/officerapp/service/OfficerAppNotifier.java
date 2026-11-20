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
package org.apache.fineract.racine.officerapp.service;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.notification.data.SmsMessageData;
import org.apache.fineract.notification.data.SmsTypeEnum;
import org.apache.fineract.notification.service.SmsNotificationWritePlatformService;
import org.apache.fineract.racine.campaigns.service.PhoneNumbers;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends a staff user (field officer) a message about their account or app access, straight away, from the request that
 * made the change. Email first (through Brevo when it is set up); an SMS through the SMS gateway only when the user has
 * no usable email address. A message that can't be sent is logged and never undoes the change.
 */
@Slf4j
@Service
public class OfficerAppNotifier {

    /** What to tell the officer. The SMS text is used only when there is no email address. */
    public record StaffMessage(String subject, String emailBody, String smsText) {
    }

    private final PlatformEmailService emailService;
    private final SmsNotificationWritePlatformService smsService;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final TransactionTemplate newTransaction;

    public OfficerAppNotifier(final PlatformEmailService emailService, final SmsNotificationWritePlatformService smsService,
            final JdbcTemplate jdbcTemplate, final Environment environment, final PlatformTransactionManager transactionManager) {
        this.emailService = emailService;
        this.smsService = smsService;
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Sends as soon as the change being made is saved: at once when there is no open transaction, otherwise right after
     * it commits (so nothing is sent for a change that fails).
     */
    public void sendAfterSave(final Long userId, final StaffMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    sendNow(userId, message);
                }
            });
        } else {
            sendNow(userId, message);
        }
    }

    /** Sends now. Returns "EMAIL", "SMS" or null (no email address and no mobile number, or sending failed). Never throws. */
    public String sendNow(final Long userId, final StaffMessage message) {
        try {
            // Own transaction: right after a commit the request's connection is already closed
            return newTransaction.execute(status -> send(userId, message));
        } catch (RuntimeException e) {
            // Never fails the action that was taken
            log.error("Could not send \"{}\" to staff user {}", message.subject(), userId, e);
            return null;
        }
    }

    private String send(final Long userId, final StaffMessage message) {
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList("select u.firstname, u.lastname, u.email, st.mobile_no"
                + " from m_appuser u left join m_staff st on st.id = u.staff_id where u.id = ?", userId);
        if (rows.isEmpty()) {
            return null;
        }
        final Map<String, Object> row = rows.get(0);
        final String firstname = StringUtils.defaultIfBlank((String) row.get("firstname"), "there");
        final String fullName = StringUtils.trim(firstname + " " + StringUtils.defaultString((String) row.get("lastname")));

        final String email = usableEmail((String) row.get("email"));
        if (email != null) {
            final String body = "Hello " + firstname + ",\n\n" + message.emailBody()
                    + "\n\nThis message was sent automatically. Contact your administrator if you have questions.";
            emailService.sendDefinedEmail(new EmailDetail(message.subject(), body, email, fullName));
            log.info("Told staff user {} by email: {}", userId, message.subject());
            return "EMAIL";
        }

        final String phone = PhoneNumbers.international((String) row.get("mobile_no"),
                environment.getProperty("sms.default.country.code", "256"));
        if (phone == null) {
            log.warn("Staff user {} has no email address or mobile number; not told: {}", userId, message.subject());
            return null;
        }
        smsService.sendSms(new SmsMessageData(phone, StringUtils.left("Hello " + firstname + ", " + message.smsText(), 300), null),
                SmsTypeEnum.OFFICER_APP);
        log.info("Told staff user {} by SMS (no email address): {}", userId, message.subject());
        return "SMS";
    }

    /** The address, or null when it is blank or can't be a real address. */
    static String usableEmail(final String raw) {
        final String email = StringUtils.trimToNull(raw);
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return null;
        }
        return email;
    }

    // ---- The messages ----

    static String withReason(final String text, final String reason) {
        return StringUtils.isBlank(reason) ? text : text + " Reason: " + reason.trim();
    }

    public static StaffMessage accessTurnedOff(final String reason) {
        return new StaffMessage("Your field officer app access has been turned off",
                withReason("An administrator has turned off your access to the field officer app.", reason)
                        + "\n\nYou can still sign in to the web app as usual.",
                withReason("your field officer app access has been turned off.", reason) + " The web app still works.");
    }

    public static StaffMessage accessTurnedOn(final String reason) {
        return new StaffMessage("Your field officer app access is back on",
                withReason("An administrator has turned your access to the field officer app back on. You can sign in again.", reason),
                "your field officer app access is back on. You can sign in again.");
    }

    public static StaffMessage deviceBlocked(final String device, final String reason) {
        return new StaffMessage("A device has been blocked",
                withReason("An administrator has blocked the field officer app on " + device + ".", reason)
                        + "\n\nYou can still sign in to the web app as usual.",
                withReason("the field officer app on " + device + " has been blocked.", reason));
    }

    public static StaffMessage signedOutByAdmin(final String where, final String reason) {
        return new StaffMessage("You have been signed out of the field officer app",
                withReason("An administrator signed you out of the field officer app on " + where + ". You can sign in again.", reason),
                withReason("an administrator signed you out of the field officer app on " + where + ".", reason));
    }

    public static StaffMessage accountLocked(final int attempts) {
        return new StaffMessage("Your account has been locked",
                "Your account was locked after " + attempts + " failed sign-in attempts. You can't sign in to the web app or the"
                        + " field officer app until an administrator unlocks it.\n\nIf you did not try to sign in, tell your"
                        + " administrator straight away: someone may be trying to use your account.",
                "your account was locked after " + attempts + " failed sign-in attempts. Ask your administrator to unlock it."
                        + " If this wasn't you, tell them straight away.");
    }

    public static StaffMessage accountUnlocked() {
        return new StaffMessage("Your account has been unlocked",
                "An administrator has unlocked your account. You can sign in to the web app and the field officer app again.",
                "your account has been unlocked. You can sign in again.");
    }

    public static StaffMessage newDeviceSignIn(final String device, final String deviceCode, final String when) {
        return new StaffMessage("New sign-in to the field officer app",
                "Your account was used to sign in to the field officer app on a device it hasn't been used on before:\n\n" + "Device: "
                        + device + " (Device ID " + deviceCode + ")\nTime: " + when
                        + "\n\nIf this was you, there is nothing to do. If it wasn't, tell your administrator straight away.",
                "your account signed in to the field officer app on a new device: " + device + " (" + deviceCode + ") at " + when
                        + ". If this wasn't you, tell your administrator.");
    }

    public static StaffMessage passwordChanged(final boolean byAdministrator) {
        final String who = byAdministrator ? "An administrator changed" : "You changed";
        return new StaffMessage("Your password has been changed",
                who + " the password of your account. Use the new password to sign in to the web app and the field officer app."
                        + "\n\nIf you did not expect this, tell your administrator straight away.",
                (byAdministrator ? "an administrator changed" : "you changed")
                        + " the password of your account. If you did not expect this, tell your administrator.");
    }
}
