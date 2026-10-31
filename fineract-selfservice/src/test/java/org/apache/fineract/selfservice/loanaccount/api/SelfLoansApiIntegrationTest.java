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
package org.apache.fineract.selfservice.loanaccount.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.apache.fineract.selfservice.testing.support.SelfServiceIntegrationTestBase;
import org.apache.fineract.selfservice.testing.support.SelfServiceTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfLoansApiIntegrationTest extends SelfServiceIntegrationTestBase {

    @Test
    @DisplayName("GET /v1/self/loans/{loanId} without auth returns 403")
    void retrieveLoan_withoutAuth_returns403() {
        given(SelfServiceTestUtils.requestSpec(getFineractPort())).when().get(SelfServiceTestUtils.SELF_LOANS_PATH + "/1").then()
                .statusCode(403);
    }

    @Test
    @DisplayName("GET /v1/self/loans/{loanId} with mifos superuser returns 401 (Not a Self Service User)")
    void retrieveLoan_withSuperUser_returns401() {
        given(SelfServiceTestUtils.requestSpecWithAuth(getFineractPort(), "mifos", "password")).when()
                .get(SelfServiceTestUtils.SELF_LOANS_PATH + "/1").then().statusCode(401);
    }

    @Test
    @DisplayName("GET /v1/self/loans/{loanId} for unmapped loan returns 404")
    void retrieveLoan_unmappedLoan_returns404() {
        String clientName = UUID.randomUUID().toString().substring(0, 8);
        String selfUser = "user_loan_" + clientName;

        // Get the Fineract roleId for 'Self Service User'
        Integer roleId = given(SelfServiceTestUtils.requestSpecWithAuth(getFineractPort(), "mifos", "password"))
                .get(SelfServiceTestUtils.CONTEXT_PATH + "/api/v1/roles").jsonPath().getInt("find { it.name == 'Self Service User' }.id");

        assertThat(roleId).as("Self Service User role must exist").isNotNull();

        // Insert a self service user with no client/loan mappings
        executeSqlInPostgres("""
                INSERT INTO m_appselfservice_user(
                    office_id, username, password, email, firstname, lastname, is_deleted,
                    nonexpired, nonlocked, nonexpired_credentials, enabled, firsttime_login_remaining,
                    password_never_expires, is_self_service_user, password_reset_required
                )
                VALUES (
                    1, %s, (SELECT password FROM m_appuser WHERE username = 'mifos' LIMIT 1), %s,
                    'LoanTest', 'User', false, true, true, true, true, false, true, true, false
                );
                """.formatted(sqlLiteral(selfUser), sqlLiteral(selfUser + "@fineract.org")));

        // Retrieve a loan ID 1 (which belongs to no one or someone else) - should be 404 because unmapped
        given(SelfServiceTestUtils.requestSpecWithAuth(getFineractPort(), selfUser, "password")).when()
                .get(SelfServiceTestUtils.SELF_LOANS_PATH + "/1").then().statusCode(404);
    }

    @Test
    @DisplayName("GET /v1/self/loans/template with unmapped client returns 404")
    void retrieveTemplate_unmappedClient_returns404() {
        String clientName = UUID.randomUUID().toString().substring(0, 8);
        String selfUser = "user_tpl_" + clientName;

        executeSqlInPostgres("""
                INSERT INTO m_appselfservice_user(
                    office_id, username, password, email, firstname, lastname, is_deleted,
                    nonexpired, nonlocked, nonexpired_credentials, enabled, firsttime_login_remaining,
                    password_never_expires, is_self_service_user, password_reset_required
                )
                VALUES (
                    1, %s, (SELECT password FROM m_appuser WHERE username = 'mifos' LIMIT 1), %s,
                    'TemplateTest', 'User', false, true, true, true, true, false, true, true, false
                );
                """.formatted(sqlLiteral(selfUser), sqlLiteral(selfUser + "@fineract.org")));

        // Try accessing template for clientId 9999 (which is not mapped to this user)
        given(SelfServiceTestUtils.requestSpecWithAuth(getFineractPort(), selfUser, "password")).queryParam("clientId", 9999)
                .queryParam("templateType", "individual").when().get(SelfServiceTestUtils.SELF_LOANS_PATH + "/template").then()
                .statusCode(404);
    }
}
