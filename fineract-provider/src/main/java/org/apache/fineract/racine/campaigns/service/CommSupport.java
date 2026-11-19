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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Time handling shared by the campaign services. */
public final class CommSupport {

    /** How staff enter and read a send time (tenant local time). */
    public static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);
    public static final DateTimeFormatter MESSAGE_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private CommSupport() {}

    public static LocalDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDateTime();
    }

    /** UTC timestamp as ISO text with an offset, for the web-app to show in local time. */
    public static String utcText(final LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).toString();
    }

    public static String localText(final LocalDateTime local) {
        return local == null ? null : local.format(LOCAL_TIME);
    }
}
