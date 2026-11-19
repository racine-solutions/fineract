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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.racine.campaigns.domain.RepeatStop;
import org.apache.fineract.racine.campaigns.domain.RepeatUnit;
import org.apache.fineract.racine.campaigns.domain.ScheduleType;

/**
 * When a campaign's runs happen, in tenant local time. Occurrences are counted from the start, so a late run never
 * makes the next one drift (a monthly campaign on the 25th stays on the 25th).
 *
 * @param quietFrom
 *            with quietTo, the hours sending is allowed in; null when sending may happen at any time
 */
public record CampaignSchedule(ScheduleType type, LocalDateTime start, Integer repeatEvery, RepeatUnit repeatUnit, RepeatStop stop,
        LocalDate until, Integer maxRuns, LocalTime quietFrom, LocalTime quietTo) {

    /** The schedule of a campaign, from its m_comm_campaign row. */
    public static CampaignSchedule of(final java.util.Map<String, Object> row) {
        return new CampaignSchedule(ScheduleType.valueOf((String) row.get("schedule_type")), dateTime(row.get("scheduled_at")),
                row.get("repeat_every") == null ? null : ((Number) row.get("repeat_every")).intValue(),
                row.get("repeat_unit") == null ? null : RepeatUnit.valueOf((String) row.get("repeat_unit")),
                row.get("repeat_stop") == null ? null : RepeatStop.valueOf((String) row.get("repeat_stop")), date(row.get("repeat_until")),
                row.get("repeat_max_runs") == null ? null : ((Number) row.get("repeat_max_runs")).intValue(), time(row.get("quiet_from")),
                time(row.get("quiet_to")));
    }

    /** Columns read by {@link #of}. */
    public static final String COLUMNS = "schedule_type, scheduled_at, repeat_every, repeat_unit, repeat_stop, repeat_until, repeat_max_runs,"
            + " quiet_from, quiet_to";

    private static LocalDateTime dateTime(final Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }

    private static LocalDate date(final Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        return (LocalDate) value;
    }

    private static LocalTime time(final Object value) {
        return value == null ? null : LocalTime.parse((String) value);
    }

    /** The start of run number index + 1 (index 0 is the first run). */
    public LocalDateTime occurrence(final int index) {
        if (type != ScheduleType.REPEAT || index == 0) {
            return start;
        }
        final long steps = (long) index * repeatEvery;
        return switch (repeatUnit) {
            case DAYS -> start.plusDays(steps);
            case WEEKS -> start.plusWeeks(steps);
            case MONTHS -> start.plusMonths(steps);
        };
    }

    /**
     * When the next run starts, after runsDone runs, or null when the campaign is finished. A run whose time has
     * already passed (the server was down, or the last run took long) is skipped rather than sent late.
     */
    public LocalDateTime nextRun(final int runsDone, final LocalDateTime now) {
        if (type != ScheduleType.REPEAT) {
            return null;
        }
        int index = runsDone;
        LocalDateTime next = occurrence(index);
        while (!next.isAfter(now)) {
            index++;
            next = occurrence(index);
        }
        if (stop == RepeatStop.AFTER_RUNS && runsDone >= maxRuns) {
            return null;
        }
        if (stop == RepeatStop.ON_DATE && next.toLocalDate().isAfter(until)) {
            return null;
        }
        return next;
    }

    /** True when sending is allowed at this time. */
    public boolean allowsSendingAt(final LocalDateTime time) {
        if (quietFrom == null || quietTo == null) {
            return true;
        }
        final LocalTime clock = time.toLocalTime();
        return !clock.isBefore(quietFrom) && clock.isBefore(quietTo);
    }

    /** The time itself when sending is allowed then, else the next moment it is. */
    public LocalDateTime firstAllowed(final LocalDateTime time) {
        if (allowsSendingAt(time)) {
            return time;
        }
        final LocalDateTime opening = time.toLocalDate().atTime(quietFrom);
        return time.toLocalTime().isBefore(quietFrom) ? opening : opening.plusDays(1);
    }

    /** The next few run times, for staff to check the schedule. */
    public List<LocalDateTime> upcoming(final int count) {
        final List<LocalDateTime> times = new ArrayList<>();
        for (int index = 0; times.size() < count; index++) {
            final LocalDateTime next = occurrence(index);
            if ((stop == RepeatStop.AFTER_RUNS && index >= maxRuns) || (stop == RepeatStop.ON_DATE && next.toLocalDate().isAfter(until))) {
                break;
            }
            times.add(firstAllowed(next));
            if (type != ScheduleType.REPEAT) {
                break;
            }
        }
        return times;
    }
}
