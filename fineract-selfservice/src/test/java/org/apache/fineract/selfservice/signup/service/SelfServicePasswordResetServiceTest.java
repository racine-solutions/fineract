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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.selfservice.audit.service.SelfServiceEventRecorder;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistration;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistrationRepository;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRequestType;
import org.apache.fineract.selfservice.security.service.SelfServicePasswordChangeService;
import org.apache.fineract.selfservice.security.service.SelfServiceUserIdentifierResolver;
import org.apache.fineract.selfservice.signup.exception.SelfServiceCodeException;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserRepository;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SelfServicePasswordResetServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 0);

    private SelfServiceRegistrationRepository repository;
    private SelfServiceUserIdentifierResolver resolver;
    private AppSelfServiceUserRepository userRepository;
    private SelfServiceCodeSender codeSender;
    private SelfServicePasswordChangeService passwordChangeService;
    private SelfServicePasswordResetService service;
    private AppSelfServiceUser user;
    private final AtomicReference<String> sentCode = new AtomicReference<>();
    private final AtomicReference<SelfServiceRegistration> saved = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        repository = mock(SelfServiceRegistrationRepository.class);
        resolver = mock(SelfServiceUserIdentifierResolver.class);
        userRepository = mock(AppSelfServiceUserRepository.class);
        codeSender = mock(SelfServiceCodeSender.class);
        passwordChangeService = mock(SelfServicePasswordChangeService.class);
        final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        final ClientRepository clientRepository = mock(ClientRepository.class);
        final SelfServiceSupportContacts support = mock(SelfServiceSupportContacts.class);
        when(support.callInstruction()).thenReturn("call +256 774 067 967");
        service = new SelfServicePasswordResetService(repository, resolver, userRepository, codeSender, passwordChangeService, support,
                jdbcTemplate, mock(SelfServiceEventRecorder.class), clientRepository);
        service.clock = () -> NOW;

        final Client client = mock(Client.class);
        when(client.getId()).thenReturn(5L);
        when(client.getAccountNumber()).thenReturn("000000005");
        when(client.getFirstname()).thenReturn("Jane");
        when(client.getLastname()).thenReturn("Doe");
        when(client.getMobileNo()).thenReturn("256785154434");
        when(clientRepository.findById(5L)).thenReturn(Optional.of(client));

        user = mock(AppSelfServiceUser.class);
        when(user.getId()).thenReturn(2L);
        when(user.getUsername()).thenReturn("jane@example.com");
        when(user.getEmail()).thenReturn("jane@example.com");
        when(user.getFirstname()).thenReturn("Jane");
        when(user.getStatus()).thenReturn(SelfServiceUserStatus.ACTIVE);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.findAppSelfServiceUserByName("jane@example.com")).thenReturn(user);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(2L))).thenReturn(List.of(5L));

        when(repository.findSentSince(eq(5L), eq(SelfServiceRequestType.RESET_CODE), any())).thenReturn(List.of());
        when(repository.findOpenByClientAndType(5L, SelfServiceRequestType.RESET_CODE)).thenReturn(List.of());
        when(repository.saveAndFlush(any(SelfServiceRegistration.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(codeSender.send(any(), anyString(), any(), any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            sentCode.set(invocation.getArgument(1));
            return new SelfServiceCodeSender.Delivery(SelfServiceCodeSender.Channel.SMS, "2567•••••434", false);
        });
    }

    @Test
    void start_forAKnownMember_sendsToTheClientRecordPhone_andAnswersLikeAnUnknownOne() {
        when(resolver.candidates("0785154434")).thenReturn(List.of(new SelfServiceUserIdentifierResolver.Match(2L, "jane@example.com")));
        final Map<String, Object> known = service.start("0785154434");
        verify(codeSender).send(eq(SelfServiceCodeSender.Purpose.PASSWORD_RESET), anyString(), eq("256785154434"), eq("jane@example.com"),
                eq("Jane"), eq(SelfServiceCodeSender.Channel.SMS), eq(10));
        assertThat(known.get("resetId")).isEqualTo(saved.get().getExternalAuthorizationToken());

        when(resolver.candidates("nobody@example.com")).thenReturn(List.of());
        final Map<String, Object> unknown = service.start("nobody@example.com");
        assertThat(unknown.keySet()).isEqualTo(known.keySet());
        assertThat(unknown.get("message")).isEqualTo(known.get("message"));
        assertThat(unknown.get("resetId")).isNotEqualTo(known.get("resetId"));
    }

    @Test
    void start_forAPhoneSharedByTwoAccounts_sendsNothing() {
        when(resolver.candidates("0785154434")).thenReturn(List.of(new SelfServiceUserIdentifierResolver.Match(2L, "jane@example.com"),
                new SelfServiceUserIdentifierResolver.Match(3L, "john@example.com")));
        service.start("0785154434");
        verify(codeSender, never()).send(any(), anyString(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void start_forASuspendedMember_sendsNothing() {
        when(user.getStatus()).thenReturn(SelfServiceUserStatus.SUSPENDED);
        when(resolver.candidates("jane@example.com")).thenReturn(List.of(new SelfServiceUserIdentifierResolver.Match(2L, "jane@example.com")));
        service.start("jane@example.com");
        verify(codeSender, never()).send(any(), anyString(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void verify_withTheRightCode_setsThePassword() {
        when(resolver.candidates("jane@example.com")).thenReturn(List.of(new SelfServiceUserIdentifierResolver.Match(2L, "jane@example.com")));
        final String resetId = (String) service.start("jane@example.com").get("resetId");
        final SelfServiceRegistration request = saved.get();
        when(repository.getRequestByExternalAuthorizationToken(resetId, SelfServiceRequestType.RESET_CODE)).thenReturn(request);

        assertThat(service.verify(resetId, sentCode.get(), "New#Pass2026", "New#Pass2026")).containsEntry("username", "jane@example.com");
        verify(passwordChangeService).setNewPassword(user, "New#Pass2026", "New#Pass2026");
        assertThat(request.isConsumed()).isTrue();
        verify(user, never()).unlock(any(), any());
    }

    @Test
    void verify_unlocksAnAccountLockedByWrongPasswords() {
        when(user.isAccountNonLocked()).thenReturn(false);
        when(resolver.candidates("jane@example.com")).thenReturn(List.of(new SelfServiceUserIdentifierResolver.Match(2L, "jane@example.com")));
        final String resetId = (String) service.start("jane@example.com").get("resetId");
        when(repository.getRequestByExternalAuthorizationToken(resetId, SelfServiceRequestType.RESET_CODE)).thenReturn(saved.get());

        service.verify(resetId, sentCode.get(), "New#Pass2026", "New#Pass2026");
        verify(user).unlock(SelfServicePasswordResetService.UNLOCK_REASON, null);
    }

    @Test
    void verify_withAWrongCode_leavesTheLockInPlace() {
        when(user.isAccountNonLocked()).thenReturn(false);
        when(resolver.candidates("jane@example.com")).thenReturn(List.of(new SelfServiceUserIdentifierResolver.Match(2L, "jane@example.com")));
        final String resetId = (String) service.start("jane@example.com").get("resetId");
        when(repository.getRequestByExternalAuthorizationToken(resetId, SelfServiceRequestType.RESET_CODE)).thenReturn(saved.get());
        final String wrong = sentCode.get().equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> service.verify(resetId, wrong, "New#Pass2026", "New#Pass2026")).isInstanceOf(SelfServiceCodeException.class);
        verify(user, never()).unlock(any(), any());
    }

    @Test
    void verify_wrongCodeAndMadeUpResetId_getTheSameAnswer() {
        when(resolver.candidates("jane@example.com")).thenReturn(List.of(new SelfServiceUserIdentifierResolver.Match(2L, "jane@example.com")));
        final String resetId = (String) service.start("jane@example.com").get("resetId");
        when(repository.getRequestByExternalAuthorizationToken(resetId, SelfServiceRequestType.RESET_CODE)).thenReturn(saved.get());
        final String wrong = sentCode.get().equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> service.verify(resetId, wrong, "New#Pass2026", "New#Pass2026"))
                    .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(400);
                        assertThat(e.getExtra()).isEmpty();
                    });
        }
        assertThatThrownBy(() -> service.verify("made-up", "123456", "New#Pass2026", "New#Pass2026"))
                .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("error.msg.code.invalid");
                });
        verify(passwordChangeService, never()).setNewPassword(any(), any(), any());
    }
}
