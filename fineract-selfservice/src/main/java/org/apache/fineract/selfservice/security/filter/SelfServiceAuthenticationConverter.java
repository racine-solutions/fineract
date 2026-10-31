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

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

public class SelfServiceAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.toLowerCase().startsWith("basic ")) {
            return null;
        }

        String base64Token = header.substring(6).trim();
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(base64Token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }

        int delim = decoded.indexOf(":");
        if (delim == -1) {
            // No colon found; assume it's a self-service authentication token
            return new SelfServiceTokenAuthenticationToken(decoded);
        }

        // Standard username:password format
        return UsernamePasswordAuthenticationToken.unauthenticated(decoded.substring(0, delim), decoded.substring(delim + 1));
    }
}
