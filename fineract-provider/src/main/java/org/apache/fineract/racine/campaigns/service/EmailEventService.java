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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.racine.campaigns.domain.CommChannel;
import org.apache.fineract.racine.campaigns.domain.DeliveryReason;
import org.apache.fineract.racine.campaigns.domain.DeliveryStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Applies Brevo's webhook events to campaign emails, matched by Brevo's message id: delivered, bounced, invalid,
 * blocked, failed or marked as spam. Events for emails the platform didn't send from a campaign are ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailEventService {

    /** Brevo event names that end an email as failed, and the reason they record. */
    private static final Map<String, DeliveryReason> FAILURES = Map.of("hard_bounce", DeliveryReason.HARD_BOUNCE, "soft_bounce",
            DeliveryReason.SOFT_BOUNCE, "invalid_email", DeliveryReason.INVALID_EMAIL, "blocked", DeliveryReason.BLOCKED, "error",
            DeliveryReason.SEND_ERROR);

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param payload
     *            one Brevo event, or an array of them when Brevo batches events
     * @return how many campaign emails the events changed
     */
    public int apply(final JsonElement payload) {
        if (payload == null || payload.isJsonNull()) {
            return 0;
        }
        if (payload.isJsonArray()) {
            int changed = 0;
            for (final JsonElement event : (JsonArray) payload) {
                changed += apply(event);
            }
            return changed;
        }
        if (!payload.isJsonObject()) {
            return 0;
        }
        final JsonObject event = payload.getAsJsonObject();
        final String name = StringUtils.lowerCase(text(event, "event"), Locale.ROOT);
        final String messageId = StringUtils.defaultIfBlank(text(event, "message-id"), text(event, "messageId"));
        if (name == null || StringUtils.isBlank(messageId)) {
            return 0;
        }
        final String bare = messageId.trim().replaceAll("^<|>$", "");
        final Object[] ids = { "<" + bare + ">", bare };
        final LocalDateTime now = CommSupport.nowUtc();
        final String channel = CommChannel.EMAIL.name();
        final int changed;
        if ("delivered".equals(name)) {
            // A soft bounce Brevo later delivered counts as sent again; a bounce or spam report is never overwritten
            changed = jdbcTemplate.update(
                    "update m_comm_delivery set status = ?, reason = null, provider_event = ?,"
                            + " provider_event_on_utc = ? where channel = ? and provider_message_id in (?, ?)"
                            + " and (status = ? or (status = ? and reason = ?)) and (provider_event is null or provider_event in (?, ?))",
                    DeliveryStatus.SENT.name(), "DELIVERED", now, channel, ids[0], ids[1], DeliveryStatus.SENT.name(),
                    DeliveryStatus.FAILED.name(), DeliveryReason.SOFT_BOUNCE.name(), "DEFERRED", "SOFT_BOUNCE");
        } else if ("deferred".equals(name)) {
            changed = jdbcTemplate.update(
                    "update m_comm_delivery set provider_event = ?, provider_event_on_utc = ? where channel = ?"
                            + " and provider_message_id in (?, ?) and status = ? and provider_event is null",
                    "DEFERRED", now, channel, ids[0], ids[1], DeliveryStatus.SENT.name());
        } else if ("spam".equals(name)) {
            // Delivered, but the client reported it: keep it as sent and record the complaint
            changed = jdbcTemplate.update(
                    "update m_comm_delivery set reason = ?, provider_event = ?, provider_event_on_utc = ? where channel = ?"
                            + " and provider_message_id in (?, ?) and status = ?",
                    DeliveryReason.SPAM.name(), "SPAM", now, channel, ids[0], ids[1], DeliveryStatus.SENT.name());
        } else if (FAILURES.containsKey(name)) {
            final DeliveryReason reason = FAILURES.get(name);
            final String why = StringUtils.abbreviate(StringUtils.defaultIfBlank(text(event, "reason"), name.replace('_', ' ')), 255);
            changed = jdbcTemplate.update(
                    "update m_comm_delivery set status = ?, reason = ?, provider_event = ?, provider_event_on_utc = ?,"
                            + " provider_response = ? where channel = ? and provider_message_id in (?, ?)",
                    DeliveryStatus.FAILED.name(), reason.name(), name.toUpperCase(Locale.ROOT), now, why, channel, ids[0], ids[1]);
        } else {
            // opened, click, request, unsubscribed...: nothing to record
            return 0;
        }
        if (changed > 0) {
            log.info("Brevo {} event applied to {} campaign email(s)", name, changed);
        }
        return changed;
    }

    private static String text(final JsonObject json, final String name) {
        final JsonElement value = json.get(name);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }
}
