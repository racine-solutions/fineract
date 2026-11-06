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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.racine.campaigns.data.AudienceClientData;
import org.apache.fineract.racine.campaigns.data.AudienceCountData;
import org.apache.fineract.racine.campaigns.data.AudienceFilter;
import org.apache.fineract.racine.campaigns.domain.CommPurpose;
import org.apache.fineract.racine.campaigns.domain.DeliveryReason;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Works out who a campaign reaches and who is skipped, and why. */
@Service
@RequiredArgsConstructor
public class AudienceService {

    private static final Gson GSON = new Gson();

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    /** One matching client: where the SMS goes, or why it is skipped. */
    public record Recipient(Long clientId, String accountNo, String displayName, String officeName, String mobileNo, String phone,
            DeliveryReason skipReason) {
    }

    public static List<AudienceFilter> parse(final String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            final List<AudienceFilter> filters = GSON.fromJson(json, new TypeToken<List<AudienceFilter>>() {}.getType());
            return filters == null ? List.of() : filters;
        } catch (RuntimeException e) {
            throw new PlatformApiDataValidationException("validation.msg.comm.audience.invalid", "The audience filters are not valid.",
                    "audience", e);
        }
    }

    public static String toJson(final List<AudienceFilter> filters) {
        return GSON.toJson(filters == null ? List.of() : filters);
    }

    /** Every matching client, in a stable order, with the SMS phone or the reason they are skipped. */
    public List<Recipient> recipients(final List<AudienceFilter> filters, final String officeHierarchy, final CommPurpose purpose) {
        final AudienceQueryBuilder query = AudienceQueryBuilder.of(filters, officeHierarchy, DateUtils.getBusinessLocalDate());
        final Set<String> sharedPhones = sharedPhones();
        final String countryCode = environment.getProperty("sms.default.country.code", "256");
        final List<Recipient> recipients = new ArrayList<>();
        new NamedParameterJdbcTemplate(jdbcTemplate).query(
                "select c.id, c.account_no, c.display_name, o.name office_name, c.mobile_no,"
                        + " mc.receives_marketing from m_client c join m_office o on o.id = c.office_id"
                        + " left join m_client_marketing_consent mc on mc.client_id = c.id where " + query.where() + " order by c.id",
                query.params(), rs -> {
                    final String mobile = rs.getString("mobile_no");
                    final String phone = PhoneNumbers.international(mobile, countryCode);
                    final boolean optedOut = purpose == CommPurpose.MARKETING && rs.getObject("receives_marketing") != null
                            && !rs.getBoolean("receives_marketing");
                    DeliveryReason reason = null;
                    if (optedOut) {
                        reason = DeliveryReason.OPTED_OUT;
                    } else if (PhoneNumbers.isBlank(mobile)) {
                        reason = DeliveryReason.NO_PHONE;
                    } else if (phone == null) {
                        reason = DeliveryReason.INVALID_PHONE;
                    } else if (sharedPhones.contains(PhoneNumbers.key(mobile))) {
                        // Balances must never reach another member who shares the phone
                        reason = DeliveryReason.PHONE_SHARED;
                    }
                    recipients.add(new Recipient(rs.getLong("id"), rs.getString("account_no"), rs.getString("display_name"),
                            rs.getString("office_name"), mobile, phone, reason));
                });
        return recipients;
    }

    public AudienceCountData count(final List<AudienceFilter> filters, final String officeHierarchy, final CommPurpose purpose) {
        final List<Recipient> recipients = recipients(filters, officeHierarchy, purpose);
        final Map<String, Long> skipped = recipients.stream().filter(recipient -> recipient.skipReason() != null)
                .collect(Collectors.groupingBy(recipient -> recipient.skipReason().name(), LinkedHashMap::new, Collectors.counting()));
        return new AudienceCountData(recipients.size(), recipients.size() - skipped.values().stream().mapToLong(Long::longValue).sum(),
                skipped);
    }

    /**
     * One page of the matching clients, for staff to check who the filters reach.
     *
     * @param show
     *            ALL, RECIPIENTS (will get the message) or SKIPPED
     * @param search
     *            part of a name, account number or phone
     */
    public Page<AudienceClientData> clients(final List<AudienceFilter> filters, final String officeHierarchy, final CommPurpose purpose,
            final String show, final String search, final int offset, final int limit) {
        final String term = StringUtils.trimToEmpty(search).toLowerCase(Locale.ROOT);
        final String which = StringUtils.defaultString(show).toUpperCase(Locale.ROOT);
        final List<AudienceClientData> matching = recipients(filters, officeHierarchy, purpose).stream()
                .filter(recipient -> switch (which) {
                    case "RECIPIENTS" -> recipient.skipReason() == null;
                    case "SKIPPED" -> recipient.skipReason() != null;
                    default -> true;
                })
                .filter(recipient -> term.isEmpty() || contains(recipient.displayName(), term) || contains(recipient.accountNo(), term)
                        || contains(recipient.mobileNo(), term))
                .map(recipient -> new AudienceClientData(recipient.clientId(), recipient.accountNo(), recipient.displayName(),
                        recipient.officeName(), recipient.mobileNo(),
                        recipient.skipReason() == null ? null : recipient.skipReason().name()))
                .toList();
        final int from = Math.min(Math.max(offset, 0), matching.size());
        final int to = Math.min(from + Math.max(1, Math.min(limit, 500)), matching.size());
        return new Page<>(matching.subList(from, to), matching.size());
    }

    private static boolean contains(final String value, final String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    /** Phones (last nine digits) on more than one client's record. */
    private Set<String> sharedPhones() {
        final Map<String, Integer> counts = new HashMap<>();
        jdbcTemplate.query("select mobile_no from m_client where mobile_no is not null and mobile_no <> ''", rs -> {
            final String key = PhoneNumbers.key(rs.getString("mobile_no"));
            if (key != null) {
                counts.merge(key, 1, Integer::sum);
            }
        });
        return counts.entrySet().stream().filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
    }
}
