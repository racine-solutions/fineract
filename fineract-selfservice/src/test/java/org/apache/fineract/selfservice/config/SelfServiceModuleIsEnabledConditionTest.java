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
package org.apache.fineract.selfservice.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.junit.jupiter.api.Test;

class SelfServiceModuleIsEnabledConditionTest {

    @Test
    void matches_shouldAlwaysReturnTrueRegardlessOfProperties() {
        SelfServiceModuleIsEnabledCondition condition = new SelfServiceModuleIsEnabledCondition();
        FineractProperties properties = mock(FineractProperties.class);
        // The matches method always returns true — the module is always enabled when loaded.
        // This documents that there is currently no configuration toggle for the module.
        assertTrue(condition.matches(properties));
    }

    @Test
    void matches_shouldReturnTrueEvenWithNullProperties() {
        SelfServiceModuleIsEnabledCondition condition = new SelfServiceModuleIsEnabledCondition();
        assertTrue(condition.matches(null));
    }
}
