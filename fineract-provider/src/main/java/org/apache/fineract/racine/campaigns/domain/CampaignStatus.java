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
package org.apache.fineract.racine.campaigns.domain;

import java.util.EnumSet;
import java.util.Set;

public enum CampaignStatus {

    DRAFT, PENDING_APPROVAL, SCHEDULED, SENDING, PAUSED, COMPLETED, STOPPED, REJECTED;

    /** Message, audience and schedule can still change. */
    public static final Set<CampaignStatus> EDITABLE = EnumSet.of(DRAFT, REJECTED, PENDING_APPROVAL, SCHEDULED);
}
