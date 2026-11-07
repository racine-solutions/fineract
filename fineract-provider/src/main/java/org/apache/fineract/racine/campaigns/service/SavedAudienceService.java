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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.campaigns.data.AudienceFilter;
import org.apache.fineract.racine.campaigns.data.SavedAudienceData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Named audiences ("Women 18-35 with savings") that can be loaded into any campaign. */
@Service
@RequiredArgsConstructor
public class SavedAudienceService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PlatformSecurityContext context;

    private static final String FROM = " from m_comm_audience a join m_office o on o.id = a.office_id left join m_appuser u on u.id = a.created_by"
            + " where o.hierarchy like ?";

    public Page<SavedAudienceData> retrieveAll(final Integer offset, final Integer limit) {
        final String scope = context.officeHierarchy() + "%";
        final Integer total = jdbcTemplate.queryForObject("select count(*)" + FROM, Integer.class, scope);
        final List<SavedAudienceData> rows = jdbcTemplate.query(
                "select a.*, o.name office_name, u.firstname, u.lastname, u.username" + FROM + " order by a.name "
                        + sqlGenerator.limit(CampaignReadService.size(limit), CampaignReadService.from(offset)),
                (rs, n) -> new SavedAudienceData(rs.getLong("id"), rs.getString("name"), rs.getString("audience_json"),
                        rs.getString("office_name"),
                        MarketingConsentService.userName(rs.getString("firstname"), rs.getString("lastname"), rs.getString("username")),
                        CommSupport.utcText(rs.getTimestamp("last_modified_on_utc").toLocalDateTime())),
                scope);
        return new Page<>(rows, total == null ? 0 : total);
    }

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final String name = name(command, null);
        final String audience = audience(command);
        final Long userId = context.authenticatedUser().getId();
        final Number id = new SimpleJdbcInsert(jdbcTemplate).withTableName("m_comm_audience").usingGeneratedKeyColumns("id")
                .usingColumns("name", "audience_json", "office_id", "created_by", "created_on_utc", "last_modified_by",
                        "last_modified_on_utc")
                .executeAndReturnKey(Map.of("name", name, "audience_json", audience, "office_id",
                        context.authenticatedUser().getOffice().getId(), "created_by", userId, "created_on_utc", CommSupport.nowUtc(),
                        "last_modified_by", userId, "last_modified_on_utc", CommSupport.nowUtc()));
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id.longValue()).build();
    }

    @Transactional
    public CommandProcessingResult update(final Long id, final JsonCommand command) {
        check(id);
        final String name = command.parameterExists("name") ? name(command, id)
                : jdbcTemplate.queryForObject("select name from m_comm_audience where id = ?", String.class, id);
        final String audience = command.parameterExists("audience") ? audience(command)
                : jdbcTemplate.queryForObject("select audience_json from m_comm_audience where id = ?", String.class, id);
        jdbcTemplate.update(
                "update m_comm_audience set name = ?, audience_json = ?, last_modified_by = ?, last_modified_on_utc = ? where id = ?", name,
                audience, context.authenticatedUser().getId(), CommSupport.nowUtc(), id);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id).build();
    }

    /** Deleting a saved audience leaves campaigns alone: each keeps its own copy of the filters. */
    @Transactional
    public CommandProcessingResult delete(final Long id, final JsonCommand command) {
        check(id);
        jdbcTemplate.update("delete from m_comm_audience where id = ?", id);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id).build();
    }

    private void check(final Long id) {
        final Integer found = jdbcTemplate.queryForObject(
                "select count(*) from m_comm_audience a join m_office o on o.id = a.office_id" + " where a.id = ? and o.hierarchy like ?",
                Integer.class, id, context.officeHierarchy() + "%");
        if (found == null || found == 0) {
            throw new ResourceNotFoundException("error.msg.comm.audience.not.found", "Saved audience " + id + " does not exist.",
                    new Object[] { id });
        }
    }

    private String name(final JsonCommand command, final Long id) {
        final String name = StringUtils.trimToNull(command.stringValueOfParameterNamedAllowingNull("name"));
        if (name == null || name.length() > 100) {
            throw CommTemplateService.invalid("name", "Give the audience a name of up to 100 characters.");
        }
        final Integer clash = jdbcTemplate.queryForObject("select count(*) from m_comm_audience where name = ? and id <> ?", Integer.class,
                name, id == null ? -1L : id);
        if (clash != null && clash > 0) {
            throw CommTemplateService.invalid("name", "An audience called \"" + name + "\" already exists. Choose another name.");
        }
        return name;
    }

    private String audience(final JsonCommand command) {
        final var json = command.parsedJson().getAsJsonObject();
        final List<AudienceFilter> filters = AudienceService.parse(json.has("audience") ? json.get("audience").toString() : "[]");
        if (filters.isEmpty()) {
            throw CommTemplateService.invalid("audience", "Add at least one filter before saving the audience.");
        }
        AudienceService.validate(filters, context.officeHierarchy());
        return AudienceService.toJson(filters);
    }
}
