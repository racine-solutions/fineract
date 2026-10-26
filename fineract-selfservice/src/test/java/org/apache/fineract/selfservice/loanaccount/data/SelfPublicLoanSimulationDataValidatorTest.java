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
package org.apache.fineract.selfservice.loanaccount.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfPublicLoanSimulationDataValidatorTest {

    private SelfPublicLoanSimulationDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SelfPublicLoanSimulationDataValidator(new FromJsonHelper());
    }

    @Test
    void validateCommand_calculateLoanSchedule_passes() {
        String validJson = "{\"productId\": 1, \"principal\": 10000}";
        assertDoesNotThrow(() -> validator.validatePublicSimulationRequest("calculateLoanSchedule", validJson));
    }

    @Test
    void validateCommand_null_throws() {
        String validJson = "{\"productId\": 1}";
        assertThrows(UnrecognizedQueryParamException.class, () -> validator.validatePublicSimulationRequest(null, validJson));
    }

    @Test
    void validateCommand_empty_throws() {
        String validJson = "{\"productId\": 1}";
        assertThrows(UnrecognizedQueryParamException.class, () -> validator.validatePublicSimulationRequest("", validJson));
    }

    @Test
    void validateCommand_submit_throws() {
        String validJson = "{\"productId\": 1}";
        assertThrows(UnrecognizedQueryParamException.class, () -> validator.validatePublicSimulationRequest("submit", validJson));
    }

    @Test
    void validateCommand_randomString_throws() {
        String validJson = "{\"productId\": 1}";
        assertThrows(UnrecognizedQueryParamException.class,
                () -> validator.validatePublicSimulationRequest("createLoanApplication", validJson));
    }

    @Test
    void validateBody_withClientId_throws() {
        String jsonWithClientId = "{\"productId\": 1, \"principal\": 10000, \"clientId\": 42}";
        assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validatePublicSimulationRequest("calculateLoanSchedule", jsonWithClientId));
    }

    @Test
    void validateBody_withoutClientId_passes() {
        String cleanJson = "{\"productId\": 1, \"principal\": 10000}";
        assertDoesNotThrow(() -> validator.validatePublicSimulationRequest("calculateLoanSchedule", cleanJson));
    }

    @Test
    void validateBody_blankJson_throws() {
        assertThrows(InvalidJsonException.class, () -> validator.validatePublicSimulationRequest("calculateLoanSchedule", ""));
    }

    @Test
    void validateBody_nullJson_throws() {
        assertThrows(InvalidJsonException.class, () -> validator.validatePublicSimulationRequest("calculateLoanSchedule", null));
    }

    @Test
    void validateBody_malformedJson_throwsInvalidJsonException() {
        assertThrows(InvalidJsonException.class, () -> validator.validatePublicSimulationRequest("calculateLoanSchedule", "{bad json}"));
    }
}
