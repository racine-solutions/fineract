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
package org.apache.fineract.selfservice.signup.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.selfservice.signup.service.SelfServicePasswordResetService;
import org.springframework.stereotype.Component;

/**
 * Forgot password with a 6-digit code (replaces /self/password/request, which stays for older app versions).
 *
 * <pre>
 * start   {identifier}  -&gt; {resetId, codeLength, codeExpiresInSeconds, resendAfterSeconds, message}   (same answer whether or not an account matched)
 * resend  {resetId}     -&gt; same as start
 * verify  {resetId, code, password, repeatPassword}  -&gt; {username}
 * </pre>
 */
@Path("/v1/self/password-reset")
@Component
@Tag(name = "Self Service Password Reset", description = "Reset a forgotten mobile app password with a 6-digit code")
@RequiredArgsConstructor
public class SelfServicePasswordResetApiResource {

    static final class ResetBody {

        String identifier;
        String resetId;
        String code;
        String password;
        String repeatPassword;
    }

    private final SelfServicePasswordResetService resetService;

    @POST
    @Path("start")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Ask for a reset code", description = "identifier: username, email or phone number")
    public String start(final String body) {
        return SelfServiceCodeJson.write(resetService.start(SelfServiceCodeJson.read(body, ResetBody.class).identifier));
    }

    @POST
    @Path("resend")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Send a new reset code")
    public String resend(final String body) {
        return SelfServiceCodeJson.write(resetService.resend(SelfServiceCodeJson.read(body, ResetBody.class).resetId));
    }

    @POST
    @Path("verify")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Set a new password with the code")
    public String verify(final String body) {
        final ResetBody request = SelfServiceCodeJson.read(body, ResetBody.class);
        return SelfServiceCodeJson.write(resetService.verify(request.resetId, request.code, request.password, request.repeatPassword));
    }
}
