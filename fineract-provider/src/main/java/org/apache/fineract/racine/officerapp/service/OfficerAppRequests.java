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

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.racine.officerapp.domain.OfficerAppConstants;

/** Reads what the field officer app sends with each request. */
public final class OfficerAppRequests {

    private OfficerAppRequests() {}

    public static String clientIp(final HttpServletRequest request) {
        final Object tracked = request.getAttribute("IP");
        if (tracked != null && StringUtils.isNotBlank(tracked.toString())) {
            return tracked.toString();
        }
        final String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** App version from "X-Racine-App: officer-app/2026.12.1 (61)", or null. */
    public static String appVersion(final HttpServletRequest request) {
        final String header = request.getHeader(OfficerAppConstants.APP_HEADER);
        if (StringUtils.isBlank(header) || !header.contains("/")) {
            return null;
        }
        final String afterSlash = header.substring(header.indexOf('/') + 1).trim();
        final int space = afterSlash.indexOf(' ');
        return StringUtils.trimToNull(space < 0 ? afterSlash : afterSlash.substring(0, space));
    }

    /** True for HTTP libraries used by phone apps (the old field officer app sends okhttp's default User-Agent). */
    public static boolean isPhoneAppClient(final HttpServletRequest request) {
        final String agent = StringUtils.defaultString(request.getHeader("User-Agent")).toLowerCase(Locale.ROOT);
        return agent.contains("okhttp") || agent.contains("ktor") || agent.contains("dalvik");
    }

    public static boolean isBrowser(final HttpServletRequest request) {
        return StringUtils.defaultString(request.getHeader("User-Agent")).startsWith("Mozilla/");
    }
}
