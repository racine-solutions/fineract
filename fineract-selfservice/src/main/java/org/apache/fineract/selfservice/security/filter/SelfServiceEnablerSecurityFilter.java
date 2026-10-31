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
package org.apache.fineract.selfservice.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.selfservice.security.exception.SelfServiceErrorBody;
import org.apache.fineract.selfservice.security.service.SelfServiceSwitch;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses every mobile app request while the bank has switched the mobile app off ({@code enable-racine-self-service}),
 * with HTTP 503 and {@code error.msg.selfservice.disabled}. Runs after the tenant is resolved, so the switch is read
 * for the right tenant.
 */
public class SelfServiceEnablerSecurityFilter extends OncePerRequestFilter {

    private final SelfServiceSwitch selfServiceSwitch;

    public SelfServiceEnablerSecurityFilter(final SelfServiceSwitch selfServiceSwitch) {
        this.selfServiceSwitch = selfServiceSwitch;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !hasTenant()) {
            // Preflight requests, and requests without a tenant (refused elsewhere), carry no tenant data
            filterChain.doFilter(request, response);
            return;
        }
        if (!selfServiceSwitch.isOn()) {
            selfServiceSwitch.refusedWhileOff();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(SelfServiceErrorBody.json(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    SelfServiceSwitch.DISABLED_CODE, SelfServiceSwitch.DISABLED_MESSAGE));
            return;
        }
        selfServiceSwitch.allowedWhileOn();
        filterChain.doFilter(request, response);
    }

    private static boolean hasTenant() {
        try {
            return ThreadLocalContextUtil.getTenant() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
