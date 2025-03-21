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

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.notification.data.SmsTypeEnum;

@Entity
@Table(name = "m_sms_notification_account_message")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class SmsNotificationAccountMessage extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "message")
    private String message;
    @Column(name = "number")
    private String number;
    @Column(name = "sender_id")
    private String senderid;
    @Column(name = "sms_response")
    private String smsResponse;
    @Enumerated(EnumType.STRING)
    @Column(name = "sms_type_enum")
    private SmsTypeEnum smsTypeEnum;
    @Column(name = "has_passed")
    private Boolean hasPassed;

}
