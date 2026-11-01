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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the "View only" status: a view-only app user may log in and read, but every request that changes data is
 * refused with 403. Refreshing the session and logging out are still allowed.
 */
public class SelfServiceViewOnlyFilter extends OncePerRequestFilter {

    static final String ERROR_CODE = "error.msg.selfservice.user.view.only";
    static final String USER_MESSAGE = "Your mobile app access is view only. Contact your branch to make transfers or other changes.";

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final List<String> ALLOWED_WRITE_PATHS = List.of("/self/authentication/token", "/self/authentication/logout");

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        if (isBlocked(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"developerMessage\":\"" + USER_MESSAGE + "\",\"httpStatusCode\":\"403\",\"defaultUserMessage\":\""
                    + USER_MESSAGE + "\",\"userMessageGlobalisationCode\":\"" + ERROR_CODE + "\",\"errors\":[]}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean isBlocked(final HttpServletRequest request) {
        if (READ_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return false;
        }
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppSelfServiceUser user) || !user.isViewOnly()) {
            return false;
        }
        final String uri = request.getRequestURI();
        return ALLOWED_WRITE_PATHS.stream().noneMatch(uri::endsWith);
    }
}
