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
package org.apache.fineract.racine.campaigns.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.campaigns.data.CommTemplateData;
import org.apache.fineract.racine.campaigns.data.MessageCheckData;
import org.apache.fineract.racine.campaigns.data.PlaceholderData;
import org.apache.fineract.racine.campaigns.service.CommMessageRenderer;
import org.apache.fineract.racine.campaigns.service.CommPlaceholders;
import org.apache.fineract.racine.campaigns.service.CommTemplateService;
import org.springframework.stereotype.Component;

@Path("/v1/comm/templates")
@Component
@Tag(name = "Communication Templates", description = "SMS (and later email) templates for campaigns, with placeholders and preview")
@RequiredArgsConstructor
public class CommTemplatesApiResource {

    static final String RESOURCE = "COMMTEMPLATE";

    private final PlatformSecurityContext context;
    private final CommTemplateService templateService;
    private final CommMessageRenderer renderer;
    private final PortfolioCommandSourceWritePlatformService commands;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List communication templates")
    public Page<CommTemplateData> retrieveAll(@QueryParam("archived") @Parameter(description = "include archived") final Boolean archived,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit, up to 500 (default 25)") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return templateService.retrieveAll(Boolean.TRUE.equals(archived), offset, limit);
    }

    @GET
    @Path("placeholders")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Placeholders a message can use, with the longest value each can take")
    public List<PlaceholderData> retrievePlaceholders() {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return CommPlaceholders.ALL;
    }

    @GET
    @Path("tags")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Template tags")
    public List<String> retrieveTags() {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return templateService.retrieveTags();
    }

    @GET
    @Path("{templateId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a communication template")
    public CommTemplateData retrieveOne(@PathParam("templateId") final Long templateId) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return templateService.retrieveOne(templateId);
    }

    @POST
    @Path("preview")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Preview a message", description = "Body: text (or templateId), channel (SMS or EMAIL), emailSubject for "
            + "email, and an optional clientId. Without a client, example values are used. For SMS, returns the message, its length, its "
            + "longest possible length and whether it fits one SMS; for email, the filled-in subject and HTML.")
    public MessageCheckData preview(final String body) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        final JsonObject json = StringUtils.isBlank(body) ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
        String text = string(json, "text");
        String subject = string(json, "emailSubject");
        boolean email = "EMAIL".equalsIgnoreCase(string(json, "channel"));
        if (text == null && json.has("templateId")) {
            final CommTemplateData template = templateService.retrieveOne(json.get("templateId").getAsLong());
            text = template.text();
            subject = template.emailSubject();
            email = "EMAIL".equals(template.channel());
        }
        text = StringUtils.defaultString(text);
        Map<String, Object> scope = CommMessageRenderer.exampleScope();
        if (json.has("clientId") && !json.get("clientId").isJsonNull()) {
            final Long clientId = json.get("clientId").getAsLong();
            final Map<String, Object> clientScope = renderer.scopes(List.of(clientId)).get(clientId);
            if (clientScope != null) {
                scope = clientScope;
            }
        }
        if (email) {
            return CommPlaceholders.checkEmail(text, fill(text, scope, true), fill(StringUtils.defaultString(subject), scope, false));
        }
        return CommPlaceholders.check(text, fill(text, scope, false));
    }

    /** The text with the scope's values, or the text as typed when it isn't a valid template yet. */
    private String fill(final String text, final Map<String, Object> scope, final boolean html) {
        try {
            return renderer.render(html ? renderer.compileHtml(text, "preview") : renderer.compile(text, "preview"), scope);
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static String string(final JsonObject json, final String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a communication template", description = "Body: name, channel (SMS or EMAIL), tag, purpose (GENERAL or "
            + "MARKETING), text (HTML for email) and emailSubject (email only).")
    public CommandProcessingResult create(final String body) {
        return commands.logCommandSource(CommCommands.of("CREATE", RESOURCE, null, null, null, "/comm/templates", body));
    }

    @PUT
    @Path("{templateId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a communication template")
    public CommandProcessingResult update(@PathParam("templateId") final Long templateId, final String body) {
        return commands
                .logCommandSource(CommCommands.of("UPDATE", RESOURCE, templateId, null, null, "/comm/templates/" + templateId, body));
    }

    @DELETE
    @Path("{templateId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Archive a communication template", description = "Archived templates can't be used for new campaigns.")
    public CommandProcessingResult archive(@PathParam("templateId") final Long templateId) {
        return commands
                .logCommandSource(CommCommands.of("DELETE", RESOURCE, templateId, null, null, "/comm/templates/" + templateId, "{}"));
    }
}
