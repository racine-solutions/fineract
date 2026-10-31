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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.springframework.stereotype.Component;

/**
 * Login refused because the mobile app user may not use the app: HTTP 401 with a code per account state, so the app can
 * tell the client why. Only reached after a correct password (the state is checked after the password).
 */
@Provider
@Component
@RequiredArgsConstructor
public class SelfServiceDisabledExceptionMapper implements ExceptionMapper<SelfServiceDisabledException> {

    private final SelfServiceSupportContacts supportContacts;

    @Override
    public Response toResponse(final SelfServiceDisabledException exception) {
        final AppSelfServiceUser user = exception.getUser();
        final SelfServiceUserStatus status = user == null ? null : user.getStatus();
        final String code;
        final String message;
        if (status == SelfServiceUserStatus.PENDING) {
            code = "error.msg.selfservice.user.pending";
            message = "Finish registration by entering the code we sent you.";
        } else if (status == SelfServiceUserStatus.SUSPENDED) {
            code = "error.msg.selfservice.user.suspended";
            message = "Your app access is suspended. For help, " + supportContacts.callInstruction() + ".";
        } else if (status == SelfServiceUserStatus.CLOSED) {
            code = "error.msg.selfservice.user.closed";
            message = "Your app access has been closed. To use the app again, " + supportContacts.callInstruction() + ".";
        } else {
            code = "error.msg.selfservice.user.disabled";
            message = "Your app access is not active. For help, " + supportContacts.callInstruction() + ".";
        }
        return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON)
                .entity(SelfServiceErrorBody.json(401, code, message, Map.of("supportPhoneNumbers", supportContacts.phoneNumbers())))
                .build();
    }
}
