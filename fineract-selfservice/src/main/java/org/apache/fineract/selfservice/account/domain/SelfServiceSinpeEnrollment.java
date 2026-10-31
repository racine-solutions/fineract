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
package org.apache.fineract.selfservice.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_selfservice_sinpe_enrollment")
public class SelfServiceSinpeEnrollment extends AbstractPersistableCustom<Long> {

    @Column(name = "app_selfservice_user_id", nullable = false)
    private Long appSelfServiceUserId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "mobile_number", length = 20, nullable = false)
    private String mobileNumber;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    @Column(name = "verified_on")
    private LocalDateTime verifiedOn;

    protected SelfServiceSinpeEnrollment() {}

    public SelfServiceSinpeEnrollment(Long appSelfServiceUserId, Long clientId, String mobileNumber) {
        this.appSelfServiceUserId = appSelfServiceUserId;
        this.clientId = clientId;
        this.mobileNumber = mobileNumber;
    }

    public void markAsVerified() {
        this.isVerified = true;
        this.verifiedOn = LocalDateTime.now();
    }

    // Getters
    public Long getAppSelfServiceUserId() {
        return appSelfServiceUserId;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public boolean isVerified() {
        return isVerified;
    }
}
