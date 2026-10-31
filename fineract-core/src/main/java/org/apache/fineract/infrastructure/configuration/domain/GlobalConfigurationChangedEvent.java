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
package org.apache.fineract.infrastructure.configuration.domain;

import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a global configuration property is changed through the API, so a module can react straight away (for
 * example when its feature switch is turned off). Events are delivered on a worker thread, so the event carries the
 * tenant it belongs to; listeners that use the database must set it on their thread.
 */
public class GlobalConfigurationChangedEvent extends ApplicationEvent {

    private final String name;
    private final boolean enabled;
    private final FineractPlatformTenant tenant;

    public GlobalConfigurationChangedEvent(final Object source, final String name, final boolean enabled,
            final FineractPlatformTenant tenant) {
        super(source);
        this.name = name;
        this.enabled = enabled;
        this.tenant = tenant;
    }

    /** The tenant whose configuration changed. */
    public FineractPlatformTenant getTenant() {
        return tenant;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
