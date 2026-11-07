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

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EmailEventServiceTest {

    /** Records each update's arguments and reports one row changed. */
    private final List<Object[]> updates = new ArrayList<>();
    private EmailEventService service;

    @BeforeEach
    void setUp() {
        updates.clear();
        service = new EmailEventService(new JdbcTemplate() {

            @Override
            public int update(final String sql, final Object... args) {
                updates.add(args);
                return 1;
            }
        });
    }

    private Object[] applied(final String json) {
        assertThat(service.apply(JsonParser.parseString(json))).isEqualTo(1);
        assertThat(updates).hasSize(1);
        return updates.get(0);
    }

    @Test
    void aHardBounceFailsTheEmailWithBrevosReason() {
        final Object[] args = applied(
                "{\"event\":\"hard_bounce\",\"message-id\":\"<abc@smtp-relay.mailin.fr>\",\"reason\":\"mailbox does not exist\"}");
        assertThat(args).contains("FAILED", "HARD_BOUNCE", "mailbox does not exist", "<abc@smtp-relay.mailin.fr>",
                "abc@smtp-relay.mailin.fr");
    }

    @Test
    void eachFailureEventHasItsOwnReason() {
        assertThat(applied("{\"event\":\"invalid_email\",\"message-id\":\"<x>\"}")).contains("FAILED", "INVALID_EMAIL");
    }

    @Test
    void blockedAndSoftBounceAreFailuresToo() {
        assertThat(applied("{\"event\":\"blocked\",\"message-id\":\"<x>\"}")).contains("FAILED", "BLOCKED");
    }

    @Test
    void deliveredMarksTheEmailDelivered() {
        assertThat(applied("{\"event\":\"delivered\",\"message-id\":\"<x>\"}")).contains("SENT", "DELIVERED");
    }

    @Test
    void spamKeepsTheEmailSentAndRecordsTheComplaint() {
        final Object[] args = applied("{\"event\":\"spam\",\"message-id\":\"<x>\"}");
        assertThat(args).contains("SPAM").doesNotContain("FAILED");
    }

    @Test
    void openingsAndEventsWithoutAMessageIdChangeNothing() {
        assertThat(service.apply(JsonParser.parseString("{\"event\":\"opened\",\"message-id\":\"<x>\"}"))).isZero();
        assertThat(service.apply(JsonParser.parseString("{\"event\":\"delivered\"}"))).isZero();
        assertThat(service.apply(JsonParser.parseString("\"text\""))).isZero();
        assertThat(updates).isEmpty();
    }

    @Test
    void batchedEventsAreEachApplied() {
        assertThat(service.apply(JsonParser
                .parseString("[{\"event\":\"delivered\",\"message-id\":\"<a>\"},{\"event\":\"hard_bounce\",\"message-id\":\"<b>\"}]")))
                .isEqualTo(2);
    }
}
