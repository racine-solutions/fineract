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

/**
 * Mobile app adoption: per office, how many active clients use the app, and per month, how many people signed up and
 * logged in for the first time.
 */
public record SelfServiceAdoptionReport(Totals totals, List<OfficeAdoption> offices, List<MonthlyAdoption> months) {

    /** Counts for all offices in the report. Adoption rate is the share of active clients using the app, in percent. */
    public record Totals(long activeClients, long clientsUsingApp, long appUsers, long activeAppUsers, long loggedInLast30Days,
            BigDecimal adoptionRate) {
    }

    public record OfficeAdoption(Long officeId, String officeName, long activeClients, long clientsUsingApp, long appUsers,
            long activeAppUsers, long loggedInLast30Days, BigDecimal adoptionRate) {
    }

    /** month is yyyy-MM (UTC). */
    public record MonthlyAdoption(String month, long signUps, long firstLogins) {
    }
}
