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
package org.apache.fineract.selfservice.registration.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Swagger schema representing the JSON payload for self-service enrollment confirmation. */
public class SelfServiceConfirmationRequest {

    @Schema(description = "The token sent to the user via email or SMS.", example = "12345678-1234-5678-1234-567812345678")
    private String externalAuthenticationToken;

    @Schema(description = "Legacy request identifier (used only if externalAuthenticationToken is not provided).", example = "123")
    private Long requestId;

    @Schema(description = "Legacy authentication token (used only if externalAuthenticationToken is not provided).", example = "a1b2c3d4")
    private String authenticationToken;

    public String getExternalAuthenticationToken() {
        return externalAuthenticationToken;
    }

    public void setExternalAuthenticationToken(String externalAuthenticationToken) {
        this.externalAuthenticationToken = externalAuthenticationToken;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getAuthenticationToken() {
        return authenticationToken;
    }

    public void setAuthenticationToken(String authenticationToken) {
        this.authenticationToken = authenticationToken;
    }
}
