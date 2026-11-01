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
import org.apache.fineract.selfservice.signup.service.SelfServiceSignupService;
import org.springframework.stereotype.Component;

/**
 * Mobile app sign-up with a 6-digit code (replaces /self/registration, which stays for older app versions).
 *
 * <pre>
 * start   {accountNumber, firstName, lastName, mobileNumber, email, password, repeatPassword?, channel?}
 *         -&gt; {registrationId, channel, destination, smsFailed, codeLength, codeExpiresInSeconds, resendAfterSeconds}
 * resend  {registrationId, channel?}  -&gt; same as start
 * resume  {registrationId}  -&gt; {registrationId, channel, destination, codeActive, codeExpiresInSeconds, attemptsLeft, resendAfterSeconds}
 * verify  {registrationId, code}  -&gt; {userId, username, clientId}
 * </pre>
 */
@Path("/v1/self/signup")
@Component
@Tag(name = "Self Service Sign-up", description = "Sign up for the mobile app with a 6-digit code sent by SMS or email")
@RequiredArgsConstructor
public class SelfServiceSignupApiResource {

    static final class StartBody {

        String accountNumber;
        String firstName;
        String lastName;
        String mobileNumber;
        String email;
        String password;
        String repeatPassword;
        String channel;
    }

    static final class CodeBody {

        String registrationId;
        String code;
        String channel;
    }

    private final SelfServiceSignupService signupService;

    @POST
    @Path("start")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Start sign-up", description = "Checks the details against the client record and sends a 6-digit code to the phone on that record (email on the record when the SMS fails)")
    public String start(final String body) {
        final StartBody request = SelfServiceCodeJson.read(body, StartBody.class);
        return SelfServiceCodeJson
                .write(signupService.start(new SelfServiceSignupService.StartRequest(request.accountNumber, request.firstName,
                        request.lastName, request.mobileNumber, request.email, request.password, request.repeatPassword, request.channel)));
    }

    @POST
    @Path("resend")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Send a new sign-up code", description = "channel SMS (default) or EMAIL; the previous code stops working")
    public String resend(final String body) {
        final CodeBody request = SelfServiceCodeJson.read(body, CodeBody.class);
        return SelfServiceCodeJson.write(signupService.resend(request.registrationId, request.channel));
    }

    @POST
    @Path("resume")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Where a sign-up stands", description = "For an app returning to the code screen")
    public String resume(final String body) {
        final CodeBody request = SelfServiceCodeJson.read(body, CodeBody.class);
        return SelfServiceCodeJson.write(signupService.resume(request.registrationId));
    }

    @POST
    @Path("verify")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Finish sign-up", description = "Checks the code and creates the app user (username = email)")
    public String verify(final String body) {
        final CodeBody request = SelfServiceCodeJson.read(body, CodeBody.class);
        return SelfServiceCodeJson.write(signupService.verify(request.registrationId, request.code));
    }
}
