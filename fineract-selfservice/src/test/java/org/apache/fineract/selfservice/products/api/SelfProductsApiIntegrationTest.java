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
package org.apache.fineract.selfservice.products.api;

import static io.restassured.RestAssured.given;

import org.apache.fineract.selfservice.testing.support.SelfServiceIntegrationTestBase;
import org.apache.fineract.selfservice.testing.support.SelfServiceTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelfProductsApiIntegrationTest extends SelfServiceIntegrationTestBase {

    @Test
    @DisplayName("GET /v1/self/loanproducts without auth returns 403")
    void retrieveAllLoanProducts_withoutAuth_returns403() {
        given(SelfServiceTestUtils.requestSpec(getFineractPort())).when().get(SelfServiceTestUtils.SELF_LOAN_PRODUCTS_PATH).then()
                .statusCode(403);
    }

    @Test
    @DisplayName("GET /v1/self/loanproducts with mifos returns 401")
    void retrieveAllLoanProducts_withSuperUser_returns401() {
        given(SelfServiceTestUtils.requestSpecWithAuth(getFineractPort(), "mifos", "password")).when()
                .get(SelfServiceTestUtils.SELF_LOAN_PRODUCTS_PATH).then().statusCode(401);
    }

    @Test
    @DisplayName("GET /v1/self/savingsproducts without auth returns 403")
    void retrieveAllSavingsProducts_withoutAuth_returns403() {
        given(SelfServiceTestUtils.requestSpec(getFineractPort())).when().get(SelfServiceTestUtils.SELF_SAVINGS_PRODUCTS_PATH).then()
                .statusCode(403);
    }

    @Test
    @DisplayName("GET /v1/self/savingsproducts with mifos returns 401")
    void retrieveAllSavingsProducts_withSuperUser_returns401() {
        given(SelfServiceTestUtils.requestSpecWithAuth(getFineractPort(), "mifos", "password")).when()
                .get(SelfServiceTestUtils.SELF_SAVINGS_PRODUCTS_PATH).then().statusCode(401);
    }
}
