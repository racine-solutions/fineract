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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.racine.officerapp.domain.OfficerAppEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes the officer app activity trail (m_officerapp_event). Each row is written in its own transaction and any error
 * is logged and swallowed, so recording never changes the outcome of what is recorded.
 */
@Slf4j
@Service
public class OfficerAppEventRecorder {

    static final int DESCRIPTION_MAX = 500;
    static final int DETAILS_MAX = 2000;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate newTransaction;

    public OfficerAppEventRecorder(final JdbcTemplate jdbcTemplate, final PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(final OfficerAppEvent event) {
        try {
            newTransaction.executeWithoutResult(status -> insert(complete(event)));
        } catch (RuntimeException e) {
            log.error("Could not record officer app event {} for user {}", event.eventType(), event.appUserId(), e);
        }
    }

    /** Records the event once the current transaction commits (straight away when there is none). */
    public void recordAfterCommit(final OfficerAppEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    record(event);
                }
            });
        } else {
            record(event);
        }
    }

    /** Fills in the username and office from the staff user when they are missing. */
    private OfficerAppEvent complete(final OfficerAppEvent event) {
        if (event.appUserId() == null || (event.username() != null && event.officeId() != null)) {
            return event;
        }
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList("select username, office_id from m_appuser where id = ?",
                event.appUserId());
        if (rows.isEmpty()) {
            return event;
        }
        final Map<String, Object> row = rows.get(0);
        final Object officeId = row.get("office_id");
        return event.toBuilder().username(event.username() != null ? event.username() : (String) row.get("username"))
                .officeId(event.officeId() != null ? event.officeId() : officeId instanceof Number n ? n.longValue() : null).build();
    }

    private void insert(final OfficerAppEvent e) {
        jdbcTemplate.update(
                "insert into m_officerapp_event (appuser_id, username, office_id, device_id, device_label, event_type,"
                        + " category, result, actor, staff_username, command_id, entity_name, action_name, resource_id, client_id, loan_id,"
                        + " savings_id, amount, currency_code, description, details, ip_address, captured_on_utc, created_on_utc)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                e.appUserId(), StringUtils.left(e.username(), 100), e.officeId(), e.deviceId(), StringUtils.left(e.deviceLabel(), 150),
                e.eventType(), e.category(), e.result(), e.actor(), StringUtils.left(e.staffUsername(), 100), e.commandId(),
                StringUtils.left(e.entityName(), 100), StringUtils.left(e.actionName(), 100), e.resourceId(), e.clientId(), e.loanId(),
                e.savingsId(), e.amount(), StringUtils.left(e.currencyCode(), 3), StringUtils.left(e.description(), DESCRIPTION_MAX),
                StringUtils.left(e.details(), DETAILS_MAX), StringUtils.left(e.ipAddress(), 64),
                e.capturedOnUtc() == null ? null : Timestamp.valueOf(e.capturedOnUtc()),
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)));
    }
}
