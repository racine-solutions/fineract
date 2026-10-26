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

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.restassured.response.Response;
import org.apache.fineract.selfservice.testing.support.SelfServiceIntegrationTestBase;
import org.apache.fineract.selfservice.testing.support.SelfServiceTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfServiceRegistrationIntegrationTest extends SelfServiceIntegrationTestBase {

    @Test
    @DisplayName("POST /v1/self/registration with missing body returns 500")
    void createRegistration_emptyBody_returns500() {
        given(SelfServiceTestUtils.requestSpec(getFineractPort())).body("{}").when().post(SelfServiceTestUtils.SELF_REGISTRATION_PATH)
                .then().log().all().statusCode(500);
    }

    @Test
    @DisplayName("POST /v1/self/registration with invalid client logic returns 404")
    void createRegistration_invalidClient_returns404() {
        String payload = """
                {
                  "accountNumber": "000000000",
                  "firstName": "Inv",
                  "lastName": "alid",
                  "username": "invaliduser",
                  "password": "Strong#Abc123",
                  "authenticationMode": "email",
                  "email": "invalid@test.com"
                }
                """;

        given(SelfServiceTestUtils.requestSpec(getFineractPort())).body(payload).when().post(SelfServiceTestUtils.SELF_REGISTRATION_PATH)
                .then().log().all().statusCode(404);
    }

    @Test
    @DisplayName("Full signup flow: seed client, request registration, then create self-service user")
    void testSuccessfulRegistrationFlow() {
        long clientId = System.currentTimeMillis();
        String accountNo = "ACC" + clientId;
        String firstName = "First" + clientId;
        String lastName = "Last" + clientId;
        String mobileNo = "555" + (clientId % 1000000);
        String email = "test" + clientId + "@fineract.test";
        String username = "user_" + clientId;
        String password = "Strong#Abc123";

        // 1. Seed the client record into the database
        executeSqlInPostgres(
                "INSERT INTO m_client (id, account_no, firstname, lastname, office_id, status_enum, mobile_no, email_address, created_by, last_modified_by) "
                        + "VALUES (%s, %s, %s, %s, 1, 300, %s, %s, 1, 1)",
                clientId, accountNo, firstName, lastName, mobileNo, email);

        // 2. Call POST /v1/self/registration to create the registration request (which sends enrollment token)
        String signupPayload = """
                {
                  "accountNumber": "%s",
                  "firstName": "%s",
                  "lastName": "%s",
                  "mobileNumber": "%s",
                  "email": "%s",
                  "username": "%s",
                  "password": "%s",
                  "authenticationMode": "email"
                }
                """.formatted(accountNo, firstName, lastName, mobileNo, email, username, password);

        Response signupResponse = given(SelfServiceTestUtils.requestSpec(getFineractPort())).body(signupPayload).when()
                .post(SelfServiceTestUtils.SELF_REGISTRATION_PATH).then().log().all().statusCode(200).extract().response();

        // Query token from the request_audit_table
        String token = querySingleValue(
                "SELECT external_authorization_token FROM request_audit_table WHERE username = ? AND request_type = 'REGISTRATION' ORDER BY id DESC LIMIT 1",
                username);
        assertNotNull(token);
        assertFalse(token.isBlank());

        // 3. Confirm signup and create the self-service user via POST /v1/self/registration/user
        String confirmPayload = """
                {
                  "externalAuthenticationToken": "%s"
                }
                """.formatted(token);

        Response confirmResponse = given(SelfServiceTestUtils.requestSpec(getFineractPort())).body(confirmPayload).when()
                .post(SelfServiceTestUtils.SELF_REGISTRATION_PATH + "/user").then().log().all().statusCode(200).extract().response();

        // 4. Verify the self-service user can now login
        Response loginResponse = SelfServiceTestUtils.authenticate(getFineractPort(), username, password);
        assertEquals(200, loginResponse.getStatusCode());
    }
}
