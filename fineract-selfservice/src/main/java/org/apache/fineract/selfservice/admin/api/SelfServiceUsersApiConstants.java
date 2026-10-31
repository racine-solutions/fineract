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
package org.apache.fineract.selfservice.admin.api;

import java.util.Map;

public final class SelfServiceUsersApiConstants {

    private SelfServiceUsersApiConstants() {}

    /** Permission entity: READ_SELFSERVICEUSER, SUSPEND_SELFSERVICEUSER, ... */
    public static final String RESOURCE_NAME = "SELFSERVICEUSER";

    public static final String ACCESS_TOKEN_TYPE = "ACCESS";

    // Command actions (permission code = ACTION_SELFSERVICEUSER)
    public static final String ACTION_VIEWONLY = "VIEWONLY";
    public static final String ACTION_SUSPEND = "SUSPEND";
    public static final String ACTION_REACTIVATE = "REACTIVATE";
    public static final String ACTION_CLOSE = "CLOSE";
    public static final String ACTION_UNLOCK = "UNLOCK";
    public static final String ACTION_REVOKESESSIONS = "REVOKESESSIONS";
    public static final String ACTION_RESETPASSWORD = "RESETPASSWORD";
    public static final String ACTION_UPDATELIMITS = "UPDATELIMITS";
    public static final String ACTION_UPDATECLIENTS = "UPDATECLIENTS";

    /** ?command= value on POST /selfserviceusers/{id} mapped to the command action. */
    public static final Map<String, String> COMMANDS = Map.of("viewOnly", ACTION_VIEWONLY, "suspend", ACTION_SUSPEND, "reactivate",
            ACTION_REACTIVATE, "close", ACTION_CLOSE, "unlock", ACTION_UNLOCK, "revokeSessions", ACTION_REVOKESESSIONS, "resetPassword",
            ACTION_RESETPASSWORD);

    // Request body parameters
    public static final String REASON_PARAM = "reason";
    public static final String REVOKE_SESSIONS_PARAM = "revokeSessions";
    public static final String METHOD_PARAM = "method";
    public static final String METHOD_SEND_LINK = "sendLink";
    public static final String METHOD_FORCE_CHANGE = "forceChange";
    public static final int REASON_MAX_LENGTH = 500;
    public static final String LIMITS_PARAM = "limits";
    public static final String CURRENCY_CODE_PARAM = "currencyCode";
    public static final String PER_TRANSFER_LIMIT_PARAM = "perTransferLimit";
    public static final String DAILY_LIMIT_PARAM = "dailyLimit";
    public static final String CLIENT_IDS_PARAM = "clientIds";
}
