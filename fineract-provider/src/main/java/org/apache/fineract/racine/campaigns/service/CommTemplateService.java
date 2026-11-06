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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.campaigns.data.CommTemplateData;
import org.apache.fineract.racine.campaigns.data.MessageCheckData;
import org.apache.fineract.racine.campaigns.domain.CommChannel;
import org.apache.fineract.racine.campaigns.domain.CommPurpose;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Communication templates. The text lives in m_template (the Templates feature's table, as a client SMS template), so
 * the Templates API still sees it; campaign details (channel, tag, purpose) live in m_comm_template_meta.
 */
@Service
@RequiredArgsConstructor
public class CommTemplateService {

    /** m_template.entity CLIENT and m_template.type SMS, stored by ordinal. */
    private static final int TEMPLATE_ENTITY_CLIENT = 0;
    private static final int TEMPLATE_TYPE_SMS = 1;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final DatabaseSpecificSQLGenerator sqlGenerator;

    private static final String SELECT = "select t.id, t.name, t.text, m.channel, m.tag, m.purpose, m.email_subject, m.archived,"
            + " (select count(*) from m_comm_campaign cc where cc.template_id = t.id) campaigns"
            + " from m_template t join m_comm_template_meta m on m.template_id = t.id";

    /** One page of templates, by name. */
    public Page<CommTemplateData> retrieveAll(final boolean includeArchived, final Integer offset, final Integer limit) {
        final Object[] params = includeArchived ? new Object[0] : new Object[] { false };
        final String where = includeArchived ? "" : " where m.archived = ?";
        final Integer total = jdbcTemplate.queryForObject(
                "select count(*) from m_template t join m_comm_template_meta m on m.template_id = t.id" + where, Integer.class, params);
        final List<CommTemplateData> rows = jdbcTemplate.query(
                SELECT + where + " order by t.name "
                        + sqlGenerator.limit(CampaignReadService.size(limit), CampaignReadService.from(offset)),
                (rs, n) -> toData(rs.getLong("id"), rs.getString("name"), rs.getString("channel"), rs.getString("tag"),
                        rs.getString("purpose"), rs.getString("email_subject"), rs.getString("text"), rs.getBoolean("archived"),
                        rs.getLong("campaigns")),
                params);
        return new Page<>(rows, total == null ? 0 : total);
    }

    public CommTemplateData retrieveOne(final Long id) {
        final List<CommTemplateData> found = jdbcTemplate.query(SELECT + " where t.id = ?",
                (rs, n) -> toData(rs.getLong("id"), rs.getString("name"), rs.getString("channel"), rs.getString("tag"),
                        rs.getString("purpose"), rs.getString("email_subject"), rs.getString("text"), rs.getBoolean("archived"),
                        rs.getLong("campaigns")),
                id);
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("error.msg.comm.template.not.found", "Communication template " + id + " does not exist.",
                    new Object[] { id });
        }
        return found.get(0);
    }

    public List<String> retrieveTags() {
        return jdbcTemplate.queryForList("select name from m_comm_tag order by name", String.class);
    }

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final Fields fields = validate(command, null);
        final Number id = new SimpleJdbcInsert(jdbcTemplate).withTableName("m_template").usingColumns("name", "text", "entity", "type")
                .usingGeneratedKeyColumns("id").executeAndReturnKey(
                        Map.of("name", fields.name, "text", fields.text, "entity", TEMPLATE_ENTITY_CLIENT, "type", TEMPLATE_TYPE_SMS));
        final Long userId = context.authenticatedUser().getId();
        jdbcTemplate.update(
                "insert into m_comm_template_meta (template_id, channel, tag, purpose, email_subject, archived, created_by,"
                        + " created_on_utc, last_modified_by, last_modified_on_utc) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id.longValue(), fields.channel.name(), fields.tag, fields.purpose.name(), fields.emailSubject, false, userId,
                CommSupport.nowUtc(), userId, CommSupport.nowUtc());
        ensureTag(fields.tag);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id.longValue()).build();
    }

    @Transactional
    public CommandProcessingResult update(final Long id, final JsonCommand command) {
        final CommTemplateData existing = retrieveOne(id);
        final Fields fields = validate(command, existing);
        jdbcTemplate.update("update m_template set name = ?, text = ? where id = ?", fields.name, fields.text, id);
        jdbcTemplate.update(
                "update m_comm_template_meta set channel = ?, tag = ?, purpose = ?, email_subject = ?, last_modified_by = ?,"
                        + " last_modified_on_utc = ? where template_id = ?",
                fields.channel.name(), fields.tag, fields.purpose.name(), fields.emailSubject, context.authenticatedUser().getId(),
                CommSupport.nowUtc(), id);
        ensureTag(fields.tag);
        final Map<String, Object> changes = new HashMap<>();
        changes.put("name", fields.name);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id).with(changes).build();
    }

    /** Templates are archived, not deleted: campaigns keep pointing at them for their history. */
    @Transactional
    public CommandProcessingResult archive(final Long id, final JsonCommand command) {
        retrieveOne(id);
        jdbcTemplate.update(
                "update m_comm_template_meta set archived = ?, last_modified_by = ?, last_modified_on_utc = ? where template_id = ?", true,
                context.authenticatedUser().getId(), CommSupport.nowUtc(), id);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id).build();
    }

    private record Fields(String name, CommChannel channel, String tag, CommPurpose purpose, String emailSubject, String text) {
    }

    private Fields validate(final JsonCommand command, final CommTemplateData existing) {
        final String name = value(command, "name", existing == null ? null : existing.name());
        final String text = value(command, "text", existing == null ? null : existing.text());
        final String tag = value(command, "tag", existing == null ? null : existing.tag());
        final String channel = value(command, "channel", existing == null ? CommChannel.SMS.name() : existing.channel());
        final String purpose = value(command, "purpose", existing == null ? CommPurpose.GENERAL.name() : existing.purpose());
        final String subject = command.parameterExists("emailSubject") ? command.stringValueOfParameterNamedAllowingNull("emailSubject")
                : existing == null ? null : existing.emailSubject();
        if (StringUtils.isBlank(name) || name.length() > 200) {
            throw invalid("name", "Give the template a name of up to 200 characters.");
        }
        final Integer clash = jdbcTemplate.queryForObject("select count(*) from m_template where name = ? and id <> ?", Integer.class,
                name.trim(), existing == null ? -1L : existing.id());
        if (clash != null && clash > 0) {
            throw invalid("name", "A template called \"" + name.trim() + "\" already exists. Choose another name.");
        }
        if (StringUtils.isBlank(tag) || tag.length() > 50) {
            throw invalid("tag", "Choose a tag, such as Loan or General.");
        }
        final CommChannel parsedChannel = parse(CommChannel.class, channel, "channel", "Channel must be SMS or EMAIL.");
        if (parsedChannel != CommChannel.SMS) {
            throw invalid("channel", "Email templates arrive with email campaigns in a later release. Choose SMS.");
        }
        final CommPurpose parsedPurpose = parse(CommPurpose.class, purpose, "purpose", "Purpose must be GENERAL or MARKETING.");
        if (StringUtils.isBlank(text)) {
            throw invalid("text", "Write the message.");
        }
        final List<String> unknown = CommPlaceholders.unknown(text);
        if (!unknown.isEmpty()) {
            throw invalid("text", "These placeholders are not available: " + String.join(", ", unknown) + ". Use the placeholder buttons.");
        }
        final MessageCheckData check = CommPlaceholders.check(text, null);
        if (!check.fits()) {
            throw invalid("text", "With the longest client details this SMS would be " + check.longestLength()
                    + " characters; one SMS holds " + check.limit() + ". Shorten it by " + (check.longestLength() - check.limit()) + ".");
        }
        return new Fields(name.trim(), parsedChannel, tag.trim(), parsedPurpose, StringUtils.trimToNull(subject), text.trim());
    }

    private void ensureTag(final String tag) {
        final Integer known = jdbcTemplate.queryForObject("select count(*) from m_comm_tag where name = ?", Integer.class, tag);
        if (known == null || known == 0) {
            jdbcTemplate.update("insert into m_comm_tag (name) values (?)", tag);
        }
    }

    private static CommTemplateData toData(final Long id, final String name, final String channel, final String tag, final String purpose,
            final String subject, final String text, final boolean archived, final Long campaigns) {
        final MessageCheckData check = CommPlaceholders.check(text, null);
        return new CommTemplateData(id, name, channel, tag, purpose, subject, text, archived, check.length(), check.longestLength(),
                check.limit(), check.unicode(), campaigns);
    }

    private static String value(final JsonCommand command, final String name, final String fallback) {
        return command.parameterExists(name) ? command.stringValueOfParameterNamedAllowingNull(name) : fallback;
    }

    static <E extends Enum<E>> E parse(final Class<E> type, final String value, final String parameter, final String message) {
        try {
            return Enum.valueOf(type, StringUtils.defaultString(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid(parameter, message, e);
        }
    }

    static PlatformApiDataValidationException invalid(final String parameter, final String message) {
        return new PlatformApiDataValidationException("validation.msg.comm." + parameter + ".invalid", message, parameter);
    }

    static PlatformApiDataValidationException invalid(final String parameter, final String message, final Throwable cause) {
        return new PlatformApiDataValidationException("validation.msg.comm." + parameter + ".invalid", message, parameter, cause);
    }
}
