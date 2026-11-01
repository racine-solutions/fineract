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
package org.apache.fineract.selfservice.useradministration.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

class AppSelfServiceUserTest {

    @BeforeEach
    void setUp() {
        // AppSelfServiceUser constructor calls DateUtils.getLocalDateOfTenant()
        // which requires a tenant in ThreadLocalContextUtil
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "default", "Default Tenant", "UTC", null);
        ThreadLocalContextUtil.setTenant(tenant);
        // updatePassword calls DateUtils.getBusinessLocalDate() which needs business dates
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.now());
        businessDates.put(BusinessDateType.COB_DATE, LocalDate.now().minusDays(1));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    private AppSelfServiceUser createUser(Set<Role> roles) {
        Office office = mock(Office.class);
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("DUMMY_ROLE"));
        User springUser = new User("testuser", "password123", authorities);
        Collection<Client> clients = new ArrayList<>();
        return new AppSelfServiceUser(office, springUser, roles, "test@email.com", "John", "Doe", null, true, true, clients, false);
    }

    @Test
    void constructor_shouldSetAllFields() {
        AppSelfServiceUser user = createUser(new HashSet<>());

        assertEquals("testuser", user.getUsername());
        assertEquals("John", user.getFirstname());
        assertEquals("Doe", user.getLastname());
        assertEquals("test@email.com", user.getEmail());
        assertTrue(user.isEnabled());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
        assertTrue(user.isSelfServiceUser());
        assertTrue(user.getPasswordNeverExpires());
        assertNotNull(user.getLastTimePasswordUpdated());
    }

    @Test
    void getDisplayName_shouldReturnFullName() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        assertEquals("John Doe", user.getDisplayName());
    }

    @Test
    void delete_shouldSoftDelete() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        user.delete();

        assertTrue(user.isDeleted());
        assertFalse(user.isEnabled());
        assertFalse(user.isAccountNonExpired());
        assertTrue(user.getUsername().contains("DELETED"));
    }

    @Test
    void updatePassword_shouldChangePassword() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        user.updatePassword("newEncodedPassword");

        assertEquals("newEncodedPassword", user.getPassword());
    }

    @Test
    void updatePassword_shouldThrowWhenCannotChangePassword() {
        Office office = mock(Office.class);
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("DUMMY"));
        User springUser = new User("testuser", "password123", authorities);
        // cannotChangePassword = true
        AppSelfServiceUser user = new AppSelfServiceUser(office, springUser, new HashSet<>(), "test@email.com", "John", "Doe", null, true,
                true, new ArrayList<>(), true);

        assertThrows(NoAuthorizationException.class, () -> user.updatePassword("newPassword"));
    }

    @Test
    void updateRoles_shouldReplaceRoles() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        assertTrue(user.getRoles().isEmpty());

        Set<Role> newRoles = new HashSet<>();
        Role mockRole = mock(Role.class);
        newRoles.add(mockRole);
        user.updateRoles(newRoles);

        assertEquals(1, user.getRoles().size());
    }

    @Test
    void updateRoles_shouldNotReplaceWithEmptySet() {
        Set<Role> initialRoles = new HashSet<>();
        Role mockRole = mock(Role.class);
        initialRoles.add(mockRole);
        AppSelfServiceUser user = createUser(initialRoles);

        user.updateRoles(new HashSet<>());
        // Empty set should not replace
        assertEquals(1, user.getRoles().size());
    }

    @Test
    void changeOffice_shouldUpdateOffice() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        Office newOffice = mock(Office.class);
        user.changeOffice(newOffice);
        assertSame(newOffice, user.getOffice());
    }

    @Test
    void isNotEnabled_shouldBeInverseOfIsEnabled() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        assertTrue(user.isEnabled());
        assertFalse(user.isNotEnabled());
    }

    @Test
    void hasIdOf_shouldThrowNpeWhenIdIsNull() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        // Without JPA, id is null — the entity's hasIdOf() does not handle this gracefully.
        // This documents a potential bug: getId().equals() throws NPE when id is null.
        assertThrows(NullPointerException.class, () -> user.hasIdOf(999L));
    }

    @Test
    void toString_shouldContainUsername() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        String str = user.toString();
        assertTrue(str.contains("testuser"));
    }

    @Test
    void getStaffId_shouldReturnNullWhenNoStaff() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        assertNull(user.getStaffId());
        assertNull(user.getStaff());
    }

    @Test
    void newEnabledUser_isActiveWithCreatedDate() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        assertEquals(SelfServiceUserStatus.ACTIVE, user.getStatus());
        assertNotNull(user.getCreatedOnUtc());
    }

    @Test
    void newDisabledUser_isPendingUntilEnabled() {
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("DUMMY_ROLE"));
        User disabled = new User("pending", "password123", false, true, true, true, authorities);
        AppSelfServiceUser user = new AppSelfServiceUser(mock(Office.class), disabled, new HashSet<>(), "p@email.com", "Pat", "Doe", null,
                true, true, new ArrayList<>(), false);
        assertEquals(SelfServiceUserStatus.PENDING, user.getStatus());

        user.enable();

        assertEquals(SelfServiceUserStatus.ACTIVE, user.getStatus());
        assertTrue(user.isEnabled());
    }

    @Test
    void changeStatus_keepsLoginFlagInStepAndRecordsWhoAndWhy() {
        AppSelfServiceUser user = createUser(new HashSet<>());

        user.changeStatus(SelfServiceUserStatus.SUSPENDED, "Disputed transfer", 5L);
        assertFalse(user.isEnabled());
        assertEquals("Disputed transfer", user.getStatusReason());
        assertEquals(5L, user.getStatusChangedById());
        assertNotNull(user.getStatusChangedOnUtc());

        user.changeStatus(SelfServiceUserStatus.VIEW_ONLY, "Review", 5L);
        assertTrue(user.isEnabled());
        assertTrue(user.isViewOnly());

        user.changeStatus(SelfServiceUserStatus.CLOSED, "Client exited", 5L);
        assertFalse(user.isEnabled());
    }

    @Test
    void enable_doesNotReopenAViewOnlyUser() {
        AppSelfServiceUser user = createUser(new HashSet<>());
        user.changeStatus(SelfServiceUserStatus.VIEW_ONLY, "Review", 5L);

        user.enable();

        assertEquals(SelfServiceUserStatus.VIEW_ONLY, user.getStatus());
    }
}
