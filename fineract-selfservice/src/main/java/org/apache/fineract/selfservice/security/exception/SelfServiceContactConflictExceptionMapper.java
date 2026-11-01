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
import org.springframework.stereotype.Component;

/** Sign-up or login blocked because the phone or email is shared with another client: HTTP 409 with the reference. */
@Provider
@Component
public class SelfServiceContactConflictExceptionMapper implements ExceptionMapper<SelfServiceContactConflictException> {

    static final String CODE = "error.msg.selfservice.contact.conflict";

    @Override
    public Response toResponse(final SelfServiceContactConflictException exception) {
        final Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("reference", exception.getReference());
        extra.put("supportPhoneNumbers", exception.getSupportPhoneNumbers());
        return Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON)
                .entity(SelfServiceErrorBody.json(409, CODE, exception.getMessage(), extra)).build();
    }
}
