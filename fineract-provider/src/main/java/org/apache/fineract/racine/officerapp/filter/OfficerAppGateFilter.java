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
package org.apache.fineract.racine.officerapp.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.racine.officerapp.service.OfficerAppGate;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Field officer app request check (see {@link OfficerAppGate}). Runs after the tenant and the staff user are known. Not
 * a Spring bean, so it only runs inside the API security chain.
 */
public class OfficerAppGateFilter extends OncePerRequestFilter {

    private final OfficerAppGate gate;

    public OfficerAppGateFilter(final OfficerAppGate gate) {
        this.gate = gate;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !hasTenant()) {
            filterChain.doFilter(request, response);
            return;
        }
        final OfficerAppGate.Refusal refusal = gate.check(request, response);
        if (refusal != null) {
            response.setStatus(refusal.status());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(refusal.body());
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            gate.afterRequest(request, response);
        }
    }

    private static boolean hasTenant() {
        try {
            final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
            return tenant != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
