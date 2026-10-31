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
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.springframework.web.filter.OncePerRequestFilter;

public class SelfServiceEnablerSecurityFilter extends OncePerRequestFilter {

    private final ConfigurationDomainService configurationDomainService;

    public SelfServiceEnablerSecurityFilter(ConfigurationDomainService configurationDomainService) {
        this.configurationDomainService = configurationDomainService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean selfServiceEnabled = true;
        try {
            selfServiceEnabled = configurationDomainService.isSelfServiceEnabled();
        } catch (Exception e) {
            // Default to true when property is not yet initialized/found in the database
            selfServiceEnabled = true;
        }

        if (!selfServiceEnabled) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"developerMessage\":\"Self-service features are disabled.\",\"httpStatusCode\":\"403\",\"defaultUserMessage\":\"Self-service features are disabled.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
