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
package org.apache.fineract.selfservice.admin.api;

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
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.selfservice.admin.data.SelfServiceUserAdminData;
import org.apache.fineract.selfservice.admin.data.SelfServiceUserSummaryData;
import org.apache.fineract.selfservice.admin.service.SelfServiceUserAdminReadService;
import org.apache.fineract.selfservice.admin.service.SelfServiceUserSearchParameters;
import org.apache.fineract.selfservice.commands.service.CommandWrapperBuilderSelfService;
import org.springframework.stereotype.Component;

/**
 * Back-office API for managing self-service (mobile app) users. Called by staff with their normal login; it is not part
 * of the mobile app API under /v1/self. Results are limited to the staff member's office and its child offices.
 */
@Path("/v1/selfserviceusers")
@Component
@Tag(name = "Self Service Users (back office)", description = "Staff management of mobile app users: list, profile, and access changes")
@RequiredArgsConstructor
public class SelfServiceUsersApiResource {

    private final PlatformSecurityContext context;
    private final SelfServiceUserAdminReadService readService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List self-service users", description = "Filters: officeId, status (100 Pending, 300 Active, 400 View only, "
            + "500 Suspended, 600 Closed), locked, clientId, search (name, username, email, mobile, client account number). "
            + "Sorting: orderBy = displayName | username | officeName | status | createdOn | lastLoginOn, sortOrder = ASC | DESC.")
    public Page<SelfServiceUserAdminData> retrieveAll(@QueryParam("officeId") @Parameter(description = "officeId") final Long officeId,
            @QueryParam("status") @Parameter(description = "status") final Integer status,
            @QueryParam("locked") @Parameter(description = "locked") final Boolean locked,
            @QueryParam("clientId") @Parameter(description = "clientId") final Long clientId,
            @QueryParam("search") @Parameter(description = "search") final String search,
            @QueryParam("offset") @Parameter(description = "offset") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit") final Integer limit,
            @QueryParam("orderBy") @Parameter(description = "orderBy") final String orderBy,
            @QueryParam("sortOrder") @Parameter(description = "sortOrder") final String sortOrder) {
        context.authenticatedUser().validateHasReadPermission(SelfServiceUsersApiConstants.RESOURCE_NAME);
        return readService.retrieveAll(SelfServiceUserSearchParameters.builder().officeId(officeId).status(status).locked(locked)
                .clientId(clientId).search(search).offset(offset).limit(limit).orderBy(orderBy).sortOrder(sortOrder).build());
    }

    @GET
    @Path("summary")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Count self-service users", description = "Counts by status, locked users and users who logged in during the "
            + "last 30 days, for the staff member's offices (optionally one office).")
    public SelfServiceUserSummaryData retrieveSummary(@QueryParam("officeId") @Parameter(description = "officeId") final Long officeId) {
        context.authenticatedUser().validateHasReadPermission(SelfServiceUsersApiConstants.RESOURCE_NAME);
        return readService.retrieveSummary(officeId);
    }

    @GET
    @Path("{userId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a self-service user")
    public SelfServiceUserAdminData retrieveOne(@PathParam("userId") @Parameter(description = "userId") final Long userId) {
        context.authenticatedUser().validateHasReadPermission(SelfServiceUsersApiConstants.RESOURCE_NAME);
        return readService.retrieveOne(userId);
    }

    @POST
    @Path("{userId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Change a self-service user's app access", description = "command = viewOnly | suspend | reactivate | close "
            + "(body: reason, required; viewOnly also takes revokeSessions, default true), unlock | revokeSessions (body: reason, "
            + "optional), resetPassword (body: method = sendLink | forceChange, reason optional). The client is notified by email "
            + "and SMS.")
    public CommandProcessingResult changeAccess(@PathParam("userId") @Parameter(description = "userId") final Long userId,
            @QueryParam("command") @Parameter(description = "command") final String commandParam, final String apiRequestBodyAsJson) {
        final String action = SelfServiceUsersApiConstants.COMMANDS.get(StringUtils.defaultString(commandParam));
        if (action == null) {
            throw new UnrecognizedQueryParamException("command", commandParam);
        }
        final String json = StringUtils.isBlank(apiRequestBodyAsJson) ? "{}" : apiRequestBodyAsJson;
        final CommandWrapper commandRequest = new CommandWrapperBuilderSelfService().selfServiceUserCommand(action, userId).withJson(json)
                .build();
        return commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("{userId}/limits")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Set a self-service user's transfer limits", description = "Replaces all limits. Body: {\"limits\": "
            + "[{\"currencyCode\": \"UGX\", \"perTransferLimit\": 1000000, \"dailyLimit\": 2000000}], \"reason\": \"...\"}. "
            + "An empty list removes all limits. Applies to every transfer made in the mobile app.")
    public CommandProcessingResult updateLimits(@PathParam("userId") @Parameter(description = "userId") final Long userId,
            final String apiRequestBodyAsJson) {
        return submit(SelfServiceUsersApiConstants.ACTION_UPDATELIMITS, userId, apiRequestBodyAsJson);
    }

    @PUT
    @Path("{userId}/clients")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Set the clients a self-service user acts for", description = "Replaces all links. Body: {\"clientIds\": [12], "
            + "\"reason\": \"...\"}. Clients must be in one office and not linked to another app user.")
    public CommandProcessingResult updateClients(@PathParam("userId") @Parameter(description = "userId") final Long userId,
            final String apiRequestBodyAsJson) {
        return submit(SelfServiceUsersApiConstants.ACTION_UPDATECLIENTS, userId, apiRequestBodyAsJson);
    }

    private CommandProcessingResult submit(final String action, final Long userId, final String apiRequestBodyAsJson) {
        final String json = StringUtils.isBlank(apiRequestBodyAsJson) ? "{}" : apiRequestBodyAsJson;
        final CommandWrapper commandRequest = new CommandWrapperBuilderSelfService().selfServiceUserCommand(action, userId).withJson(json)
                .build();
        return commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
