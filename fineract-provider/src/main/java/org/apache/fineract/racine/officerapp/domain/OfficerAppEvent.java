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
package org.apache.fineract.racine.officerapp.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

/** One row of the officer app activity trail (m_officerapp_event). */
@Builder(toBuilder = true)
public record OfficerAppEvent(Long appUserId, String username, Long officeId, Long deviceId, String deviceLabel, String eventType,
        String category, String result, String actor, String staffUsername, Long commandId, String entityName, String actionName,
        Long resourceId, Long clientId, Long loanId, Long savingsId, BigDecimal amount, String currencyCode, String description,
        String details, String ipAddress, LocalDateTime capturedOnUtc) {

    public static final String CATEGORY_LOGIN = "LOGIN";
    public static final String CATEGORY_ACTION = "ACTION";
    public static final String CATEGORY_SYNC = "SYNC";
    public static final String CATEGORY_REPORT = "REPORT";
    public static final String CATEGORY_ACCESS = "ACCESS";
    public static final String CATEGORY_SESSION = "SESSION";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_REFUSED = "REFUSED";
    public static final String RESULT_PENDING = "PENDING";

    public static final String ACTOR_OFFICER = "OFFICER";
    public static final String ACTOR_STAFF = "STAFF";
    public static final String ACTOR_SYSTEM = "SYSTEM";
}
