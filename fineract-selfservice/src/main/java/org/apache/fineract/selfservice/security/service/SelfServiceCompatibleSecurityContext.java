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

import java.lang.reflect.Field;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.SpringSecurityPlatformSecurityContext;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

/**
 * Extends the core {@link SpringSecurityPlatformSecurityContext} to handle both {@link AppUser} and
 * {@link AppSelfServiceUser} principals.
 *
 * <p>
 * For a self-service principal, what gets returned depends on why it's being asked for:
 *
 * <ul>
 * <li>Outside command-handler execution (e.g. resource-layer permission checks in template/list/retrieve endpoints) - a
 * minimal, never-persisted {@link AppUser} stub carrying the self-service user's own roles/office, so
 * {@code validateHasReadPermission}-style guard checks reflect that user's real grants.
 * <li>While a command handler's business logic is executing on this thread
 * ({@link ThreadLocalContextUtil#isExecutingCommandHandler()}) - core write-services routinely persist the "current
 * user" as a real {@code @ManyToOne AppUser} relationship (e.g. {@code CommandSource.maker},
 * {@code SavingsAccount.submittedBy}). {@link AppSelfServiceUser} rows live in a wholly separate table with an
 * unrelated id sequence, so the stub can never satisfy those foreign keys - a real, configured {@link AppUser} is
 * resolved instead (see {@link #resolveAuditUser()}).
 * </ul>
 */
public class SelfServiceCompatibleSecurityContext extends SpringSecurityPlatformSecurityContext {

    private static final String AUDIT_USER_PROPERTY = "fineract.selfservice.audit-user";
    private static final String DEFAULT_AUDIT_USERNAME = "mifos";

    private final AppUserRepository appUserRepository;
    private final Environment environment;

    public SelfServiceCompatibleSecurityContext(ConfigurationDomainService configurationDomainService, AppUserRepository appUserRepository,
            Environment environment) {
        super(configurationDomainService);
        this.appUserRepository = appUserRepository;
        this.environment = environment;
    }

    /**
     * Retrieves the authenticated user, wrapping self-service users in a stub - unless a command handler is currently
     * executing on this thread, in which case a real, persisted audit {@link AppUser} is returned instead (see the
     * class javadoc).
     *
     * @return the authenticated AppUser
     */
    @Override
    public AppUser authenticatedUser() {
        final Object principal = extractPrincipal();

        if (principal instanceof AppSelfServiceUser selfServiceUser) {
            return ThreadLocalContextUtil.isExecutingCommandHandler() ? resolveAuditUser() : toAppUserStub(selfServiceUser);
        }

        return super.authenticatedUser();
    }

    /**
     * Retrieves the authenticated user from the context for a specific command.
     *
     * <p>
     * For a self-service principal this deliberately does NOT return {@link #toAppUserStub}: the result here is
     * persisted as {@code CommandSource.maker}, a real FK to {@code m_appuser}, which a self-service stub id can never
     * satisfy. A real, configured {@link AppUser} is used instead.
     *
     * @param commandWrapper
     *            the command wrapper contextualizing the request
     * @return the authenticated AppUser
     */
    @Override
    public AppUser authenticatedUser(final CommandWrapper commandWrapper) {
        final Object principal = extractPrincipal();

        if (principal instanceof AppSelfServiceUser) {
            return resolveAuditUser();
        }

        return super.authenticatedUser(commandWrapper);
    }

    /**
     * Retrieves the authenticated user if one is currently present in the security context. Subject to the same
     * command-handler-execution override as {@link #authenticatedUser()}.
     *
     * @return the authenticated AppUser, or null if none
     */
    @Override
    public AppUser getAuthenticatedUserIfPresent() {
        final Object principal = extractPrincipal();

        if (principal instanceof AppSelfServiceUser selfServiceUser) {
            return ThreadLocalContextUtil.isExecutingCommandHandler() ? resolveAuditUser() : toAppUserStub(selfServiceUser);
        }

        return super.getAuthenticatedUserIfPresent();
    }

    private Object extractPrincipal() {
        final SecurityContext context = SecurityContextHolder.getContext();
        if (context != null) {
            final Authentication auth = context.getAuthentication();
            if (auth != null) {
                return auth.getPrincipal();
            }
        }
        return null;
    }

    private AppUser toAppUserStub(AppSelfServiceUser selfServiceUser) {
        final User springUser = new User(selfServiceUser.getUsername(), selfServiceUser.getPassword(), selfServiceUser.isEnabled(),
                selfServiceUser.isAccountNonExpired(), selfServiceUser.isCredentialsNonExpired(), selfServiceUser.isAccountNonLocked(),
                selfServiceUser.getAuthorities());
        final AppUser stub = new AppUser(selfServiceUser.getOffice(), springUser, selfServiceUser.getRoles(),
                selfServiceUser.getEmail() == null ? "" : selfServiceUser.getEmail(), selfServiceUser.getFirstname(),
                selfServiceUser.getLastname(), null, true, false);
        setId(stub, selfServiceUser.getId());
        return stub;
    }

    /**
     * Resolves the real, managed {@link AppUser} recorded as the maker of commands raised by self-service users,
     * configurable via {@value #AUDIT_USER_PROPERTY} (defaults to {@value #DEFAULT_AUDIT_USERNAME}).
     */
    private AppUser resolveAuditUser() {
        final String auditUsername = environment.getProperty(AUDIT_USER_PROPERTY, DEFAULT_AUDIT_USERNAME);
        final AppUser auditUser = appUserRepository.findAppUserByName(auditUsername);
        if (auditUser == null) {
            throw new IllegalStateException("Configured self-service audit user '" + auditUsername + "' does not exist. Set "
                    + AUDIT_USER_PROPERTY + " to a valid m_appuser username.");
        }
        return auditUser;
    }

    private void setId(AppUser stub, Long id) {
        try {
            Field idField = AppUser.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(stub, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to set id on AppUser stub", e);
        }
    }
}
