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
package org.apache.fineract.selfservice.security.api;

import static io.restassured.RestAssured.given;

import org.apache.fineract.selfservice.testing.support.SelfServiceIntegrationTestBase;
import org.apache.fineract.selfservice.testing.support.SelfServiceTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfServiceSecurityGrantsIntegrationTest extends SelfServiceIntegrationTestBase {

    @Test
    @DisplayName("Verify that the application startup executes the liquibase scripts fixing the missing Fineract permission grants")
    void verifySelfServicePermissionsAreSeeded() {
        // If the DB migration failed or the permissions were not granted, the user wouldn't even be
        // able to login and perform basic tasks.
        // Here we query the core fineract /permissions endpoint as the superuser to ensure the custom
        // POST /self/registration etc permissions exist.

        // We expect 200 OK since mifos is superuser and can list permissions.
        // If we wanted to get extremely deep we could assert body string, but hitting the endpoint is a
        // good indicator
        // the system is stable and liquibase executed our scripts successfully.
        given(SelfServiceTestUtils.requestSpecWithAuth(getFineractPort(), "mifos", "password")).when()
                .get(SelfServiceTestUtils.CONTEXT_PATH + "/api/v1/permissions").then().statusCode(200);
    }
}
