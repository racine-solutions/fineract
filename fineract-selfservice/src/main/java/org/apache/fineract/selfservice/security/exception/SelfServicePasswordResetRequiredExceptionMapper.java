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
package org.apache.fineract.selfservice.security.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fineract.selfservice.security.data.SelfServiceAuthenticatedUserData;
import org.springframework.stereotype.Component;

/**
 * Login with a correct password by a user who must set a new password first (e.g. staff forced a change): HTTP 401 with
 * {@code error.msg.selfservice.password.reset.required}, {@code shouldRenewPassword: true} and the session the client
 * uses to change the password.
 */
@Provider
@Component
public class SelfServicePasswordResetRequiredExceptionMapper implements ExceptionMapper<SelfServicePasswordResetRequiredException> {

    static final String CODE = "error.msg.selfservice.password.reset.required";

    @Override
    public Response toResponse(final SelfServicePasswordResetRequiredException exception) {
        final Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("shouldRenewPassword", true);
        final SelfServiceAuthenticatedUserData data = exception.getAuthenticatedUserData();
        if (data != null) {
            extra.put("username", data.getUsername());
            extra.put("userId", data.getUserId());
            extra.put("base64EncodedAuthenticationKey", data.getBase64EncodedAuthenticationKey());
            extra.put("refreshToken", data.getRefreshToken());
        }
        return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON)
                .entity(SelfServiceErrorBody.json(401, CODE, "Please set a new password to continue.", extra)).build();
    }
}
