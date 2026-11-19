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
package org.apache.fineract.racine.campaigns.data;

import java.util.List;

public record CampaignData(Long id, String name, Long templateId, String templateName, String channel, String purpose, String tag,
        String messageText, String audience, String scheduleType, String scheduledAt, String nextRunAt, String status, String lastError,
        Long officeId, String officeName, String createdBy, Long createdById, String createdOn, String lastModifiedBy,
        Long lastModifiedById, String submittedBy, String submittedOn, String approvedBy, String approvedOn, String rejectedBy,
        String rejectedOn, String rejectionReason, CampaignRunData lastRun, List<CampaignHistoryData> history, Integer repeatEvery,
        String repeatUnit, String repeatStop, String repeatUntil, Integer repeatMaxRuns, String quietFrom, String quietTo, int runsDone,
        List<String> upcomingRuns, String emailSubject) {
}
