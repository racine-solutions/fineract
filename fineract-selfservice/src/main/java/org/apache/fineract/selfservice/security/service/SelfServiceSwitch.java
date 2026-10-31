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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationChangedEvent;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.selfservice.audit.data.SelfServiceEventEntry;
import org.apache.fineract.selfservice.audit.service.SelfServiceEventRecorder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * The bank's mobile app on/off switch ({@code enable-racine-self-service}).
 *
 * <p>
 * While the switch is off every mobile app request is refused, including login and registration, and every mobile app
 * session is ended. Sessions are ended the moment staff save the change, and again on the first request this server
 * sees while the switch is off (in case the change was made directly in the database or on another server). If the
 * switch cannot be read, the app is treated as switched off.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfServiceSwitch {

    public static final String DISABLED_CODE = "error.msg.selfservice.disabled";
    public static final String DISABLED_MESSAGE = "The mobile app is currently unavailable. Please try again later or contact your branch.";
    static final String SWITCHED_OFF_EVENT = "SELF_SERVICE_SWITCHED_OFF";
    static final String SWITCHED_ON_EVENT = "SELF_SERVICE_SWITCHED_ON";

    /** Tenants whose sessions this server has already ended since the switch was last seen off. */
    private final Set<String> signedOutTenants = ConcurrentHashMap.newKeySet();

    private final ConfigurationDomainService configurationDomainService;
    private final SelfServiceAuthenticationTokenService tokenService;
    private final SelfServiceEventRecorder eventRecorder;
    private final PlatformSecurityContext context;

    /** True only when the switch can be read and is on. */
    public boolean isOn() {
        try {
            return configurationDomainService.isSelfServiceEnabled();
        } catch (RuntimeException e) {
            log.error("Could not read {}; refusing mobile app requests until it can be read",
                    GlobalConfigurationConstants.ENABLE_RACINE_SELF_SERVICE, e);
            return false;
        }
    }

    /** Called for each mobile app request while the switch is off: ends all sessions once per off period. */
    public void refusedWhileOff() {
        final String tenant = currentTenant();
        if (tenant != null && signedOutTenants.add(tenant)) {
            signOutEveryone(tenant, "first request while switched off");
        }
    }

    /** Called for each mobile app request while the switch is on. */
    public void allowedWhileOn() {
        final String tenant = currentTenant();
        if (tenant != null) {
            signedOutTenants.remove(tenant);
        }
    }

    /** Ends all sessions the moment staff switch the mobile app off, inside the same transaction as the change. */
    @EventListener
    public void onConfigurationChanged(final GlobalConfigurationChangedEvent event) {
        if (!GlobalConfigurationConstants.ENABLE_RACINE_SELF_SERVICE.equals(event.getName())) {
            return;
        }
        final String tenant = currentTenant();
        if (event.isEnabled()) {
            if (tenant != null) {
                signedOutTenants.remove(tenant);
            }
            log.warn("Mobile app switched ON for tenant {} by {}", tenant, staffUsername());
            record(SWITCHED_ON_EVENT, "Mobile app switched on");
            return;
        }
        if (tenant != null) {
            signedOutTenants.add(tenant);
        }
        signOutEveryone(tenant, "switched off by " + staffUsername());
        record(SWITCHED_OFF_EVENT, "Mobile app switched off; all mobile app users signed out");
    }

    private void signOutEveryone(final String tenant, final String why) {
        try {
            final long removed = tokenService.revokeAllSessionsOfAllUsers();
            log.warn("Mobile app is switched off for tenant {} ({}): ended all sessions, {} tokens removed", tenant, why, removed);
        } catch (RuntimeException e) {
            // Requests are refused anyway while the switch is off; the next refused request tries again
            if (tenant != null) {
                signedOutTenants.remove(tenant);
            }
            log.error("Could not end mobile app sessions for tenant {}", tenant, e);
        }
    }

    private void record(final String eventType, final String description) {
        eventRecorder.record(SelfServiceEventEntry.builder().eventType(eventType).category(SelfServiceEventEntry.CATEGORY_ACCESS)
                .result(SelfServiceEventEntry.RESULT_SUCCESS).actor(SelfServiceEventEntry.ACTOR_STAFF).staffUsername(staffUsername())
                .description(description).details(null).build());
    }

    private String staffUsername() {
        try {
            return context.authenticatedUser().getUsername();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String currentTenant() {
        try {
            final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
            return tenant == null ? null : tenant.getTenantIdentifier();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
