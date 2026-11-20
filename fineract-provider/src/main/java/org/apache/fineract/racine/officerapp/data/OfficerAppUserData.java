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
package org.apache.fineract.racine.officerapp.data;

import java.util.List;
import lombok.Builder;

/** A staff user as seen on the Mobile Officer App dashboard. */
@Builder(toBuilder = true)
public record OfficerAppUserData(Long userId, String username, String firstname, String lastname, String displayName, String email,
        Long officeId, String officeName, Long staffId, String staffName, String mobileNo, String accessStatus, boolean accessExplicit,
        String accessReason, String accessChangedOn, String accessChangedBy, String onlineStatus, int devices, int blockedDevices,
        int liveSessions, String firstSeenOn, String lastSeenOn, String lastAction, String lastActionOn, String webStatus,
        List<String> roles, List<OfficerAppDeviceData> deviceList, Stats stats30Days) {

    public record Stats(long daysActive, long signIns, long actions, long offlineSyncs, long refused) {
    }
}
