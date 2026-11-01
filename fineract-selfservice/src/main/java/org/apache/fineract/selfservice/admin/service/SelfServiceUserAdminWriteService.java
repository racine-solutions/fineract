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
package org.apache.fineract.selfservice.admin.service;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;

/**
 * Back-office changes to a self-service user's app access. Each runs as a command, so it is audited and permissioned.
 */
public interface SelfServiceUserAdminWriteService {

    CommandProcessingResult makeViewOnly(Long userId, JsonCommand command);

    CommandProcessingResult suspend(Long userId, JsonCommand command);

    CommandProcessingResult reactivate(Long userId, JsonCommand command);

    CommandProcessingResult close(Long userId, JsonCommand command);

    CommandProcessingResult unlock(Long userId, JsonCommand command);

    CommandProcessingResult revokeSessions(Long userId, JsonCommand command);

    CommandProcessingResult resetPassword(Long userId, JsonCommand command);

    /** Replaces the user's per-currency transfer limits. */
    CommandProcessingResult updateLimits(Long userId, JsonCommand command);

    /** Replaces the clients the user acts for; all must be in one office and not used by another app user. */
    CommandProcessingResult updateClients(Long userId, JsonCommand command);
}
