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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.exception.UnAuthenticatedUserException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SelfServiceCompatibleSecurityContextTest {

    private ConfigurationDomainService config;
    private AppUserRepository appUserRepository;
    private Environment environment;
    private SelfServiceCompatibleSecurityContext ctx;
    private Office office;
    private AppUser auditUser;

    @BeforeEach
    void setUp() {
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "default", "Default Tenant", "UTC", null);
        ThreadLocalContextUtil.setTenant(tenant);
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now());
        businessDates.put(BusinessDateType.COB_DATE, LocalDate.now().minusDays(1));
        ThreadLocalContextUtil.setBusinessDates(businessDates);

        config = mock(ConfigurationDomainService.class);
        appUserRepository = mock(AppUserRepository.class);
        environment = mock(Environment.class);
        when(environment.getProperty("fineract.selfservice.audit-user", "mifos")).thenReturn("mifos");
        ctx = new SelfServiceCompatibleSecurityContext(config, appUserRepository, environment);

        office = mock(Office.class);
        when(office.getId()).thenReturn(1L);

        auditUser = mock(AppUser.class);
        when(appUserRepository.findAppUserByName("mifos")).thenReturn(auditUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        ThreadLocalContextUtil.reset();
    }

    private AppSelfServiceUser mockSelfServiceUser(long id, String username) {
        AppSelfServiceUser principal = mock(AppSelfServiceUser.class);
        when(principal.getId()).thenReturn(id);
        when(principal.getOffice()).thenReturn(office);
        when(principal.getUsername()).thenReturn(username);
        when(principal.getPassword()).thenReturn("secret");
        when(principal.isEnabled()).thenReturn(true);
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.getAuthorities()).thenReturn(new ArrayList<>());
        when(principal.getRoles()).thenReturn(new HashSet<>());
        when(principal.getEmail()).thenReturn(username + "@test.com");
        when(principal.getFirstname()).thenReturn("First");
        when(principal.getLastname()).thenReturn("Last");
        return principal;
    }

    private void setSecurityPrincipal(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }

    private CommandWrapper beneficiaryCommandWrapper() {
        CommandWrapper cw = mock(CommandWrapper.class);
        when(cw.actionName()).thenReturn("CREATE");
        when(cw.getEntityName()).thenReturn("SSBENEFICIARYTPT");
        when(cw.getEntityId()).thenReturn(null);
        return cw;
    }

    // ── authenticatedUser() ──────────────────────────────────────────────────

    /** Tests that a valid self-service principal returns an AppUser stub. */
    @Test
    void authenticatedUser_selfServicePrincipal_returnsStub() {
        AppSelfServiceUser principal = mockSelfServiceUser(42L, "ssuser");
        setSecurityPrincipal(principal);

        AppUser stub = ctx.authenticatedUser();

        assertThat(stub.getId()).isEqualTo(42L);
        assertThat(stub.getUsername()).isEqualTo("ssuser");
        assertThat(stub.getOffice()).isSameAs(office);
        assertThat(stub.getEmail()).isEqualTo("ssuser@test.com");
        assertThat(stub.getFirstname()).isEqualTo("First");
        assertThat(stub.getLastname()).isEqualTo("Last");
    }

    /** Tests that missing principal throws UnAuthenticatedUserException. */
    @Test
    void authenticatedUser_noPrincipal_throwsUnAuthenticatedUserException() {
        assertThatThrownBy(() -> ctx.authenticatedUser()).isInstanceOf(UnAuthenticatedUserException.class);
    }

    /**
     * While a command handler is executing (e.g. a core write-service persisting the current user as a real AppUser
     * relationship, such as SavingsAccount.submittedBy), authenticatedUser() must return the real, configured audit
     * AppUser instead of the stub - the stub is a never-persisted object and would make Hibernate/EclipseLink fail the
     * same way CommandSource.maker used to.
     */
    @Test
    void authenticatedUser_selfServicePrincipal_duringCommandHandler_returnsConfiguredAuditUser() {
        AppSelfServiceUser principal = mockSelfServiceUser(42L, "ssuser");
        setSecurityPrincipal(principal);

        ThreadLocalContextUtil.enterCommandHandler();
        try {
            AppUser resolved = ctx.authenticatedUser();
            assertThat(resolved).isSameAs(auditUser);
        } finally {
            ThreadLocalContextUtil.exitCommandHandler();
        }
    }

    /** Once the command handler scope exits, authenticatedUser() reverts to returning the stub. */
    @Test
    void authenticatedUser_selfServicePrincipal_afterCommandHandlerExits_returnsStubAgain() {
        AppSelfServiceUser principal = mockSelfServiceUser(42L, "ssuser");
        setSecurityPrincipal(principal);

        ThreadLocalContextUtil.enterCommandHandler();
        ThreadLocalContextUtil.exitCommandHandler();

        AppUser stub = ctx.authenticatedUser();

        assertThat(stub.getId()).isEqualTo(42L);
        assertThat(stub.getUsername()).isEqualTo("ssuser");
    }

    /** Nested command handler execution (e.g. batch requests) must not exit the scope prematurely. */
    @Test
    void authenticatedUser_selfServicePrincipal_nestedCommandHandlers_staysInAuditScopeUntilOutermostExits() {
        AppSelfServiceUser principal = mockSelfServiceUser(42L, "ssuser");
        setSecurityPrincipal(principal);

        ThreadLocalContextUtil.enterCommandHandler();
        ThreadLocalContextUtil.enterCommandHandler();
        ThreadLocalContextUtil.exitCommandHandler();

        assertThat(ctx.authenticatedUser()).isSameAs(auditUser);

        ThreadLocalContextUtil.exitCommandHandler();

        assertThat(ctx.authenticatedUser().getId()).isEqualTo(42L);
    }

    // ── authenticatedUser(CommandWrapper) ────────────────────────────────────

    /**
     * A self-service principal must resolve to the real, configured audit AppUser (not the stub): this value is
     * persisted as CommandSource.maker, a real FK into m_appuser, which a self-service stub id can never satisfy.
     */
    @Test
    void authenticatedUserWithCommandWrapper_selfServicePrincipal_returnsConfiguredAuditUser() {
        AppSelfServiceUser principal = mockSelfServiceUser(99L, "ssuser3");
        setSecurityPrincipal(principal);

        AppUser resolved = ctx.authenticatedUser(beneficiaryCommandWrapper());

        assertThat(resolved).isSameAs(auditUser);
    }

    /** Tests command wrapper resolution throws when no principal is found. */
    @Test
    void authenticatedUserWithCommandWrapper_noPrincipal_throwsUnAuthenticatedUserException() {
        assertThatThrownBy(() -> ctx.authenticatedUser(beneficiaryCommandWrapper())).isInstanceOf(UnAuthenticatedUserException.class);
    }

    /** Tests that password-expired self-service users still resolve to the audit user. */
    @Test
    void authenticatedUserWithCommandWrapper_passwordExpiredSelfServiceUser_returnsConfiguredAuditUser() {
        AppSelfServiceUser principal = mockSelfServiceUser(55L, "expireduser");
        when(principal.isPasswordResetRequired()).thenReturn(true);
        setSecurityPrincipal(principal);

        AppUser resolved = ctx.authenticatedUser(beneficiaryCommandWrapper());

        assertThat(resolved).isSameAs(auditUser);
    }

  /** Tests that a missing configured audit user surfaces a clear configuration error. */
  @Test
  void authenticatedUserWithCommandWrapper_missingConfiguredAuditUser_throwsIllegalStateException() {
    when(appUserRepository.findAppUserByName("mifos")).thenReturn(null);
    AppSelfServiceUser principal = mockSelfServiceUser(99L, "ssuser3");
    setSecurityPrincipal(principal);

    assertThatThrownBy(() -> ctx.authenticatedUser(beneficiaryCommandWrapper()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mifos");
  }

    // ── getAuthenticatedUserIfPresent() ──────────────────────────────────────

    /** Tests getAuthenticatedUserIfPresent returns a stub for self-service user. */
    @Test
    void getAuthenticatedUserIfPresent_selfServicePrincipal_returnsStub() {
        AppSelfServiceUser principal = mockSelfServiceUser(7L, "ssuser2");
        setSecurityPrincipal(principal);

        AppUser stub = ctx.getAuthenticatedUserIfPresent();

        assertThat(stub.getId()).isEqualTo(7L);
        assertThat(stub.getUsername()).isEqualTo("ssuser2");
        assertThat(stub.getOffice()).isSameAs(office);
    }

    /** Tests getAuthenticatedUserIfPresent returns null when absent. */
    @Test
    void getAuthenticatedUserIfPresent_noPrincipal_returnsNull() {
        AppUser result = ctx.getAuthenticatedUserIfPresent();

        assertThat(result).isNull();
    }

    /** getAuthenticatedUserIfPresent is subject to the same command-handler-execution override. */
    @Test
    void getAuthenticatedUserIfPresent_selfServicePrincipal_duringCommandHandler_returnsConfiguredAuditUser() {
        AppSelfServiceUser principal = mockSelfServiceUser(7L, "ssuser2");
        setSecurityPrincipal(principal);

        ThreadLocalContextUtil.enterCommandHandler();
        try {
            assertThat(ctx.getAuthenticatedUserIfPresent()).isSameAs(auditUser);
        } finally {
            ThreadLocalContextUtil.exitCommandHandler();
        }
    }
}
