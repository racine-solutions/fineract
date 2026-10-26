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
package org.apache.fineract.selfservice.registration.exception;

/** Raised when anonymous self-enrollment violates a uniqueness or other conflict constraint. */
public class SelfServiceEnrollmentConflictException extends RuntimeException {

    private final String parameterName;
    private final String userMessageGlobalisationCode;

    /**
     * Creates a conflict exception for a specific enrollment field.
     *
     * @param userMessageGlobalisationCode
     *            message code exposed to API clients for localization
     * @param defaultMessage
     *            fallback user-facing message
     * @param parameterName
     *            request field associated with the conflict
     */
    public SelfServiceEnrollmentConflictException(String userMessageGlobalisationCode, String defaultMessage, String parameterName) {
        super(defaultMessage);
        this.parameterName = parameterName;
        this.userMessageGlobalisationCode = userMessageGlobalisationCode;
    }

    /**
     * @return the request parameter associated with the conflict
     */
    public String getParameterName() {
        return parameterName;
    }

    /**
     * @return the globalization code describing the conflict
     */
    public String getUserMessageGlobalisationCode() {
        return userMessageGlobalisationCode;
    }
}
