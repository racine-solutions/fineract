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
package org.apache.fineract.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_sms_notification_account_transaction")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class SmsNotificationAccountTransaction extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "sms_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal smsCost;

    /**
     * Derived at save time: floor(amount / smsCost).
     */
    @Column(name = "sms_credit", nullable = false)
    private Integer smsCredit;

    /**
     * Snapshot of m_sms_notification_account.sms_total_balance taken immediately before this credit is applied.
     */
    @Column(name = "previous_balance", nullable = false)
    private Integer previousBalance;

    @Column(name = "note", length = 500)
    private String note;
}
