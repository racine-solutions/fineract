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
package org.apache.fineract.selfservice.audit.data;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * One row to add to the self-service audit trail (m_selfservice_event). Names are copied in so the row still reads
 * correctly after the user, client or staff member changes.
 */
@Builder
public record SelfServiceEventEntry(Long appUserId, String username, Long clientId, Long officeId, String eventType, String category,
        String result, String actor, String staffUsername, String description, BigDecimal amount, String currencyCode, String ipAddress,
        String details) {

    public static final String ACTOR_CLIENT = "CLIENT";
    public static final String ACTOR_STAFF = "STAFF";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_REFUSED = "REFUSED";

    public static final String CATEGORY_ACCESS = "ACCESS";
    public static final String CATEGORY_TRANSFER = "TRANSFER";
}
