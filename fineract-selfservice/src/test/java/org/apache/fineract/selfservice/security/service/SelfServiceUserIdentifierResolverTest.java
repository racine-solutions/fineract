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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import org.apache.fineract.selfservice.signup.exception.SelfServiceCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class SelfServiceUserIdentifierResolverTest {

    private JdbcTemplate jdbcTemplate;
    private SelfServiceUserIdentifierResolver resolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        resolver = new SelfServiceUserIdentifierResolver(jdbcTemplate);
        when(jdbcTemplate.queryForList(contains("where username = ?"), eq(String.class), anyString())).thenReturn(List.of());
    }

    /** Linked client phones as stored: id, username, mobile_no. */
    private void phones(final Object[]... rows) {
        doAnswer(invocation -> {
            final RowCallbackHandler handler = invocation.getArgument(1);
            for (Object[] row : rows) {
                final ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("id")).thenReturn((Long) row[0]);
                when(rs.getString("username")).thenReturn((String) row[1]);
                when(rs.getString("mobile_no")).thenReturn((String) row[2]);
                handler.processRow(rs);
            }
            return null;
        }).when(jdbcTemplate).query(contains("c.mobile_no like ?"), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    void anExactUsernameIsUsedAsItIs() {
        when(jdbcTemplate.queryForList(contains("where username = ?"), eq(String.class), eq("0785154434"))).thenReturn(List.of("0785154434"));
        assertThat(resolver.resolveLoginUsername("0785154434")).isEqualTo("0785154434");
    }

    @Test
    void aPhoneOnExactlyOneLinkedClient_logsIntoThatAccount() {
        phones(new Object[] { 7L, "jane@example.com", "256785154434" }, new Object[] { 8L, "john@example.com", "256700000434" });
        assertThat(resolver.resolveLoginUsername("0785 154 434")).isEqualTo("jane@example.com");
    }

    @Test
    void aPhoneOnTwoAccounts_isRefusedInsteadOfPickingOne() {
        phones(new Object[] { 7L, "jane@example.com", "256785154434" }, new Object[] { 8L, "john@example.com", "0785154434" });
        assertThatThrownBy(() -> resolver.resolveLoginUsername("0785154434")).isInstanceOfSatisfying(SelfServiceCodeException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(401);
            assertThat(e.getCode()).isEqualTo("error.msg.login.identifier.ambiguous");
        });
    }

    @Test
    void anUnknownPhone_isPassedOnUnchanged() {
        phones();
        assertThat(resolver.resolveLoginUsername("0700111222")).isEqualTo("0700111222");
    }
}
