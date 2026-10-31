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
package org.apache.fineract.selfservice.report.data;

import java.math.BigDecimal;
import java.util.List;

/** Money moved in the mobile app in a period, and the transfers the app refused. */
public record SelfServiceTransactionsReport(String fromDate, String toDate, List<ChannelTotal> totals, List<DailyTotal> days,
        List<RefusalTotal> refusals) {

    /** channel: ACCOUNT_TRANSFER, CONFIRMED_TRANSFER or SINPE. */
    public record ChannelTotal(String channel, String currencyCode, long transfers, BigDecimal amount, long users) {
    }

    /** day is yyyy-MM-dd (business date of the transfer). */
    public record DailyTotal(String day, String currencyCode, long transfers, BigDecimal amount) {
    }

    /** reason: OWNERSHIP, PER_TRANSFER_LIMIT, DAILY_LIMIT or OTHER. */
    public record RefusalTotal(String reason, String currencyCode, long attempts, BigDecimal amount, long users) {
    }
}
