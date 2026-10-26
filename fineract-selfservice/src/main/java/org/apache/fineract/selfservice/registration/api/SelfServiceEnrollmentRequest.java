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
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SelfServiceEnrollmentRequest {

    @Schema(example = "vilma", requiredMode = Schema.RequiredMode.REQUIRED)
    public String username;

    @Schema(example = "SecretPassword123#", requiredMode = Schema.RequiredMode.REQUIRED)
    public String password;

    @Schema(example = "Vilma", requiredMode = Schema.RequiredMode.REQUIRED)
    public String firstName;

    @Schema(example = "VILMA")
    public String firstname;

    @Schema(example = "PICAPIEDRA")
    public String middlename;

    @Schema(example = "Flintstone", requiredMode = Schema.RequiredMode.REQUIRED)
    public String lastName;

    @Schema(example = "PEREZ")
    public String lastname;

    @Schema(example = "5522649498", requiredMode = Schema.RequiredMode.REQUIRED)
    public String mobileNumber;

    @Schema(example = "vilma@hotmail.com", requiredMode = Schema.RequiredMode.REQUIRED)
    public String email;

    @Schema(example = "email", requiredMode = Schema.RequiredMode.REQUIRED)
    public String authenticationMode;

    @Schema(example = "1")
    public Long legalFormId;

    @Schema(example = "1")
    public Long officeId;

    @Schema(example = "17 febrero 2026")
    public String submittedOnDate;

    @Schema(example = "17 febrero 2026")
    public String activationDate;

    @Schema(example = "false")
    public Boolean isStaff;

    @Schema(example = "false")
    public Boolean active;

    @Schema(example = "ID12345")
    public String externalId;

    @Schema(example = "ID12345")
    public String externalID;

    @Schema(example = "dd MMMM yyyy")
    public String dateFormat;

    @Schema(example = "es")
    public String locale;
}
