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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Last-resort mapper for errors no other mapper handles (it is registered for the whole API, so Jersey only uses it
 * when nothing more specific exists). Answers in the normal Fineract error format with a fitting status, and never
 * sends stack traces or internal messages for unexpected errors.
 */
@Slf4j
@Provider
@Component
public class ThrowableMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(final Throwable exception) {
        if (exception instanceof WebApplicationException web) {
            // e.g. unknown URL (404) or wrong HTTP method (405): keep the status Jersey chose
            final int status = web.getResponse().getStatus();
            final String code = status == 404 ? "error.msg.resource.not.found" : "error.msg.request.invalid";
            final String message = status == 404 ? "The requested resource is not available." : String.valueOf(exception.getMessage());
            return build(status, code, message);
        }
        if (exception instanceof AuthenticationException) {
            return build(401, "error.msg.not.authenticated", "Unauthenticated. Please login.");
        }
        if (exception instanceof IllegalArgumentException) {
            // Used across the self-service module for invalid requests, with a message meant for the client
            return build(400, "error.msg.invalid.request", String.valueOf(exception.getMessage()));
        }
        log.error("Unexpected error answered with HTTP 500", exception);
        return build(500, "error.msg.platform.server.side.error", "An unexpected error occurred. Please try again.");
    }

    private static Response build(final int status, final String code, final String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(SelfServiceErrorBody.json(status, code, message)).build();
    }
}
