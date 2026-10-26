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
package org.apache.fineract.selfservice.registration.exceptionmapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.selfservice.registration.exception.SelfServiceEnrollmentConflictException;
import org.springframework.stereotype.Component;

/**
 * Maps {@link SelfServiceEnrollmentConflictException} to an HTTP conflict response.
 *
 * @since 1.15.0
 */
@Provider
@Component
public class SelfServiceEnrollmentConflictExceptionMapper implements ExceptionMapper<SelfServiceEnrollmentConflictException> {

    /**
     * Builds the conflict response body for a self-enrollment constraint violation.
     *
     * @param exception
     *            the enrollment conflict to serialize
     * @return HTTP 409 response containing the conflict details
     */
    @Override
    public Response toResponse(SelfServiceEnrollmentConflictException exception) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("defaultUserMessage", exception.getMessage());
        error.put("parameterName", exception.getParameterName());
        error.put("developerMessage", exception.getMessage());
        error.put("userMessageGlobalisationCode", exception.getUserMessageGlobalisationCode());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("developerMessage", "The request caused a data integrity issue to be fired by the database.");
        body.put("httpStatusCode", "409");
        body.put("defaultUserMessage", exception.getMessage());
        body.put("userMessageGlobalisationCode", exception.getUserMessageGlobalisationCode());
        body.put("errors", List.of(error));

        return Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
