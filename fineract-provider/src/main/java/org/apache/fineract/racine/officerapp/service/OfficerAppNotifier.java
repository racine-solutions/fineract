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
 * Tells a staff user by email (their user email) and SMS (their staff mobile number) when an admin changes their field
 * officer app access. Sent once the change is saved; a message that can't be sent is logged and never undoes the
 * change.
 */
@Slf4j
@Service
public class OfficerAppNotifier {

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
     * @param change
     *            what changed, completing "your field officer app ...", e.g. "access has been turned off"
     */
    public void notifyAfterCommit(final Long userId, final String change, final String reason) {
        final Runnable send = () -> send(userId, change, reason);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void send(final Long userId, final String change, final String reason) {
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList("select u.firstname, u.lastname, u.email, st.mobile_no"
                + " from m_appuser u left join m_staff st on st.id = u.staff_id where u.id = ?", userId);
        if (rows.isEmpty()) {
            return;
        }
        final Map<String, Object> row = rows.get(0);
        final String firstname = StringUtils.defaultIfBlank((String) row.get("firstname"), "there");
        final String fullName = StringUtils.trim(firstname + " " + StringUtils.defaultString((String) row.get("lastname")));
        final String why = StringUtils.isBlank(reason) ? "" : " Reason: " + reason.trim();

        final String email = StringUtils.trimToNull((String) row.get("email"));
        if (email != null && email.contains("@")) {
            final String body = "Hello " + firstname + ",\n\nYour field officer app " + change + "." + why
                    + "\n\nYou can still sign in to the web app as usual. Contact your administrator if you have questions.";
            try {
                newTransaction.executeWithoutResult(
                        status -> emailService.sendDefinedEmail(new EmailDetail("Field officer app access", body, email, fullName)));
            } catch (RuntimeException e) {
                log.error("Could not email user {} about an officer app access change", userId, e);
            }
        }

        final String phone = PhoneNumbers.international((String) row.get("mobile_no"),
                environment.getProperty("sms.default.country.code", "256"));
        if (phone != null) {
            final String text = "Hello " + firstname + ", your field officer app " + change + "." + why + " The web app still works.";
            try {
                newTransaction.executeWithoutResult(status -> smsService
                        .sendSms(new SmsMessageData(phone, StringUtils.left(text, 300), null), SmsTypeEnum.OFFICER_APP));
            } catch (RuntimeException e) {
                log.error("Could not SMS user {} about an officer app access change", userId, e);
            }
        }
    }
}
