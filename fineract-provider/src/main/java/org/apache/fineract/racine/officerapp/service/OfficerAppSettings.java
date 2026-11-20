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
package org.apache.fineract.racine.officerapp.service;

import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CONFIG_ACCESS_BY_DEFAULT;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CONFIG_ENABLED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CONFIG_MINIMUM_VERSION;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CONFIG_REQUIRE_SESSION;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CONFIG_SESSION_TIMEOUT;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CONFIG_UPDATE_URL;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.DEFAULT_SESSION_TIMEOUT_MINUTES;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationProperty;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.springframework.stereotype.Service;

/** Mobile Officer App settings from System › Configurations, with safe defaults when one cannot be read. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficerAppSettings {

    private final GlobalConfigurationRepositoryWrapper configurations;

    /** The institution-wide on/off switch. If it cannot be read the app is treated as switched off. */
    public boolean isOn() {
        final GlobalConfigurationProperty property = find(CONFIG_ENABLED);
        return property != null && property.isEnabled();
    }

    public int sessionTimeoutMinutes() {
        final GlobalConfigurationProperty property = find(CONFIG_SESSION_TIMEOUT);
        if (property == null || !property.isEnabled() || property.getValue() == null || property.getValue() <= 0) {
            return DEFAULT_SESSION_TIMEOUT_MINUTES;
        }
        return property.getValue().intValue();
    }

    /** The oldest app version allowed, or null when there is no minimum. */
    public String minimumVersion() {
        final GlobalConfigurationProperty property = find(CONFIG_MINIMUM_VERSION);
        return property != null && property.isEnabled() ? StringUtils.trimToNull(property.getStringValue()) : null;
    }

    /** Refuse phone apps that do not sign in through the Mobile Officer App (old app versions). */
    public boolean requireSession() {
        final GlobalConfigurationProperty property = find(CONFIG_REQUIRE_SESSION);
        return property != null && property.isEnabled();
    }

    public String updateUrl() {
        final GlobalConfigurationProperty property = find(CONFIG_UPDATE_URL);
        return property != null && property.isEnabled() ? StringUtils.trimToNull(property.getStringValue()) : null;
    }

    /** Staff without an explicit access decision may use the app. Defaults to true when the setting cannot be read. */
    public boolean accessByDefault() {
        final GlobalConfigurationProperty property = find(CONFIG_ACCESS_BY_DEFAULT);
        return property == null || property.isEnabled();
    }

    private GlobalConfigurationProperty find(final String name) {
        try {
            return configurations.findOneByNameWithNotFoundDetection(name);
        } catch (RuntimeException e) {
            log.warn("Could not read configuration {}: {}", name, e.getMessage());
            return null;
        }
    }
}
