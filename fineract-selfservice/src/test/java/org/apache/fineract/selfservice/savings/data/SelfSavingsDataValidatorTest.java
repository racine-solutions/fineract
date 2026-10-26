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
package org.apache.fineract.selfservice.savings.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfSavingsDataValidatorTest {

    private SelfSavingsDataValidator validator;

    @BeforeEach
    void setUp() {
        FromJsonHelper fromJsonHelper = new FromJsonHelper();
        validator = new SelfSavingsDataValidator(fromJsonHelper);
    }

    @Test
    void validateSavingsApplication_shouldThrowOnBlankJson() {
        assertThrows(InvalidJsonException.class, () -> validator.validateSavingsApplication(""));
        assertThrows(InvalidJsonException.class, () -> validator.validateSavingsApplication("   "));
    }

    @Test
    void validateSavingsApplication_shouldThrowOnMissingClientId() {
        String json = "{\"productId\": 1}";
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateSavingsApplication(json));
    }

    @Test
    void validateSavingsApplication_shouldThrowOnNullClientId() {
        String json = "{\"clientId\": null}";
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateSavingsApplication(json));
    }

    @Test
    void validateSavingsApplication_shouldReturnClientIdOnValidInput() {
        String json = "{\"clientId\": 42}";
        HashMap<String, Object> result = validator.validateSavingsApplication(json);
        assertEquals(42L, result.get("clientId"));
    }

    @Test
    void validateSavingsApplication_shouldAcceptClientIdWithOtherFields() {
        String json = "{\"clientId\": 10, \"productId\": 1, \"locale\": \"en\"}";
        HashMap<String, Object> result = validator.validateSavingsApplication(json);
        assertEquals(10L, result.get("clientId"));
    }
}
