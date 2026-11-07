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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.service.BrevoEmailClient;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.campaigns.data.CampaignData;
import org.apache.fineract.racine.campaigns.data.CampaignRunData;
import org.apache.fineract.racine.campaigns.data.DeliveryData;
import org.apache.fineract.racine.campaigns.service.CampaignDispatcher;
import org.apache.fineract.racine.campaigns.service.CampaignReadService;
import org.springframework.stereotype.Component;

@Path("/v1/comm/campaigns")
@Component
@Tag(name = "Communication Campaigns", description = "SMS campaigns: create, approve, send, and the delivery report with retry")
@RequiredArgsConstructor
public class CommCampaignsApiResource {

    static final String RESOURCE = "COMMCAMPAIGN";
    private static final Set<String> COMMANDS = Set.of("submit", "approve", "reject", "pause", "resume", "stop");

    private final PlatformSecurityContext context;
    private final CampaignReadService readService;
    private final PortfolioCommandSourceWritePlatformService commands;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final BrevoEmailClient emailClient;
    private final CampaignDispatcher dispatcher;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List campaigns", description = "Optional status: DRAFT, PENDING_APPROVAL, SCHEDULED, SENDING, PAUSED, COMPLETED, "
            + "STOPPED, REJECTED.")
    public Page<CampaignData> retrieveAll(@QueryParam("status") @Parameter(description = "status") final String status,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit, up to 500 (default 25)") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return readService.retrieveAll(status, offset, limit);
    }

    @GET
    @Path("template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Defaults for a new campaign", description = "The default quiet hours (campaign-quiet-hours "
            + "configuration), whether the server can send email, the daily email limit (null for none) and how many emails are left today.")
    public java.util.Map<String, Object> template() {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        final java.util.Map<String, Object> defaults = new java.util.LinkedHashMap<>();
        final List<java.util.Map<String, Object>> setting = jdbcTemplate
                .queryForList("select enabled, string_value from c_configuration where name = 'campaign-quiet-hours'");
        String from = null;
        String to = null;
        if (!setting.isEmpty() && Boolean.TRUE.equals(setting.get(0).get("enabled"))) {
            final String[] hours = StringUtils.defaultString((String) setting.get(0).get("string_value")).split("-");
            if (hours.length == 2 && hours[0].trim().matches("\\d{2}:\\d{2}") && hours[1].trim().matches("\\d{2}:\\d{2}")) {
                from = hours[0].trim();
                to = hours[1].trim();
            }
        }
        defaults.put("quietFrom", from);
        defaults.put("quietTo", to);
        defaults.put("emailEnabled", emailClient.isEnabled());
        final List<java.util.Map<String, Object>> limit = jdbcTemplate
                .queryForList("select enabled, value from c_configuration where name = 'campaign-email-daily-limit'");
        final boolean limited = !limit.isEmpty() && Boolean.TRUE.equals(limit.get(0).get("enabled")) && limit.get(0).get("value") != null;
        defaults.put("emailDailyLimit", limited ? ((Number) limit.get(0).get("value")).longValue() : null);
        defaults.put("emailsLeftToday", limited ? (Object) dispatcher.emailAllowanceToday() : null);
        return defaults;
    }

    @GET
    @Path("{campaignId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a campaign with its history and latest run")
    public CampaignData retrieveOne(@PathParam("campaignId") final Long campaignId) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return readService.retrieveOne(campaignId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a campaign", description = "Body: name, templateId, audience (filters), scheduleType (NOW or ONCE), "
            + "scheduledAt (yyyy-MM-dd HH:mm, tenant time, for ONCE). Starts as a draft.")
    public CommandProcessingResult create(final String body) {
        return commands.logCommandSource(CommCommands.of("CREATE", RESOURCE, null, null, null, "/comm/campaigns", body));
    }

    @PUT
    @Path("{campaignId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a campaign", description = "A submitted or approved campaign goes back to pending approval.")
    public CommandProcessingResult update(@PathParam("campaignId") final Long campaignId, final String body) {
        return commands
                .logCommandSource(CommCommands.of("UPDATE", RESOURCE, campaignId, null, null, "/comm/campaigns/" + campaignId, body));
    }

    @POST
    @Path("{campaignId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Act on a campaign", description = "command: submit, approve, reject (body: reason), pause, resume, stop.")
    public CommandProcessingResult act(@PathParam("campaignId") final Long campaignId,
            @QueryParam("command") @Parameter(description = "command") final String command, final String body) {
        if (command == null || !COMMANDS.contains(command.toLowerCase(Locale.ROOT))) {
            throw new UnrecognizedQueryParamException("command", command, COMMANDS.toArray());
        }
        return commands.logCommandSource(
                CommCommands.of(command.toUpperCase(Locale.ROOT), RESOURCE, campaignId, null, null, "/comm/campaigns/" + campaignId, body));
    }

    @GET
    @Path("{campaignId}/runs")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Runs of a campaign, newest first")
    public List<CampaignRunData> retrieveRuns(@PathParam("campaignId") final Long campaignId) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return readService.retrieveRuns(campaignId);
    }

    @GET
    @Path("{campaignId}/runs/{runId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "One run with its counts by status and reason")
    public CampaignRunData retrieveRun(@PathParam("campaignId") final Long campaignId, @PathParam("runId") final Long runId) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return readService.retrieveRun(campaignId, runId);
    }

    @GET
    @Path("{campaignId}/runs/{runId}/deliveries")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delivery report", description = "Every recipient of a run. Filters: status (QUEUED, SENT, FAILED, SKIPPED), reason.")
    public Page<DeliveryData> retrieveDeliveries(@PathParam("campaignId") final Long campaignId, @PathParam("runId") final Long runId,
            @QueryParam("status") final String status, @QueryParam("reason") final String reason,
            @QueryParam("offset") final Integer offset, @QueryParam("limit") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return readService.retrieveDeliveries(campaignId, runId, status, reason, offset, limit);
    }

    @POST
    @Path("{campaignId}/runs/{runId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retry a run's failed messages", description = "command=retry. Body: optional deliveryIds; without them every "
            + "message that can be retried is queued again.")
    public CommandProcessingResult retry(@PathParam("campaignId") final Long campaignId, @PathParam("runId") final Long runId,
            @QueryParam("command") final String command, final String body) {
        if (!"retry".equalsIgnoreCase(StringUtils.defaultString(command))) {
            throw new UnrecognizedQueryParamException("command", command, new Object[] { "retry" });
        }
        return commands.logCommandSource(
                CommCommands.of("RETRY", RESOURCE, campaignId, runId, null, "/comm/campaigns/" + campaignId + "/runs/" + runId, body));
    }
}
