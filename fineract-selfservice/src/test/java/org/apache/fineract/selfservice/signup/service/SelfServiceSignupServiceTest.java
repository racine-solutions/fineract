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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.selfservice.audit.service.SelfServiceEventRecorder;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistration;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistrationRepository;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRequestType;
import org.apache.fineract.selfservice.security.service.SelfServicePasswordChangeService;
import org.apache.fineract.selfservice.signup.exception.SelfServiceCodeException;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserClientMappingRepository;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

class SelfServiceSignupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 0);

    private SelfServiceRegistrationRepository repository;
    private ClientRepository clientRepository;
    private SelfServiceCodeSender codeSender;
    private JdbcTemplate jdbcTemplate;
    private AppSelfServiceUserRepository userRepository;
    private AppSelfServiceUserClientMappingRepository mappingRepository;
    private SelfServiceSignupService service;
    private Client client;
    private final AtomicReference<String> sentCode = new AtomicReference<>();
    private final AtomicReference<SelfServiceRegistration> saved = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default Tenant", "UTC", null));
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 11, 1));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        repository = mock(SelfServiceRegistrationRepository.class);
        clientRepository = mock(ClientRepository.class);
        codeSender = mock(SelfServiceCodeSender.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        userRepository = mock(AppSelfServiceUserRepository.class);
        mappingRepository = mock(AppSelfServiceUserClientMappingRepository.class);
        final RoleRepository roleRepository = mock(RoleRepository.class);
        when(roleRepository.getRoleByName("Self Service User")).thenReturn(mock(Role.class));
        final PlatformPasswordEncoder encoder = mock(PlatformPasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("{bcrypt}encoded");
        final SelfServiceSupportContacts support = mock(SelfServiceSupportContacts.class);
        when(support.callInstruction()).thenReturn("call +256 774 067 967");
        when(support.phoneNumbers()).thenReturn(List.of("+256 774 067 967"));

        service = new SelfServiceSignupService(repository, clientRepository, codeSender, mock(SelfServicePasswordChangeService.class),
                encoder, support, jdbcTemplate, roleRepository, userRepository, mappingRepository, mock(ApplicationEventPublisher.class),
                mock(SelfServiceEventRecorder.class));
        service.clock = () -> NOW;

        client = mock(Client.class);
        when(client.getId()).thenReturn(5L);
        when(client.getAccountNumber()).thenReturn("000000005");
        when(client.getFirstname()).thenReturn("Jane");
        when(client.getLastname()).thenReturn("Doe");
        when(client.getMobileNo()).thenReturn("256785154434");
        when(client.getEmailAddress()).thenReturn("jane.record@example.com");
        when(client.getOffice()).thenReturn(mock(Office.class));
        when(client.isActive()).thenReturn(true);
        when(clientRepository.getClientByAccountNumber("000000005")).thenReturn(client);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        when(repository.findSentSince(eq(5L), eq(SelfServiceRequestType.SIGNUP), any())).thenReturn(List.of());
        when(repository.findOpenByClientAndType(5L, SelfServiceRequestType.SIGNUP)).thenReturn(List.of());
        when(repository.saveAndFlush(any(SelfServiceRegistration.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(codeSender.send(any(), anyString(), any(), any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            sentCode.set(invocation.getArgument(1));
            return new SelfServiceCodeSender.Delivery(SelfServiceCodeSender.Channel.SMS, "2567•••••434", false);
        });
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    private SelfServiceSignupService.StartRequest startRequest(final String phone, final String email) {
        return new SelfServiceSignupService.StartRequest("000000005", "jane", "DOE", phone, email, "Secret#2026", "Secret#2026", null);
    }

    @Test
    void start_sendsTheCodeToThePhoneAndEmailOnTheClientRecord_notToTypedDetails() {
        final Map<String, Object> body = service.start(startRequest("0785 154 434", "Jane.Doe@Gmail.com"));

        verify(codeSender).send(eq(SelfServiceCodeSender.Purpose.SIGNUP), anyString(), eq("256785154434"), eq("jane.record@example.com"),
                eq("Jane"), eq(SelfServiceCodeSender.Channel.SMS), eq(10));
        assertThat(body).containsEntry("channel", "SMS").containsEntry("destination", "2567•••••434")
                .containsEntry("codeExpiresInSeconds", 600L).containsEntry("resendAfterSeconds", 60L);
        final SelfServiceRegistration registration = saved.get();
        assertThat(body.get("registrationId")).isEqualTo(registration.getExternalAuthorizationToken());
        assertThat(registration.getUsername()).isEqualTo("jane.doe@gmail.com");
        assertThat(registration.getPassword()).isEqualTo("{bcrypt}encoded");
        assertThat(registration.getAuthenticationToken()).isNotEqualTo(sentCode.get()).startsWith("$2");
        assertThat(registration.getExpiresAt()).isEqualTo(NOW.plusHours(24));
    }

    @Test
    void start_withAnyWrongDetail_givesOneAnswerAndSendsNothing() {
        for (SelfServiceSignupService.StartRequest request : List.of(startRequest("0785154435", "jane@example.com"),
                new SelfServiceSignupService.StartRequest("000000005", "Janet", "Doe", "0785154434", "jane@example.com", "Secret#2026",
                        null, null),
                new SelfServiceSignupService.StartRequest("999", "Jane", "Doe", "0785154434", "jane@example.com", "Secret#2026", null,
                        null))) {
            assertThatThrownBy(() -> service.start(request)).isInstanceOfSatisfying(SelfServiceCodeException.class,
                    e -> assertThat(e.getCode()).isEqualTo("error.msg.signup.details.mismatch"));
        }
        verify(codeSender, never()).send(any(), anyString(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void start_refusesAClientThatAlreadyHasAnAppUser() {
        when(jdbcTemplate.queryForObject(eq("select count(*) from m_selfservice_user_client_mapping where client_id = ?"),
                eq(Integer.class), any())).thenReturn(1);
        assertThatThrownBy(() -> service.start(startRequest("0785154434", "jane@example.com")))
                .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    @Test
    void start_replacesAnEarlierSignupForTheClient() {
        final SelfServiceRegistration earlier = SelfServiceCodeChecksTest.request("111111", NOW.minusMinutes(5));
        when(repository.findOpenByClientAndType(5L, SelfServiceRequestType.SIGNUP)).thenReturn(List.of(earlier));
        service.start(startRequest("0785154434", "jane@example.com"));
        assertThat(earlier.isConsumed()).isTrue();
        assertThat(earlier.getAuthenticationToken()).isNull();
    }

    @Test
    void start_whenNeitherSmsNorEmailWorks_returnsTheReferenceAndSupportNumbers() {
        when(codeSender.send(any(), anyString(), any(), any(), any(), any(), anyInt())).thenReturn(null);
        assertThatThrownBy(() -> service.start(startRequest("0785154434", "jane@example.com")))
                .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(502);
                    assertThat(e.getExtra()).containsKey("registrationId").containsEntry("supportPhoneNumbers", List.of("+256 774 067 967"));
                });
    }

    @Test
    void verify_withTheRightCodeAndReference_createsTheUserLinkedToThatClient() {
        service.start(startRequest("0785154434", "jane@example.com"));
        final SelfServiceRegistration registration = saved.get();
        when(repository.getRequestByExternalAuthorizationToken(registration.getExternalAuthorizationToken(), SelfServiceRequestType.SIGNUP))
                .thenReturn(registration);
        when(userRepository.saveAndFlush(any(AppSelfServiceUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Map<String, Object> body = service.verify(registration.getExternalAuthorizationToken(), sentCode.get());

        assertThat(body).containsEntry("username", "jane@example.com").containsEntry("clientId", 5L);
        verify(mappingRepository).saveClientUserMapping(any(), eq(5L));
        assertThat(registration.isConsumed()).isTrue();
    }

    @Test
    void verify_withAWrongCode_countsTheTry_andCreatesNothing() {
        service.start(startRequest("0785154434", "jane@example.com"));
        final SelfServiceRegistration registration = saved.get();
        when(repository.getRequestByExternalAuthorizationToken(registration.getExternalAuthorizationToken(), SelfServiceRequestType.SIGNUP))
                .thenReturn(registration);
        final String wrong = sentCode.get().equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> service.verify(registration.getExternalAuthorizationToken(), wrong))
                .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> assertThat(e.getExtra()).containsEntry("attemptsLeft", 4));
        verify(userRepository, never()).saveAndFlush(any(AppSelfServiceUser.class));
    }

    @Test
    void verify_withAReplacedOrUnknownReference_isRefused() {
        final SelfServiceRegistration replaced = SelfServiceCodeChecksTest.request("111111", NOW.minusMinutes(1));
        replaced.close();
        when(repository.getRequestByExternalAuthorizationToken("ref-1", SelfServiceRequestType.SIGNUP)).thenReturn(replaced);
        assertThatThrownBy(() -> service.verify("ref-1", "111111")).isInstanceOfSatisfying(SelfServiceCodeException.class,
                e -> assertThat(e.getStatus()).isEqualTo(404));
        assertThatThrownBy(() -> service.verify("unknown", "111111")).isInstanceOfSatisfying(SelfServiceCodeException.class,
                e -> assertThat(e.getStatus()).isEqualTo(404));
    }

    @Test
    void verify_whenTheClientGotAnAppUserMeanwhile_closesTheSignup() {
        service.start(startRequest("0785154434", "jane@example.com"));
        final SelfServiceRegistration registration = saved.get();
        when(repository.getRequestByExternalAuthorizationToken(registration.getExternalAuthorizationToken(), SelfServiceRequestType.SIGNUP))
                .thenReturn(registration);
        when(jdbcTemplate.queryForObject(eq("select count(*) from m_selfservice_user_client_mapping where client_id = ?"),
                eq(Integer.class), any())).thenReturn(1);

        assertThatThrownBy(() -> service.verify(registration.getExternalAuthorizationToken(), sentCode.get()))
                .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
        assertThat(registration.isConsumed()).isTrue();
        verify(userRepository, never()).saveAndFlush(any(AppSelfServiceUser.class));
    }
}
