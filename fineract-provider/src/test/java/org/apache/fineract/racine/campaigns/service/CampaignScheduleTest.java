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
package org.apache.fineract.racine.campaigns.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.apache.fineract.racine.campaigns.domain.RepeatStop;
import org.apache.fineract.racine.campaigns.domain.RepeatUnit;
import org.apache.fineract.racine.campaigns.domain.ScheduleType;
import org.junit.jupiter.api.Test;

class CampaignScheduleTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 25, 9, 0);

    private static CampaignSchedule monthly(final RepeatStop stop, final LocalDate until, final Integer maxRuns) {
        return new CampaignSchedule(ScheduleType.REPEAT, START, 1, RepeatUnit.MONTHS, stop, until, maxRuns, null, null);
    }

    @Test
    void monthlyRunsStayOnTheSameDay() {
        final CampaignSchedule schedule = monthly(RepeatStop.NEVER, null, null);
        assertThat(schedule.occurrence(0)).isEqualTo(START);
        assertThat(schedule.occurrence(1)).isEqualTo(LocalDateTime.of(2026, 12, 25, 9, 0));
        assertThat(schedule.occurrence(3)).isEqualTo(LocalDateTime.of(2027, 2, 25, 9, 0));
    }

    @Test
    void everyTwoWeeks() {
        final CampaignSchedule schedule = new CampaignSchedule(ScheduleType.REPEAT, START, 2, RepeatUnit.WEEKS, RepeatStop.NEVER, null,
                null, null, null);
        assertThat(schedule.occurrence(1)).isEqualTo(START.plusWeeks(2));
    }

    @Test
    void nextRunFollowsTheLastOne() {
        final CampaignSchedule schedule = monthly(RepeatStop.NEVER, null, null);
        assertThat(schedule.nextRun(1, START.plusMinutes(5))).isEqualTo(LocalDateTime.of(2026, 12, 25, 9, 0));
    }

    @Test
    void aMissedRunIsSkippedNotSentLate() {
        final CampaignSchedule schedule = monthly(RepeatStop.NEVER, null, null);
        // The server was down through the December run
        assertThat(schedule.nextRun(1, LocalDateTime.of(2026, 12, 28, 10, 0))).isEqualTo(LocalDateTime.of(2027, 1, 25, 9, 0));
    }

    @Test
    void stopsAfterTheChosenNumberOfRuns() {
        final CampaignSchedule schedule = monthly(RepeatStop.AFTER_RUNS, null, 3);
        assertThat(schedule.nextRun(2, START.plusMonths(1).plusMinutes(5))).isNotNull();
        assertThat(schedule.nextRun(3, START.plusMonths(2).plusMinutes(5))).isNull();
        assertThat(schedule.upcoming(10)).hasSize(3);
    }

    @Test
    void stopsAfterTheEndDate() {
        final CampaignSchedule schedule = monthly(RepeatStop.ON_DATE, LocalDate.of(2027, 1, 31), null);
        assertThat(schedule.nextRun(2, START.plusMonths(1).plusMinutes(5))).isEqualTo(LocalDateTime.of(2027, 1, 25, 9, 0));
        assertThat(schedule.nextRun(3, START.plusMonths(2).plusMinutes(5))).isNull();
        assertThat(schedule.upcoming(10)).containsExactly(START, START.plusMonths(1), START.plusMonths(2));
    }

    @Test
    void quietHoursMoveSendingToTheNextAllowedTime() {
        final CampaignSchedule schedule = new CampaignSchedule(ScheduleType.ONCE, START, null, null, null, null, null, LocalTime.of(8, 0),
                LocalTime.of(20, 0));
        assertThat(schedule.allowsSendingAt(LocalDateTime.of(2026, 11, 25, 8, 0))).isTrue();
        assertThat(schedule.allowsSendingAt(LocalDateTime.of(2026, 11, 25, 20, 0))).isFalse();
        assertThat(schedule.firstAllowed(LocalDateTime.of(2026, 11, 25, 6, 30))).isEqualTo(LocalDateTime.of(2026, 11, 25, 8, 0));
        assertThat(schedule.firstAllowed(LocalDateTime.of(2026, 11, 25, 21, 15))).isEqualTo(LocalDateTime.of(2026, 11, 26, 8, 0));
        assertThat(schedule.firstAllowed(LocalDateTime.of(2026, 11, 25, 12, 0))).isEqualTo(LocalDateTime.of(2026, 11, 25, 12, 0));
    }

    @Test
    void withoutQuietHoursAnyTimeIsFine() {
        assertThat(monthly(RepeatStop.NEVER, null, null).allowsSendingAt(LocalDateTime.of(2026, 11, 25, 2, 0))).isTrue();
    }

    @Test
    void aOneOffCampaignHasNoNextRun() {
        final CampaignSchedule once = new CampaignSchedule(ScheduleType.ONCE, START, null, null, null, null, null, null, null);
        assertThat(once.nextRun(1, START.plusMinutes(1))).isNull();
        assertThat(once.upcoming(5)).containsExactly(START);
    }
}
