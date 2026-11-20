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

/** Dashboard cards. "Officers" are staff users who have used the app or have an access decision. */
@Builder
public record OfficerAppSummaryData(long devicesConnected, long onlineNow, long activeToday, long officersOnApp30Days, long officers,
        long accessOff, long registeredDevices, long blockedDevices, long oldVersionDevices, String minimumVersion, boolean appEnabled,
        int sessionTimeoutMinutes, boolean accessByDefault, boolean requireSession, List<VersionCount> appVersions) {

    public record VersionCount(String version, long devices, boolean belowMinimum) {
    }
}
