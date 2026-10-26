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
package org.apache.fineract.selfservice.security.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.apache.fineract.selfservice.security.domain.SelfServiceAuthenticationTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mifos.self.service.token.purge.enabled", havingValue = "true", matchIfMissing = true)
public class SelfServiceAuthTokenPurgeScheduler {

    private final SelfServiceAuthenticationTokenRepository repository;
    private final TenantDetailsService tenantDetailsService;
    private final Environment env;
    private final FineractProperties fineractProperties;

    @Scheduled(cron = "${mifos.self.service.token.purge.cron:0 0 * * * *}")
    public void purgeExpiredTokens() {
        // Ensure this only runs on batch-enabled instances to comply with Fineract's architecture
        if (!isBatchInstance()) {
            log.debug("Skipping expired auth token purge — this instance is not batch-enabled");
            return;
        }

        log.info("Starting expired auth token purge cycle");
        List<FineractPlatformTenant> tenants = tenantDetailsService.findAllTenants();

        for (FineractPlatformTenant tenant : tenants) {
            try {
                // CRITICAL: Set the tenant context BEFORE the repository call.
                // This ensures the routing datasource connects to the correct tenant schema.
                ThreadLocalContextUtil.setTenant(tenant);

                // The repository method is transactional by default in Spring Data JPA.
                // By calling it here, the transaction starts within the correct tenant context.
                repository.deleteByExpiresAtBefore(LocalDateTime.now());
            } catch (Exception e) {
                log.error("Expired auth token purge failed for tenant: {}", tenant.getTenantIdentifier(), e);
                // Continue to next tenant — one failure must not block others
            } finally {
                // CRITICAL: Always clear the context to prevent leaking to other tasks on the same thread
                ThreadLocalContextUtil.clearTenant();
            }
        }
        log.info("Expired auth token purge cycle completed for {} tenant(s)", tenants.size());
    }

    boolean isBatchInstance() {
        FineractProperties.FineractModeProperties mode = fineractProperties.getMode();
        return mode != null && (mode.isBatchWorkerEnabled() || mode.isBatchManagerEnabled());
    }
}
