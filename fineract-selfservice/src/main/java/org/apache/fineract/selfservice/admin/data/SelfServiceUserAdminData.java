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
package org.apache.fineract.selfservice.admin.data;

import lombok.Builder;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

/**
 * A self-service (mobile app) user as seen by back-office staff. Timestamps are ISO-8601 UTC strings.
 */
@Getter
@Builder
public class SelfServiceUserAdminData {

    private final Long id;
    private final String username;
    private final String firstname;
    private final String lastname;
    private final String displayName;
    private final String email;
    private final String mobileNo;
    private final Long officeId;
    private final String officeName;
    private final Long clientId;
    private final String clientAccountNo;
    private final String clientDisplayName;
    private final EnumOptionData status;
    private final boolean locked;
    private final boolean passwordResetRequired;
    private final String statusReason;
    private final String statusChangedOn;
    private final String statusChangedBy;
    private final String createdOn;
    private final String lastLoginOn;
    /** Detail view only: number of open app sessions and when the newest one started. */
    private final Integer openSessions;
    private final String lastSessionStartedOn;
}
