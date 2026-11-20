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

import java.util.Map;

/** Names shared by the Mobile Officer App (field officer Android app) sign-in, request check and admin API. */
public final class OfficerAppConstants {

    private OfficerAppConstants() {}

    // Permissions: READ_OFFICERAPP; <ACTION>_OFFICERAPPUSER; <ACTION>_OFFICERAPPDEVICE
    public static final String READ_RESOURCE = "OFFICERAPP";
    public static final String USER_ENTITY = "OFFICERAPPUSER";
    public static final String DEVICE_ENTITY = "OFFICERAPPDEVICE";

    public static final String ACTION_TURNOFF = "TURNOFF";
    public static final String ACTION_TURNON = "TURNON";
    public static final String ACTION_SIGNOUTALL = "SIGNOUTALL";
    public static final String ACTION_UNLOCK = "UNLOCK";
    public static final String ACTION_BLOCK = "BLOCK";
    public static final String ACTION_UNBLOCK = "UNBLOCK";
    public static final String ACTION_SIGNOUT = "SIGNOUT";

    /** ?command= on POST /officerapp/officers/{userId}. */
    public static final Map<String, String> USER_COMMANDS = Map.of("turnOff", ACTION_TURNOFF, "turnOn", ACTION_TURNON, "signOutAll",
            ACTION_SIGNOUTALL, "unlock", ACTION_UNLOCK);
    /** ?command= on POST /officerapp/devices/{deviceId}. */
    public static final Map<String, String> DEVICE_COMMANDS = Map.of("block", ACTION_BLOCK, "unblock", ACTION_UNBLOCK, "signOut",
            ACTION_SIGNOUT);

    // Request headers sent by the app
    public static final String SESSION_HEADER = "X-Racine-App-Session";
    public static final String APP_HEADER = "X-Racine-App";
    public static final String DEVICE_HEADER = "X-Racine-Device";
    public static final String CAPTURED_HEADER = "X-Racine-Offline-Captured";
    /** Response header: session inactivity timeout in minutes. */
    public static final String TIMEOUT_HEADER = "X-Session-Timeout-Minutes";

    // Settings (c_configuration)
    public static final String CONFIG_ENABLED = "enable-racine-officer-app";
    public static final String CONFIG_SESSION_TIMEOUT = "officer-app-session-timeout-minutes";
    public static final String CONFIG_MINIMUM_VERSION = "officer-app-minimum-version";
    public static final String CONFIG_REQUIRE_SESSION = "officer-app-require-session";
    public static final String CONFIG_UPDATE_URL = "officer-app-update-url";
    public static final String CONFIG_ACCESS_BY_DEFAULT = "officer-app-access-by-default";
    public static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 30;

    // Refusal codes the app reacts to
    public static final String CODE_DISABLED = "error.msg.officerapp.disabled";
    public static final String CODE_ACCESS_BLOCKED = "error.msg.officerapp.access.blocked";
    public static final String CODE_DEVICE_BLOCKED = "error.msg.officerapp.device.blocked";
    public static final String CODE_SESSION_EXPIRED = "error.msg.officerapp.session.expired";
    public static final String CODE_UPDATE_REQUIRED = "error.msg.officerapp.update.required";

    public static final String MSG_DISABLED = "The field officer app is switched off for your institution. Please use the web app or contact your administrator.";
    public static final String MSG_ACCESS_BLOCKED = "Your administrator has turned off the field officer app for your account. You can still use the web app.";
    public static final String MSG_DEVICE_BLOCKED = "Your administrator has blocked this device. Contact your administrator.";
    public static final String MSG_SESSION_EXPIRED = "Your app session has ended. Please sign in again.";
    public static final String MSG_SIGNED_OUT = "An administrator signed this device out. Please sign in again.";
    public static final String MSG_UPDATE_REQUIRED = "This version of the field officer app is no longer accepted. Please install the latest version.";

    // Access and device status
    public static final String ACCESS_ALLOWED = "ALLOWED";
    public static final String ACCESS_OFF = "OFF";
    public static final String DEVICE_ACTIVE = "ACTIVE";
    public static final String DEVICE_BLOCKED = "BLOCKED";

    // Why a session ended
    public static final String END_LOGOUT = "LOGOUT";
    public static final String END_IDLE = "IDLE";
    public static final String END_REVOKED = "REVOKED";
    public static final String END_BLOCKED = "BLOCKED";
    public static final String END_SWITCHED_OFF = "SWITCHED_OFF";
    public static final String END_REPLACED = "REPLACED";

    // Online status shown on the dashboard
    public static final String ONLINE = "ONLINE";
    public static final String IDLE = "IDLE";
    public static final String SIGNED_OUT = "SIGNED_OUT";
    /** Seen within this many minutes = online. */
    public static final int ONLINE_MINUTES = 5;
    /** Last activity is saved at most this often per session. */
    public static final int TOUCH_SECONDS = 60;

    // Request body parameters
    public static final String REASON_PARAM = "reason";
    public static final String BLOCK_DEVICES_PARAM = "blockDevices";
    public static final String NOTIFY_PARAM = "notify";
    public static final int REASON_MAX_LENGTH = 500;
}
