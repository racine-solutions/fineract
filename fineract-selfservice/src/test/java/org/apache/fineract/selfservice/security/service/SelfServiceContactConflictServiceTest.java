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
package org.apache.fineract.selfservice.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.apache.fineract.selfservice.audit.data.SelfServiceEventEntry;
import org.apache.fineract.selfservice.audit.service.SelfServiceEventRecorder;
import org.apache.fineract.selfservice.security.exception.SelfServiceContactConflictException;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class SelfServiceContactConflictServiceTest {

    private JdbcTemplate jdbcTemplate;
    private SelfServiceEventRecorder recorder;
    private SelfServiceContactConflictService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        recorder = mock(SelfServiceEventRecorder.class);
        final SelfServiceSupportContacts support = mock(SelfServiceSupportContacts.class);
        when(support.callInstruction()).thenReturn("call +256 774 067 967");
        when(support.phoneNumbers()).thenReturn(List.of("+256 774 067 967"));
        service = new SelfServiceContactConflictService(jdbcTemplate, support, recorder);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
    }

    /** Other clients' phones as stored: id, account_no, mobile_no. */
    private void otherPhones(final Object[]... rows) {
        doAnswer(invocation -> {
            final RowCallbackHandler handler = invocation.getArgument(1);
            for (Object[] row : rows) {
                final ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("id")).thenReturn((Long) row[0]);
                when(rs.getString("account_no")).thenReturn((String) row[1]);
                when(rs.getString("mobile_no")).thenReturn((String) row[2]);
                handler.processRow(rs);
            }
            return null;
        }).when(jdbcTemplate).query(contains("mobile_no like ?"), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    void signup_isBlocked_whenThePhoneIsOnAnotherClientInAnotherFormat() {
        otherPhones(new Object[] { 1L, "000000001", "256785154434" });
        assertThatThrownBy(() -> service.checkSignup(5L, "000000005", "0785154434", null, "kony@example.com"))
                .isInstanceOfSatisfying(SelfServiceContactConflictException.class, e -> {
                    assertThat(e.getReference()).isEqualTo("DUP-000000005");
                    assertThat(e.getMessage()).contains("DUP-000000005").contains("+256 774 067 967").doesNotContain("000000001");
                    assertThat(e.getSupportPhoneNumbers()).containsExactly("+256 774 067 967");
                });
        final ArgumentCaptor<SelfServiceEventEntry> entry = ArgumentCaptor.forClass(SelfServiceEventEntry.class);
        verify(recorder).record(entry.capture());
        assertThat(entry.getValue().eventType()).isEqualTo("CONTACT_CONFLICT");
        assertThat(entry.getValue().description()).contains("000000001").contains("DUP-000000005");
    }

    @Test
    void signup_isBlocked_whenTheEmailIsOnAnotherClient() {
        otherPhones();
        doAnswer(invocation -> {
            final ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(3L);
            when(rs.getString("account_no")).thenReturn("000000003");
            ((RowCallbackHandler) invocation.getArgument(1)).processRow(rs);
            return null;
        }).when(jdbcTemplate).query(contains("email_address)) = ?"), any(RowCallbackHandler.class), eq(5L), eq("shared@example.com"));

        assertThatThrownBy(() -> service.checkSignup(5L, "000000005", "0700111222", null, "Shared@Example.com"))
                .isInstanceOf(SelfServiceContactConflictException.class);
    }

    @Test
    void uniqueDetails_passWithoutRecordingAnything() {
        otherPhones(new Object[] { 8L, "000000008", "256700000434" });
        service.checkSignup(5L, "000000005", "0785154434", "kony.record@example.com", "kony@example.com");
        verify(recorder, never()).record(any());
    }

    @Test
    void login_isBlocked_forAnAppUserWhoseClientSharesItsPhone() {
        final AppSelfServiceUser user = mock(AppSelfServiceUser.class);
        when(user.getId()).thenReturn(3L);
        when(user.getUsername()).thenReturn("kony@example.com");
        when(user.getEmail()).thenReturn("kony@example.com");
        when(jdbcTemplate.queryForList(contains("m_selfservice_user_client_mapping m"), eq(3L)))
                .thenReturn(List.of(Map.of("id", 5L, "account_no", "000000005", "mobile_no", "0785154434")));
        otherPhones(new Object[] { 1L, "000000001", "256785154434" });

        assertThat(service.hasConflict(user)).isTrue();
        verify(recorder, never()).record(any());
        assertThatThrownBy(() -> service.checkUser(user)).isInstanceOfSatisfying(SelfServiceContactConflictException.class,
                e -> assertThat(e.getReference()).isEqualTo("DUP-000000005"));
    }

    @Test
    void login_isBlocked_whenAnotherAppUserUsesTheSameEmail() {
        final AppSelfServiceUser user = mock(AppSelfServiceUser.class);
        when(user.getId()).thenReturn(3L);
        when(user.getUsername()).thenReturn("kony@example.com");
        when(user.getEmail()).thenReturn("kony@example.com");
        when(jdbcTemplate.queryForList(contains("m_selfservice_user_client_mapping m"), eq(3L))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("from m_appselfservice_user u"), eq(Long.class), anyLong(), eq("kony@example.com"),
                eq("kony@example.com"), anyLong())).thenReturn(List.of(9L));

        assertThatThrownBy(() -> service.checkUser(user)).isInstanceOfSatisfying(SelfServiceContactConflictException.class,
                e -> assertThat(e.getReference()).isEqualTo("DUP-U3"));
    }
}
