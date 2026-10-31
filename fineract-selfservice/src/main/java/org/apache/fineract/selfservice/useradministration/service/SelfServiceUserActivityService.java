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
package org.apache.fineract.selfservice.useradministration.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records activity on self-service users that back-office reports and screens rely on. */
@Service
@RequiredArgsConstructor
public class SelfServiceUserActivityService {

    private final AppSelfServiceUserRepository appSelfServiceUserRepository;

    @Transactional
    public void recordSuccessfulLogin(final Long userId) {
        appSelfServiceUserRepository.updateLastLogin(userId, LocalDateTime.now(ZoneOffset.UTC));
    }
}
