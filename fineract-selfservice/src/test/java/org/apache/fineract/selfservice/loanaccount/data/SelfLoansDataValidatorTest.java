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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfLoansDataValidatorTest {

    private SelfLoansDataValidator validator;

    @BeforeEach
    void setUp() {
        FromJsonHelper fromJsonHelper = new FromJsonHelper();
        validator = new SelfLoansDataValidator(fromJsonHelper);
    }

    // --- validateLoanApplication ---

    @Test
    void validateLoanApplication_shouldThrowOnBlankJson() {
        assertThrows(InvalidJsonException.class, () -> validator.validateLoanApplication(""));
        assertThrows(InvalidJsonException.class, () -> validator.validateLoanApplication("   "));
    }

    @Test
    void validateLoanApplication_shouldThrowOnMissingClientId() {
        String json = "{\"loanType\": \"individual\"}";
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateLoanApplication(json));
    }

    @Test
    void validateLoanApplication_shouldThrowOnMissingLoanType() {
        String json = "{\"clientId\": \"1\"}";
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateLoanApplication(json));
    }

    @Test
    void validateLoanApplication_shouldThrowOnInvalidLoanType() {
        String json = "{\"loanType\": \"group\", \"clientId\": \"1\"}";
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateLoanApplication(json));
    }

    @Test
    void validateLoanApplication_shouldReturnClientIdOnValidInput() {
        String json = "{\"loanType\": \"individual\", \"clientId\": \"42\"}";
        HashMap<String, Object> result = validator.validateLoanApplication(json);
        assertEquals(42L, result.get("clientId"));
    }

    // --- validateModifyLoanApplication ---

    @Test
    void validateModifyLoanApplication_shouldReturnEmptyMapWhenNoParams() {
        String json = "{}";
        HashMap<String, Object> result = validator.validateModifyLoanApplication(json);
        assertFalse(result.containsKey("clientId"));
    }

    @Test
    void validateModifyLoanApplication_shouldReturnClientIdWhenPresent() {
        String json = "{\"clientId\": \"99\"}";
        HashMap<String, Object> result = validator.validateModifyLoanApplication(json);
        assertEquals(99L, result.get("clientId"));
    }

    @Test
    void validateModifyLoanApplication_shouldThrowOnInvalidLoanType() {
        String json = "{\"loanType\": \"group\"}";
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateModifyLoanApplication(json));
    }

    @Test
    void validateModifyLoanApplication_shouldAcceptIndividualLoanType() {
        String json = "{\"loanType\": \"individual\", \"clientId\": \"5\"}";
        HashMap<String, Object> result = validator.validateModifyLoanApplication(json);
        assertEquals(5L, result.get("clientId"));
    }
}
