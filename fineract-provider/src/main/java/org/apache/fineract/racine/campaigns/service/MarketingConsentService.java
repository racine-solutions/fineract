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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.campaigns.data.MarketingConsentData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether a client gets Sales and marketing messages. Clients ask the SACCO or MFI and staff record it; there is no
 * "Reply STOP". A client with no record gets them.
 */
@Service
@RequiredArgsConstructor
public class MarketingConsentService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;

    public MarketingConsentData retrieve(final Long clientId) {
        checkClient(clientId);
        final List<MarketingConsentData> found = jdbcTemplate.query("select mc.receives_marketing, mc.note, mc.changed_on_utc,"
                + " u.firstname, u.lastname, u.username from m_client_marketing_consent mc left join m_appuser u on u.id = mc.changed_by"
                + " where mc.client_id = ?",
                (rs, n) -> new MarketingConsentData(clientId, rs.getBoolean("receives_marketing"), rs.getString("note"),
                        userName(rs.getString("firstname"), rs.getString("lastname"), rs.getString("username")),
                        CommSupport.utcText(rs.getTimestamp("changed_on_utc").toLocalDateTime())),
                clientId);
        return found.isEmpty() ? new MarketingConsentData(clientId, true, null, null, null) : found.get(0);
    }

    @Transactional
    public CommandProcessingResult update(final Long clientId, final JsonCommand command) {
        checkClient(clientId);
        final Boolean receives = command.booleanObjectValueOfParameterNamed("receivesMarketing");
        if (receives == null) {
            throw CommTemplateService.invalid("receivesMarketing", "Say whether the client receives marketing messages.");
        }
        final String note = StringUtils.trimToNull(command.stringValueOfParameterNamedAllowingNull("note"));
        if (note != null && note.length() > 500) {
            throw CommTemplateService.invalid("note", "Keep the note to 500 characters.");
        }
        jdbcTemplate.update("delete from m_client_marketing_consent where client_id = ?", clientId);
        jdbcTemplate.update("insert into m_client_marketing_consent (client_id, receives_marketing, note, changed_by, changed_on_utc)"
                + " values (?, ?, ?, ?, ?)", clientId, receives, note, context.authenticatedUser().getId(), CommSupport.nowUtc());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(clientId).withClientId(clientId)
                .build();
    }

    /** Staff only reach clients of their own office and the offices under it. */
    private void checkClient(final Long clientId) {
        final Integer found = jdbcTemplate.queryForObject(
                "select count(*) from m_client c join m_office o on o.id = c.office_id" + " where c.id = ? and o.hierarchy like ?",
                Integer.class, clientId, context.officeHierarchy() + "%");
        if (found == null || found == 0) {
            throw new ResourceNotFoundException("error.msg.client.id.invalid", "Client " + clientId + " does not exist.",
                    new Object[] { clientId });
        }
    }

    static String userName(final String firstname, final String lastname, final String username) {
        final String name = StringUtils.trimToNull(StringUtils.defaultString(firstname) + " " + StringUtils.defaultString(lastname));
        return name != null ? name : username;
    }
}
