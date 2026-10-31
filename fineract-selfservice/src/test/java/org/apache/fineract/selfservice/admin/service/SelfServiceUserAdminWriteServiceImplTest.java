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
package org.apache.fineract.selfservice.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.apache.fineract.selfservice.admin.exception.SelfServiceUserNotFoundException;
import org.apache.fineract.selfservice.notification.SelfServiceNotificationEvent;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistration;
import org.apache.fineract.selfservice.registration.service.SelfServiceForgotPasswordWritePlatformService;
import org.apache.fineract.selfservice.security.service.SelfServiceAuthenticationTokenService;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserRepository;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

class SelfServiceUserAdminWriteServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final Long STAFF_ID = 9L;

    private AppSelfServiceUserRepository repository;
    private PlatformSecurityContext context;
    private SelfServiceAuthenticationTokenService tokenService;
    private SelfServiceForgotPasswordWritePlatformService forgotPasswordService;
    private ApplicationEventPublisher publisher;
    private SelfServiceUserAdminWriteServiceImpl service;
    private AppSelfServiceUser user;
    private JdbcTemplate jdbcTemplate;
    private ClientRepositoryWrapper clientRepository;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // AppSelfServiceUser needs a tenant and business dates
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default Tenant", "UTC", null));
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now());
        businessDates.put(BusinessDateType.COB_DATE, LocalDate.now().minusDays(1));
        ThreadLocalContextUtil.setBusinessDates(businessDates);

        repository = mock(AppSelfServiceUserRepository.class);
        context = mock(PlatformSecurityContext.class);
        tokenService = mock(SelfServiceAuthenticationTokenService.class);
        forgotPasswordService = mock(SelfServiceForgotPasswordWritePlatformService.class);
        publisher = mock(ApplicationEventPublisher.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        clientRepository = mock(ClientRepositoryWrapper.class);
        cacheManager = mock(CacheManager.class);
        service = new SelfServiceUserAdminWriteServiceImpl(repository, context, tokenService, forgotPasswordService, publisher,
                jdbcTemplate, clientRepository, cacheManager);

        AppUser staff = mock(AppUser.class);
        lenient().when(staff.getId()).thenReturn(STAFF_ID);
        lenient().when(context.authenticatedUser()).thenReturn(staff);
        lenient().when(context.officeHierarchy()).thenReturn(".1.");

        user = userInOffice(".1.2.");
        lenient().when(repository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void suspend_activeUser_suspendsEndsSessionsAndNotifies() {
        service.suspend(USER_ID, command("Disputed transfer", null, null));

        assertThat(user.getStatus()).isEqualTo(SelfServiceUserStatus.SUSPENDED);
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getStatusReason()).isEqualTo("Disputed transfer");
        assertThat(user.getStatusChangedById()).isEqualTo(STAFF_ID);
        verify(tokenService).revokeAllSessions(USER_ID);
        SelfServiceNotificationEvent event = publishedEvent();
        assertThat(event.getType()).isEqualTo(SelfServiceNotificationEvent.Type.ACCESS_CHANGED);
        assertThat(event.getContextData()).containsEntry("reason", "Disputed transfer");
        assertThat((String) event.getContextData().get("accessChange")).contains("suspended");
    }

    @Test
    void statusChange_withoutReason_isRejected() {
        assertThatThrownBy(() -> service.suspend(USER_ID, command(null, null, null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
        assertThat(user.getStatus()).isEqualTo(SelfServiceUserStatus.ACTIVE);
        verify(tokenService, never()).revokeAllSessions(anyLong());
    }

    @Test
    void reactivate_closedUser_isNotAllowed() {
        user.changeStatus(SelfServiceUserStatus.CLOSED, "Client exited", STAFF_ID);

        assertThatThrownBy(() -> service.reactivate(USER_ID, command("Reopen", null, null)))
                .isInstanceOf(GeneralPlatformDomainRuleException.class);
        assertThat(user.getStatus()).isEqualTo(SelfServiceUserStatus.CLOSED);
    }

    @Test
    void viewOnly_canKeepOpenSessionsWhenAsked() {
        service.makeViewOnly(USER_ID, command("Branch review", Boolean.FALSE, null));

        assertThat(user.getStatus()).isEqualTo(SelfServiceUserStatus.VIEW_ONLY);
        assertThat(user.isEnabled()).isTrue();
        verify(tokenService, never()).revokeAllSessions(anyLong());
    }

    @Test
    void viewOnly_endsSessionsByDefault() {
        service.makeViewOnly(USER_ID, command("Branch review", null, null));

        verify(tokenService).revokeAllSessions(USER_ID);
    }

    @Test
    void reactivate_suspendedUser_restoresAccess() {
        user.changeStatus(SelfServiceUserStatus.SUSPENDED, "Review", STAFF_ID);

        service.reactivate(USER_ID, command("Review done", null, null));

        assertThat(user.getStatus()).isEqualTo(SelfServiceUserStatus.ACTIVE);
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void userInAnotherBranch_isNotFound() {
        AppSelfServiceUser otherBranchUser = userInOffice(".3.");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(otherBranchUser));

        assertThatThrownBy(() -> service.suspend(USER_ID, command("x", null, null))).isInstanceOf(SelfServiceUserNotFoundException.class);
        assertThat(otherBranchUser.getStatus()).isEqualTo(SelfServiceUserStatus.ACTIVE);
    }

    @Test
    void unlock_userThatIsNotLocked_isRejected() {
        assertThatThrownBy(() -> service.unlock(USER_ID, command(null, null, null))).isInstanceOf(GeneralPlatformDomainRuleException.class);
    }

    @Test
    void revokeSessions_endsSessionsAndNotifiesWithoutChangingStatus() {
        service.revokeSessions(USER_ID, command(null, null, null));

        verify(tokenService).revokeAllSessions(USER_ID);
        assertThat(user.getStatus()).isEqualTo(SelfServiceUserStatus.ACTIVE);
        assertThat(publishedEvent().getContextData()).containsEntry("reason", SelfServiceUserAdminWriteServiceImpl.NO_REASON);
    }

    @Test
    void resetPassword_forceChange_requiresNewPasswordAndEndsSessions() {
        service.resetPassword(USER_ID, command(null, null, "forceChange"));

        assertThat(user.isPasswordResetRequired()).isTrue();
        verify(tokenService).revokeAllSessions(USER_ID);
        verify(forgotPasswordService, never()).createForgotPasswordRequest(anyString());
    }

    @Test
    void resetPassword_sendLink_usesForgotPasswordFlow() {
        when(forgotPasswordService.createForgotPasswordRequest(anyString())).thenReturn(mock(SelfServiceRegistration.class));

        service.resetPassword(USER_ID, command(null, null, null));

        verify(forgotPasswordService).createForgotPasswordRequest("{\"username\":\"client@mail.com\"}");
        assertThat(user.isPasswordResetRequired()).isFalse();
    }

    @Test
    void resetPassword_forViewOnlyUser_isRejected() {
        user.changeStatus(SelfServiceUserStatus.VIEW_ONLY, "Review", STAFF_ID);

        assertThatThrownBy(() -> service.resetPassword(USER_ID, command(null, null, null)))
                .isInstanceOf(GeneralPlatformDomainRuleException.class);
    }

    @Test
    void updateLimits_replacesLimitsAndNotifies() {
        service.updateLimits(USER_ID,
                json("{\"limits\":[{\"currencyCode\":\"ugx\",\"perTransferLimit\":1000000,\"dailyLimit\":2000000}]}"));

        verify(jdbcTemplate).update("delete from m_selfservice_user_limit where appuser_id = ?", USER_ID);
        verify(jdbcTemplate).update(startsWith("insert into m_selfservice_user_limit"), eq(USER_ID), eq("UGX"),
                eq(new BigDecimal("1000000")), eq(new BigDecimal("2000000")), any(), eq(STAFF_ID));
        assertThat((String) publishedEvent().getContextData().get("accessChange")).contains("UGX 1000000 per transfer and 2000000 per day");
    }

    @Test
    void updateLimits_emptyListRemovesAllLimits() {
        service.updateLimits(USER_ID, json("{\"limits\":[]}"));

        verify(jdbcTemplate).update("delete from m_selfservice_user_limit where appuser_id = ?", USER_ID);
        assertThat((String) publishedEvent().getContextData().get("accessChange")).contains("removed");
    }

    @Test
    void updateLimits_dailyBelowPerTransfer_isRejected() {
        assertThatThrownBy(() -> service.updateLimits(USER_ID,
                json("{\"limits\":[{\"currencyCode\":\"UGX\",\"perTransferLimit\":500,\"dailyLimit\":100}]}")))
                .isInstanceOf(PlatformApiDataValidationException.class);
        verify(jdbcTemplate, never()).update(startsWith("delete"), anyLong());
    }

    @Test
    void updateLimits_duplicateCurrencyOrNoLimit_isRejected() {
        assertThatThrownBy(() -> service.updateLimits(USER_ID,
                json("{\"limits\":[{\"currencyCode\":\"UGX\",\"dailyLimit\":100},{\"currencyCode\":\"UGX\",\"dailyLimit\":200}]}")))
                .isInstanceOf(PlatformApiDataValidationException.class);
        assertThatThrownBy(() -> service.updateLimits(USER_ID, json("{\"limits\":[{\"currencyCode\":\"UGX\"}]}")))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void updateClients_relinksMovesOfficeAndClearsCaches() {
        Client client = client(7L, 3L, ".1.3.");
        when(clientRepository.findOneWithNotFoundDetection(7L)).thenReturn(client);
        when(jdbcTemplate.queryForObject(startsWith("select count(*) from m_selfservice_user_client_mapping"), eq(Integer.class), eq(7L),
                eq(USER_ID))).thenReturn(0);
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenReturn(cache);

        service.updateClients(USER_ID, json("{\"clientIds\":[7]}"));

        verify(jdbcTemplate).update("delete from m_selfservice_user_client_mapping where appuser_id = ?", USER_ID);
        verify(jdbcTemplate).update("insert into m_selfservice_user_client_mapping (appuser_id, client_id) values (?, ?)", USER_ID, 7L);
        verify(jdbcTemplate).update("update m_appselfservice_user set office_id = ? where id = ?", 3L, USER_ID);
        verify(cache, org.mockito.Mockito.times(3)).clear();
    }

    @Test
    void updateClients_clientOfAnotherAppUser_isRejected() {
        Client client = client(7L, 3L, ".1.3.");
        when(clientRepository.findOneWithNotFoundDetection(7L)).thenReturn(client);
        when(jdbcTemplate.queryForObject(startsWith("select count(*) from m_selfservice_user_client_mapping"), eq(Integer.class), eq(7L),
                eq(USER_ID))).thenReturn(1);

        assertThatThrownBy(() -> service.updateClients(USER_ID, json("{\"clientIds\":[7]}")))
                .isInstanceOf(GeneralPlatformDomainRuleException.class);
        verify(jdbcTemplate, never()).update(startsWith("delete"), anyLong());
    }

    @Test
    void updateClients_clientsInDifferentOffices_isRejected() {
        Client first = client(7L, 3L, ".1.3.");
        Client second = client(8L, 4L, ".1.4.");
        when(clientRepository.findOneWithNotFoundDetection(7L)).thenReturn(first);
        when(clientRepository.findOneWithNotFoundDetection(8L)).thenReturn(second);
        when(jdbcTemplate.queryForObject(startsWith("select count(*)"), eq(Integer.class), anyLong(), eq(USER_ID))).thenReturn(0);

        assertThatThrownBy(() -> service.updateClients(USER_ID, json("{\"clientIds\":[7,8]}")))
                .isInstanceOf(GeneralPlatformDomainRuleException.class);
    }

    @Test
    void updateClients_clientOutsideStaffOffices_isNotFound() {
        Client outside = client(7L, 9L, ".9.");
        when(clientRepository.findOneWithNotFoundDetection(7L)).thenReturn(outside);

        assertThatThrownBy(() -> service.updateClients(USER_ID, json("{\"clientIds\":[7]}"))).isInstanceOf(ClientNotFoundException.class);
    }

    private static Client client(final Long id, final Long officeId, final String hierarchy) {
        Office office = mock(Office.class);
        lenient().when(office.getId()).thenReturn(officeId);
        lenient().when(office.getHierarchy()).thenReturn(hierarchy);
        Client client = mock(Client.class);
        lenient().when(client.getId()).thenReturn(id);
        lenient().when(client.getOffice()).thenReturn(office);
        return client;
    }

    private static JsonCommand json(final String body) {
        JsonElement parsed = JsonParser.parseString(body);
        return JsonCommand.from(body, parsed, new FromJsonHelper(), "SELFSERVICEUSER", USER_ID, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    private SelfServiceNotificationEvent publishedEvent() {
        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        return (SelfServiceNotificationEvent) captor.getValue();
    }

    private static JsonCommand command(final String reason, final Boolean revokeSessions, final String method) {
        JsonObject json = new JsonObject();
        if (reason != null) {
            json.addProperty("reason", reason);
        }
        if (revokeSessions != null) {
            json.addProperty("revokeSessions", revokeSessions);
        }
        if (method != null) {
            json.addProperty("method", method);
        }
        return JsonCommand.from(json.toString(), json, new FromJsonHelper(), "SELFSERVICEUSER", USER_ID, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static AppSelfServiceUser userInOffice(final String hierarchy) {
        Office office = mock(Office.class);
        lenient().when(office.getHierarchy()).thenReturn(hierarchy);
        lenient().when(office.getId()).thenReturn(2L);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(List.of(new SimpleGrantedAuthority("DUMMY")));
        AppSelfServiceUser appUser = new AppSelfServiceUser(office, new User("client@mail.com", "secret", authorities), new HashSet<>(),
                "client@mail.com", "Grace", "Nakato", null, true, true, new ArrayList<>(), false);
        appUser.setId(USER_ID);
        return appUser;
    }
}
