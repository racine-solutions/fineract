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
package org.apache.fineract.infrastructure.core.service;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Where the current request came from (web-app, field officer app, self-service app or another API client), set by a
 * request filter and stored with every audited command.
 */
public final class RequestChannel {

    /** Request attribute holding the channel name. */
    public static final String CHANNEL_ATTRIBUTE = "RACINE_CHANNEL";
    /** Request attribute holding the field officer app device id (m_officerapp_device.id). */
    public static final String OFFICER_APP_DEVICE_ATTRIBUTE = "RACINE_OFFICERAPP_DEVICE_ID";

    public static final String WEB = "WEB";
    public static final String OFFICER_APP = "OFFICER_APP";
    public static final String SELF_SERVICE = "SELF_SERVICE";
    public static final String API = "API";

    private RequestChannel() {}

    public static String current() {
        final Object value = attribute(CHANNEL_ATTRIBUTE);
        return value == null ? null : value.toString();
    }

    public static Long currentOfficerAppDeviceId() {
        final Object value = attribute(OFFICER_APP_DEVICE_ATTRIBUTE);
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Object attribute(final String name) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getAttribute(name);
        }
        return null;
    }
}
