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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.selfservice.audit.data.SelfServiceEventEntry;
import org.apache.fineract.selfservice.notification.SelfServiceNotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class SelfServiceEventRecorderTest {

    private JdbcTemplate jdbcTemplate;
    private SelfServiceEventRecorder recorder;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        recorder = new SelfServiceEventRecorder(jdbcTemplate, mock(PlatformTransactionManager.class));
    }

    @Test
    void transferEvent_becomesClientTransferEntryWithAmount() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("transferAmount", "1,500.50");
        context.put("currencyCode", "UGX");
        context.put("toAccountNumber", "000000123");

        SelfServiceEventEntry entry = SelfServiceEventRecorder
                .fromNotificationEvent(event(SelfServiceNotificationEvent.Type.TRANSFER_SUCCESS, context));

        assertThat(entry.eventType()).isEqualTo("TRANSFER_SUCCESS");
        assertThat(entry.category()).isEqualTo(SelfServiceEventEntry.CATEGORY_TRANSFER);
        assertThat(entry.result()).isEqualTo(SelfServiceEventEntry.RESULT_SUCCESS);
        assertThat(entry.actor()).isEqualTo(SelfServiceEventEntry.ACTOR_CLIENT);
        assertThat(entry.description()).isEqualTo("Transfer made");
        assertThat(entry.amount()).isEqualByComparingTo(new BigDecimal("1500.50"));
        assertThat(entry.currencyCode()).isEqualTo("UGX");
        assertThat(entry.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(entry.details()).contains("000000123");
    }

    @Test
    void failedLogin_isRecordedAsFailed() {
        SelfServiceEventEntry entry = SelfServiceEventRecorder
                .fromNotificationEvent(event(SelfServiceNotificationEvent.Type.LOGIN_FAILURE, Map.of()));

        assertThat(entry.category()).isEqualTo("LOGIN");
        assertThat(entry.result()).isEqualTo(SelfServiceEventEntry.RESULT_FAILED);
        assertThat(entry.details()).isNull();
    }

    @Test
    void beneficiaryName_isAddedToTheDescription() {
        SelfServiceEventEntry entry = SelfServiceEventRecorder
                .fromNotificationEvent(event(SelfServiceNotificationEvent.Type.BENEFICIARY_ADDED, Map.of("beneficiaryName", "Jane Doe")));

        assertThat(entry.description()).isEqualTo("Beneficiary added: Jane Doe");
    }

    @Test
    void secretsAreNeverStored() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("authCode", "123456");
        context.put("otp", "654321");
        context.put("enrollmentToken", "abc");
        context.put("newPassword", "s3cret");
        context.put("username", "jdoe");
        context.put("nested", List.of("ignored"));

        String details = SelfServiceEventRecorder.safeDetails(context);

        assertThat(details).isEqualTo("{\"username\":\"jdoe\"}");
    }

    @Test
    void everyTypeHasACategoryAndDescription() {
        for (SelfServiceNotificationEvent.Type type : SelfServiceNotificationEvent.Type.values()) {
            assertThat(SelfServiceEventRecorder.categoryOf(type)).isNotBlank();
            assertThat(SelfServiceEventRecorder.describe(type)).isNotBlank();
        }
    }

    @Test
    void record_fillsClientAndOfficeFromTheUser() {
        when(jdbcTemplate.queryForList(startsWith("select u.office_id"), eq(42L)))
                .thenReturn(List.of(Map.of("office_id", 2L, "client_id", 7L)));

        recorder.record(SelfServiceEventEntry.builder().appUserId(42L).username("jdoe").eventType("LOGIN_SUCCESS").category("LOGIN")
                .result(SelfServiceEventEntry.RESULT_SUCCESS).actor(SelfServiceEventEntry.ACTOR_CLIENT).description("Logged in").build());

        verify(jdbcTemplate).update(startsWith("insert into m_selfservice_event"), eq(42L), eq("jdoe"), eq(7L), eq(2L), eq("LOGIN_SUCCESS"),
                eq("LOGIN"), eq("SUCCESS"), eq("CLIENT"), any(), eq("Logged in"), any(), any(), any(), any(), any());
    }

    @Test
    void record_neverThrows() {
        when(jdbcTemplate.queryForList(anyString(), anyLong())).thenThrow(new IllegalStateException("database down"));

        recorder.record(SelfServiceEventEntry.builder().appUserId(42L).eventType("LOGIN_SUCCESS").category("LOGIN").result("SUCCESS")
                .actor("CLIENT").build());
    }

    @Test
    void accessChangedEvents_areLeftToTheBackOffice() {
        recorder.onNotificationEvent(event(SelfServiceNotificationEvent.Type.ACCESS_CHANGED, Map.of("reason", "x")));

        verifyNoInteractions(jdbcTemplate);
    }

    private static SelfServiceNotificationEvent event(final SelfServiceNotificationEvent.Type type, final Map<String, Object> context) {
        return new SelfServiceNotificationEvent(new Object(), type, 42L, "John", "Doe", "jdoe", "j@example.com", "0700000000", true,
                "10.0.0.1", Locale.ENGLISH, null, null, context);
    }
}
