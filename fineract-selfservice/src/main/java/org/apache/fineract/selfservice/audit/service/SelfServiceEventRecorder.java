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
package org.apache.fineract.selfservice.audit.service;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.selfservice.audit.data.SelfServiceEventEntry;
import org.apache.fineract.selfservice.notification.SelfServiceNotificationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes the self-service audit trail (m_selfservice_event): what users did in the mobile app, what staff changed on
 * their access, and transfers the app refused.
 *
 * <p>
 * Recording never affects the action being recorded: each row is written in its own transaction (so it is kept when the
 * action fails and is rolled back) and any error is logged and swallowed. Secrets such as one-time codes are never
 * stored.
 */
@Slf4j
@Service
public class SelfServiceEventRecorder {

    static final int DESCRIPTION_MAX = 500;
    static final int DETAILS_MAX = 2000;
    static final int DETAIL_VALUE_MAX = 200;
    /** Context keys that hold secrets (one-time codes, tokens, passwords). Matched case-insensitively as substrings. */
    static final List<String> SECRET_KEY_PARTS = List.of("authcode", "otp", "token", "password", "secret", "pin");
    private static final List<String> AMOUNT_KEYS = List.of("transferAmount", "transactionAmount", "amount", "principal", "depositAmount");
    private static final List<String> CURRENCY_KEYS = List.of("currencyCode", "currency");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate newTransaction;

