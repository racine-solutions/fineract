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
package org.apache.fineract.selfservice.shareaccounts.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfShareAccountsDataValidatorTest {

    private SelfShareAccountsDataValidator validator;

    @BeforeEach
    void setUp() {
        FromJsonHelper fromJsonHelper = new FromJsonHelper();
        validator = new SelfShareAccountsDataValidator(fromJsonHelper);
    }

    @Test
    void validateShareAccountApplication_shouldThrowOnBlankJson() {
        assertThrows(InvalidJsonException.class, () -> validator.validateShareAccountApplication(""));
        assertThrows(InvalidJsonException.class, () -> validator.validateShareAccountApplication("   "));
    }

    @Test
    void validateShareAccountApplication_shouldThrowOnMissingClientId() {
        String json = "{\"productId\": 1}";
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateShareAccountApplication(json));
    }

    @Test
    void validateShareAccountApplication_shouldReturnClientIdOnValidInput() {
        String json = "{\"clientId\": \"15\"}";
        HashMap<String, Object> result = validator.validateShareAccountApplication(json);
        assertEquals(15L, result.get("clientId"));
    }
}
