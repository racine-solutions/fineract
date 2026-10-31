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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserRepository;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;

class SelfServicePasswordChangeServiceTest {

    private static final String CURRENT = "Current#Pass1";

    private AppSelfServiceUserRepository repository;
    private SelfServiceAuthenticationTokenService tokenService;
    private SelfServicePasswordChangeService service;
    private AppSelfServiceUser user;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default Tenant", "UTC", null));
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now());
        ThreadLocalContextUtil.setBusinessDates(businessDates);

        repository = mock(AppSelfServiceUserRepository.class);
        tokenService = mock(SelfServiceAuthenticationTokenService.class);
        PlatformPasswordEncoder encoder = mock(PlatformPasswordEncoder.class);
        when(encoder.encode(any())).thenAnswer(i -> PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .encode(((AppSelfServiceUser) i.getArgument(0)).getPassword()));
        service = new SelfServicePasswordChangeService(repository, encoder, mock(PasswordValidationPolicyRepository.class), tokenService,
                new FromJsonHelper());

        String encoded = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(CURRENT);
        user = new AppSelfServiceUser(mock(Office.class), new User("jdoe", encoded, List.of(new SimpleGrantedAuthority("DUMMY"))),
                new HashSet<>(), "j@example.com", "John", "Doe", null, true, true, new ArrayList<>(), false);
        ReflectionTestUtils.setField(user, "id", 7L);
        user.updatePasswordResetRequired(true);
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        ThreadLocalContextUtil.reset();
    }

    @Test
    void changesOnlyTheLoggedInAppUser_completesAForcedChange_andSignsOut() {
        assertThat(service.changeOwnPassword("{\"password\":\"New#Pass2\",\"repeatPassword\":\"New#Pass2\"}")).isEqualTo(7L);

        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("New#Pass2", user.getPassword())).isTrue();
        assertThat(user.isPasswordResetRequired()).isFalse();
        verify(repository).saveAndFlush(user);
        verify(tokenService).revokeAllSessions(7L);
    }

    @Test
    void samePasswordAgain_isRefused() {
        assertThatThrownBy(() -> service.changeOwnPassword("{\"password\":\"" + CURRENT + "\",\"repeatPassword\":\"" + CURRENT + "\"}"))
                .isInstanceOf(GeneralPlatformDomainRuleException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void mismatchedPasswords_areRefused() {
        assertThatThrownBy(() -> service.changeOwnPassword("{\"password\":\"New#Pass2\",\"repeatPassword\":\"Other#Pass3\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonActiveUser_cannotChangeThePassword() {
        user.changeStatus(SelfServiceUserStatus.SUSPENDED, "test", 1L);

        assertThatThrownBy(() -> service.changeOwnPassword("{\"password\":\"New#Pass2\",\"repeatPassword\":\"New#Pass2\"}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be changed");
    }
}
