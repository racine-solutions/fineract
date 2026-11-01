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
package org.apache.fineract.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SMSNotificationWritePlatformServiceImplTest {

    @Test
    void egosmsStatusDecidesWhetherTheMessageWasAccepted() {
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("{\"Status\":\"OK\",\"Cost\":\"35\"}")).isTrue();
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("{\"Status\":\"Failed\",\"Message\":\"Low balance\"}"))
                .isFalse();
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("{\"status\":\"ok\"}")).isTrue();
    }

    @Test
    void bodiesWithoutAStatusAreTreatedAsAccepted() {
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("")).isTrue();
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("queued")).isTrue();
        assertThat(SMSNotificationWritePlatformServiceImpl.isGatewayAccepted("{\"id\":12}")).isTrue();
    }

    @Test
    void numbersAreMaskedInTheLog() {
        assertThat(SMSNotificationWritePlatformServiceImpl.maskNumber("256785154434")).isEqualTo("***434");
        assertThat(SMSNotificationWritePlatformServiceImpl.maskNumber(null)).isEqualTo("***");
    }
}