    public SelfServiceEventRecorder(final JdbcTemplate jdbcTemplate, final PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Adds a row to the audit trail. The user's client and office are filled in when not given. */
    public void record(final SelfServiceEventEntry entry) {
        try {
            newTransaction.executeWithoutResult(status -> insert(entry));
        } catch (RuntimeException e) {
            log.error("Could not record self-service event {} for user {}", entry.eventType(), entry.appUserId(), e);
        }
    }

    /**
     * Records app activity from the events the app already publishes for notifications. Access changes are skipped: the
     * back office records those itself, with the staff member and reason.
     */
    @Async("notificationExecutor")
    @EventListener
    public void onNotificationEvent(final SelfServiceNotificationEvent event) {
        if (event.getType() == SelfServiceNotificationEvent.Type.ACCESS_CHANGED) {
            return;
        }
        try {
            if (event.getTenant() != null) {
                ThreadLocalContextUtil.setTenant(event.getTenant());
            }
            if (event.getBusinessDates() != null) {
                ThreadLocalContextUtil.setBusinessDates(event.getBusinessDates());
            }
            record(fromNotificationEvent(event));
        } catch (RuntimeException e) {
            log.error("Could not record self-service event {}", event, e);
        } finally {
            ThreadLocalContextUtil.reset();
        }
    }

    static SelfServiceEventEntry fromNotificationEvent(final SelfServiceNotificationEvent event) {
        final Map<String, Object> context = event.getContextData();
        final SelfServiceNotificationEvent.Type type = event.getType();
        String description = describe(type);
        final Object beneficiary = context.get("beneficiaryName");
        if (beneficiary != null && StringUtils.isNotBlank(beneficiary.toString())) {
            description = description + ": " + beneficiary;
        }
        // The registration code is sent before the app user exists; that event carries the registration request id
        // instead
        final boolean beforeAppUserExists = type == SelfServiceNotificationEvent.Type.ENROLLMENT_TOKEN;
        final Object clientId = context.get("clientId");
        return SelfServiceEventEntry.builder().appUserId(beforeAppUserExists ? null : event.getUserId()).username(event.getUsername())
                .clientId(clientId instanceof Number number ? number.longValue() : null).eventType(type.name()).category(categoryOf(type))
                .result(type == SelfServiceNotificationEvent.Type.LOGIN_FAILURE ? SelfServiceEventEntry.RESULT_FAILED
                        : SelfServiceEventEntry.RESULT_SUCCESS)
                .actor(SelfServiceEventEntry.ACTOR_CLIENT).description(description).amount(amountOf(context))
                .currencyCode(firstText(context, CURRENCY_KEYS)).ipAddress(event.getIpAddress()).details(safeDetails(context)).build();
    }

    static String categoryOf(final SelfServiceNotificationEvent.Type type) {
        return switch (type) {
            case USER_CREATED, ENROLLMENT_TOKEN, USER_ACTIVATED -> "REGISTRATION";
            case LOGIN_SUCCESS, LOGIN_FAILURE -> "LOGIN";
            case PASSWORD_RESET_REQUESTED, PASSWORD_RENEWED -> "PASSWORD";
            case TRANSFER_OTP, TRANSFER_SUCCESS -> SelfServiceEventEntry.CATEGORY_TRANSFER;
            case BENEFICIARY_ADDED, BENEFICIARY_UPDATED, BENEFICIARY_DELETED -> "BENEFICIARY";
            case LOAN_REQUESTED, LOAN_UPDATED, LOAN_WITHDRAWN -> "LOAN";
            case SAVINGS_REQUESTED, SAVINGS_UPDATED, SAVINGS_WITHDRAWN -> "SAVINGS";
            case SINPE_ENROLLMENT_OTP, SINPE_ENROLLMENT_SUCCESS, SINPE_SUBSCRIPTION_CREATED, SINPE_SUBSCRIPTION_UPDATED,
                    SINPE_SUBSCRIPTION_DELETED ->
                "SINPE";
            case ACCESS_CHANGED -> SelfServiceEventEntry.CATEGORY_ACCESS;
        };
    }

    static String describe(final SelfServiceNotificationEvent.Type type) {
        return switch (type) {
            case USER_CREATED -> "Registered for the mobile app";
            case ENROLLMENT_TOKEN -> "Registration code sent";
            case USER_ACTIVATED -> "Registration confirmed";
            case LOGIN_SUCCESS -> "Logged in";
            case LOGIN_FAILURE -> "Failed login attempt";
            case PASSWORD_RESET_REQUESTED -> "Password reset requested";
            case PASSWORD_RENEWED -> "Password changed";
            case TRANSFER_OTP -> "Transfer code sent";
            case TRANSFER_SUCCESS -> "Transfer made";
            case BENEFICIARY_ADDED -> "Beneficiary added";
            case BENEFICIARY_UPDATED -> "Beneficiary changed";
            case BENEFICIARY_DELETED -> "Beneficiary removed";
            case LOAN_REQUESTED -> "Loan requested";
            case LOAN_UPDATED -> "Loan request changed";
            case LOAN_WITHDRAWN -> "Loan request withdrawn";
            case SAVINGS_REQUESTED -> "Savings account requested";
            case SAVINGS_UPDATED -> "Savings account request changed";
            case SAVINGS_WITHDRAWN -> "Savings account request withdrawn";
            case SINPE_ENROLLMENT_OTP -> "SINPE Móvil enrolment code sent";
            case SINPE_ENROLLMENT_SUCCESS -> "Enrolled in SINPE Móvil";
            case SINPE_SUBSCRIPTION_CREATED -> "SINPE Móvil subscription created";
            case SINPE_SUBSCRIPTION_UPDATED -> "SINPE Móvil subscription changed";
            case SINPE_SUBSCRIPTION_DELETED -> "SINPE Móvil subscription removed";
            case ACCESS_CHANGED -> "Access changed";
        };
    }

    /** Event context as JSON, without secrets and with only plain values (text, numbers, yes/no). */
    static String safeDetails(final Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        final Map<String, String> safe = new LinkedHashMap<>();
        context.forEach((key, value) -> {
            if (key == null || value == null || isSecret(key)
                    || !(value instanceof CharSequence || value instanceof Number || value instanceof Boolean)) {
                return;
            }
            safe.put(key, StringUtils.abbreviate(value.toString(), DETAIL_VALUE_MAX));
        });
        return safe.isEmpty() ? null : new Gson().toJson(safe);
    }

    static boolean isSecret(final String key) {
        final String lower = key.toLowerCase(Locale.ROOT);
        return SECRET_KEY_PARTS.stream().anyMatch(lower::contains);
    }

    private static BigDecimal amountOf(final Map<String, Object> context) {
        for (String key : AMOUNT_KEYS) {
            final Object value = context.get(key);
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            if (value != null) {
                try {
                    return new BigDecimal(value.toString().trim().replace(",", ""));
                } catch (NumberFormatException ignored) {
                    // not an amount; try the next key
                }
            }
        }
        return null;
    }

    private static String firstText(final Map<String, Object> context, final List<String> keys) {
        return keys.stream().map(context::get).filter(v -> v != null && StringUtils.isNotBlank(v.toString())).map(Object::toString)
                .findFirst().orElse(null);
    }

    private void insert(final SelfServiceEventEntry entry) {
        Long clientId = entry.clientId();
        Long officeId = entry.officeId();
        if (entry.appUserId() != null && (clientId == null || officeId == null)) {
            final List<Map<String, Object>> rows = jdbcTemplate.queryForList("select u.office_id as office_id,"
                    + " (select min(m.client_id) from m_selfservice_user_client_mapping m where m.appuser_id = u.id) as client_id"
                    + " from m_appselfservice_user u where u.id = ?", entry.appUserId());
            if (!rows.isEmpty()) {
                clientId = clientId != null ? clientId : toLong(rows.get(0).get("client_id"));
                officeId = officeId != null ? officeId : toLong(rows.get(0).get("office_id"));
            }
        } else if (clientId != null && officeId == null) {
            officeId = jdbcTemplate.queryForList("select office_id from m_client where id = ?", Long.class, clientId).stream().findFirst()
                    .orElse(null);
        }
        final String details = entry.details() == null ? null : StringUtils.abbreviate(entry.details(), DETAILS_MAX);
        jdbcTemplate.update(
                "insert into m_selfservice_event (appuser_id, username, client_id, office_id, event_type, category, result, actor,"
                        + " staff_username, description, amount, currency_code, ip_address, details, created_on_utc)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entry.appUserId(), StringUtils.abbreviate(entry.username(), 100), clientId, officeId, entry.eventType(), entry.category(),
                entry.result(), entry.actor(), StringUtils.abbreviate(entry.staffUsername(), 100),
                StringUtils.abbreviate(entry.description(), DESCRIPTION_MAX), entry.amount(), entry.currencyCode(),
                StringUtils.abbreviate(entry.ipAddress(), 64), details, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
    }

    private static Long toLong(final Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
