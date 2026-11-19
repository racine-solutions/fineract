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

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;

/**
 * How much SMS credit is left, for the low-credit banner every staff user sees. One SMS uses one unit of credit, so the
 * balance is also the number of SMS still possible. "Used" is measured against the balance right after the last top-up,
 * not the lifetime total, so the banner tracks the current top-up.
 */
@Getter
public final class SmsCreditStatusData {

    /** Global configuration holding the percent of the last top-up used before staff are warned. */
    public static final String LOW_ALERT_CONFIG = "sms-credit-low-alert-percent";
    /** Global configuration holding the percent of the last top-up used before the warning turns red. */
    public static final String CRITICAL_ALERT_CONFIG = "sms-credit-critical-alert-percent";
    /** Used when the configuration is missing or holds a value outside 1-100. */
    public static final int DEFAULT_LOW_AT_PERCENT_USED = 80;
    public static final int DEFAULT_CRITICAL_AT_PERCENT_USED = 95;

    public enum Level {
        OK, LOW, CRITICAL,
        /**
         * No SMS reaches clients at all; see {@link OffReason}. Always shown, whatever the credit alerts are set to.
         */
        SMS_OFF
    }

    /** Why no SMS reaches clients. */
    public enum OffReason {
        /** The enable-sms-notifications configuration is switched off: nothing is sent, not even app sign-in codes. */
        SMS_NOTIFICATIONS_DISABLED,
        /** The SMS account is missing or inactive: nothing is sent. */
        SMS_ACCOUNT_INACTIVE,
        /** Every event under SMS Event Configuration is off: no account SMS go out (app codes still do). */
        ALL_EVENTS_DISABLED
    }

    private final Level level;
    /** Set only when the level is SMS_OFF. */
    private final OffReason offReason;
    /** SMS still possible. */
    private final int smsBalance;
    /** Credit right after the last top-up. */
    private final int topUpBalance;
    private final int percentUsed;
    /** Cost of one SMS at the last top-up; null when there has been none. */
    private final BigDecimal smsCost;
    /** What the remaining SMS are worth at that cost; null when there has been no top-up. */
    private final BigDecimal balanceAmount;
    /** Percent used that raises the amber banner; null when that alert is switched off. */
    private final Integer lowAtPercentUsed;
    /** Percent used that turns the banner red; null when that alert is switched off. */
    private final Integer criticalAtPercentUsed;

    private SmsCreditStatusData(Level level, OffReason offReason, int smsBalance, int topUpBalance, int percentUsed, BigDecimal smsCost,
            BigDecimal balanceAmount, Integer lowAtPercentUsed, Integer criticalAtPercentUsed) {
        this.level = level;
        this.offReason = offReason;
        this.smsBalance = smsBalance;
        this.topUpBalance = topUpBalance;
        this.percentUsed = percentUsed;
        this.smsCost = smsCost;
        this.balanceAmount = balanceAmount;
        this.lowAtPercentUsed = lowAtPercentUsed;
        this.criticalAtPercentUsed = criticalAtPercentUsed;
    }

    /** No SMS reaches clients: shown in red whatever the credit is. */
    public static SmsCreditStatusData smsOff(OffReason reason, int smsBalance, int topUpBalance, BigDecimal smsCost) {
        SmsCreditStatusData credit = of(false, smsBalance, topUpBalance, smsCost, null, null);
        return new SmsCreditStatusData(Level.SMS_OFF, reason, credit.smsBalance, credit.topUpBalance, credit.percentUsed, smsCost,
                credit.balanceAmount, null, null);
    }

    /** With the default alert levels. */
    public static SmsCreditStatusData of(boolean smsSending, int smsBalance, int topUpBalance, BigDecimal smsCost) {
        return of(smsSending, smsBalance, topUpBalance, smsCost, DEFAULT_LOW_AT_PERCENT_USED, DEFAULT_CRITICAL_AT_PERCENT_USED);
    }

    /**
     * @param smsSending
     *            SMS is switched on for the tenant and the SMS account is active; when false there is nothing to warn
     *            about
     * @param lowAtPercentUsed
     *            percent used that raises the amber banner, null when that alert is switched off
     * @param criticalAtPercentUsed
     *            percent used that turns it red, null when that alert is switched off; an empty balance is always red
     *            while either alert is on
     */
    public static SmsCreditStatusData of(boolean smsSending, int smsBalance, int topUpBalance, BigDecimal smsCost, Integer lowAtPercentUsed,
            Integer criticalAtPercentUsed) {
        int balance = Math.max(0, smsBalance);
        int reference = Math.max(topUpBalance, balance);
        int percentUsed = reference == 0 ? 100 : (int) (100L * (reference - balance) / reference);
        boolean alertsOn = lowAtPercentUsed != null || criticalAtPercentUsed != null;
        Level level = Level.OK;
        if (smsSending && alertsOn) {
            if (balance == 0 || (criticalAtPercentUsed != null && percentUsed >= criticalAtPercentUsed)) {
                level = Level.CRITICAL;
            } else if (lowAtPercentUsed != null && percentUsed >= lowAtPercentUsed) {
                level = Level.LOW;
            }
        }
        BigDecimal amount = smsCost == null ? null : smsCost.multiply(BigDecimal.valueOf(balance)).setScale(2, RoundingMode.HALF_UP);
        return new SmsCreditStatusData(level, null, balance, reference, percentUsed, smsCost, amount, lowAtPercentUsed,
                criticalAtPercentUsed);
    }

    /**
     * The alert level an admin set: the configured value while the configuration is enabled, the default when it is
     * missing or holds a value outside 1-100, and null (alert off) when it is disabled.
     */
    public static Integer alertLevel(Boolean enabled, Long value, int defaultValue) {
        if (enabled == null) {
            return defaultValue;
        }
        if (!enabled) {
            return null;
        }
        return value == null || value < 1 || value > 100 ? defaultValue : value.intValue();
    }
}
