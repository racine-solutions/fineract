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

/**
 * Sign-up funnel for people who signed up in a period: how far each got. Registration codes sent is counted from the
 * activity timeline, so it only covers the time since the timeline was introduced.
 */
public record SelfServiceFunnelReport(String fromDate, String toDate, long registrationCodesSent, long signedUp, long confirmed,
        long loggedIn, long transacted) {
}
