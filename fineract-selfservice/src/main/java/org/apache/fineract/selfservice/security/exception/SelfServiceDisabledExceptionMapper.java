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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Login refused because the mobile app user may not use the app: HTTP 401 with a code per account state, so the app can
 * tell the client why, and the support phone numbers to call. A pending user also gets the requestId of their open
 * registration request (null when the code has expired), which the code-confirmation step needs. Only reached after a
 * correct password (the state is checked after the password).
 */
@Slf4j
@Provider
@Component
@RequiredArgsConstructor
public class SelfServiceDisabledExceptionMapper implements ExceptionMapper<SelfServiceDisabledException> {

    private final SelfServiceSupportContacts supportContacts;
    private final JdbcTemplate jdbcTemplate;

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
                .entity(SelfServiceErrorBody.json(401, code, message, extra(user, status))).build();
    }

    /** Support numbers, and for a pending user the request id the code-confirmation step needs. */
    private Map<String, Object> extra(final AppSelfServiceUser user, final SelfServiceUserStatus status) {
        final Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("supportPhoneNumbers", supportContacts.phoneNumbers());
        if (status == SelfServiceUserStatus.PENDING) {
            extra.put("requestId", openRegistrationRequestId(user.getUsername()));
        }
        return extra;
    }

    /** The latest unused, unexpired enrollment or registration request of the user, or null when there is none. */
    Long openRegistrationRequestId(final String username) {
        try {
            final List<Long> ids = jdbcTemplate.queryForList(
                    "select id from request_audit_table where username = ? and request_type in ('ENROLLMENT', 'REGISTRATION')"
                            + " and consumed = false and (expires_at is null or expires_at > ?) order by id desc",
                    Long.class, username, LocalDateTime.now());
            return ids.isEmpty() ? null : ids.get(0);
        } catch (RuntimeException e) {
            log.warn("Could not look up the open registration request of a pending mobile app user", e);
            return null;
        }
    }
}
