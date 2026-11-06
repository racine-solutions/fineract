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
package org.apache.fineract.infrastructure.event.external.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.apache.fineract.infrastructure.event.external.data.SmsCreditStatusData.Level;
import org.junit.jupiter.api.Test;

class SmsCreditStatusDataTest {

    private static final BigDecimal COST = new BigDecimal("35");

    @Test
    void underEightyPercentUsedIsFine() {
        SmsCreditStatusData status = SmsCreditStatusData.of(true, 201, 1000, COST);
        assertThat(status.getLevel()).isEqualTo(Level.OK);
        assertThat(status.getPercentUsed()).isEqualTo(79);
    }

    @Test
    void eightyPercentUsedIsLow() {
        SmsCreditStatusData status = SmsCreditStatusData.of(true, 200, 1000, COST);
        assertThat(status.getLevel()).isEqualTo(Level.LOW);
        assertThat(status.getPercentUsed()).isEqualTo(80);
        assertThat(status.getSmsBalance()).isEqualTo(200);
        assertThat(status.getBalanceAmount()).isEqualByComparingTo("7000.00");
    }

    @Test
    void ninetyFivePercentUsedOrEmptyIsCritical() {
        assertThat(SmsCreditStatusData.of(true, 50, 1000, COST).getLevel()).isEqualTo(Level.CRITICAL);
        assertThat(SmsCreditStatusData.of(true, 51, 1000, COST).getLevel()).isEqualTo(Level.LOW);
        assertThat(SmsCreditStatusData.of(true, 0, 0, null).getLevel()).isEqualTo(Level.CRITICAL);
    }

    @Test
    void nothingToWarnAboutWhenSmsIsSwitchedOff() {
        assertThat(SmsCreditStatusData.of(false, 0, 1000, COST).getLevel()).isEqualTo(Level.OK);
    }

    @Test
    void withoutATopUpThereIsNoMoneyValue() {
        assertThat(SmsCreditStatusData.of(true, 10, 1000, null).getBalanceAmount()).isNull();
    }

    @Test
    void anAdminCanMoveTheAlertLevels() {
        assertThat(SmsCreditStatusData.of(true, 400, 1000, COST, 60, 90).getLevel()).isEqualTo(Level.LOW);
        assertThat(SmsCreditStatusData.of(true, 100, 1000, COST, 60, 90).getLevel()).isEqualTo(Level.CRITICAL);
        assertThat(SmsCreditStatusData.of(true, 401, 1000, COST, 60, 90).getLevel()).isEqualTo(Level.OK);
    }

    @Test
    void aSwitchedOffAlertNeverShows() {
        assertThat(SmsCreditStatusData.of(true, 100, 1000, COST, null, 95).getLevel()).isEqualTo(Level.OK);
        assertThat(SmsCreditStatusData.of(true, 30, 1000, COST, 80, null).getLevel()).isEqualTo(Level.LOW);
        assertThat(SmsCreditStatusData.of(true, 0, 1000, COST, null, null).getLevel()).isEqualTo(Level.OK);
        assertThat(SmsCreditStatusData.of(true, 0, 1000, COST, 80, null).getLevel()).isEqualTo(Level.CRITICAL);
    }

    @Test
    void theConfiguredLevelFallsBackToTheDefault() {
        assertThat(SmsCreditStatusData.alertLevel(true, 70L, 80)).isEqualTo(70);
        assertThat(SmsCreditStatusData.alertLevel(false, 70L, 80)).isNull();
        assertThat(SmsCreditStatusData.alertLevel(null, null, 80)).isEqualTo(80);
        assertThat(SmsCreditStatusData.alertLevel(true, null, 80)).isEqualTo(80);
        assertThat(SmsCreditStatusData.alertLevel(true, 0L, 80)).isEqualTo(80);
        assertThat(SmsCreditStatusData.alertLevel(true, 150L, 95)).isEqualTo(95);
    }

    @Test
    void smsSwitchedOffIsReportedWithItsReasonAndTheCreditLeft() {
        SmsCreditStatusData status = SmsCreditStatusData.smsOff(SmsCreditStatusData.OffReason.ALL_EVENTS_DISABLED, 900, 1000, COST);
        assertThat(status.getLevel()).isEqualTo(Level.SMS_OFF);
        assertThat(status.getOffReason()).isEqualTo(SmsCreditStatusData.OffReason.ALL_EVENTS_DISABLED);
        assertThat(status.getSmsBalance()).isEqualTo(900);
        assertThat(status.getBalanceAmount()).isEqualByComparingTo("31500.00");
    }
}
