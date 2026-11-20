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
package org.apache.fineract.racine.officerapp.api;

import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.DEVICE_COMMANDS;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.DEVICE_ENTITY;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.READ_RESOURCE;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.USER_COMMANDS;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.USER_ENTITY;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
import org.apache.fineract.racine.officerapp.data.OfficerAppDeviceData;
import org.apache.fineract.racine.officerapp.data.OfficerAppEventData;
import org.apache.fineract.racine.officerapp.data.OfficerAppSummaryData;
import org.apache.fineract.racine.officerapp.data.OfficerAppUserData;
import org.apache.fineract.racine.officerapp.service.OfficerAppReadService;
import org.apache.fineract.racine.officerapp.service.OfficerAppSearch;
import org.springframework.stereotype.Component;

/**
 * Admin › Mobile Officer App: who uses the field officer app, on which devices, what they did, and turning app access
 * off and on. Staff see users in their own office and the offices under it.
 */
@Path("/v1/officerapp")
@Component
@Tag(name = "Mobile Officer App (admin)", description = "Track and control staff use of the field officer app")
@RequiredArgsConstructor
public class OfficerAppApiResource {

    private final PlatformSecurityContext context;
    private final OfficerAppReadService readService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Path("summary")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Dashboard cards", description = "Devices connected, online now, active today, officers on the app in the "
            + "last 30 days, access off, blocked devices, app versions in use.")
    public OfficerAppSummaryData summary(@QueryParam("officeId") @Parameter(description = "officeId") final Long officeId) {
        context.authenticatedUser().validateHasReadPermission(READ_RESOURCE);
        return readService.retrieveSummary(officeId);
    }

    @GET
    @Path("officers")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Staff users of the app", description = "Users who have used the app or have an access decision (all=true: "
            + "every staff user). Filters: officeId, search (name, username, email, device code or model), access (ALLOWED | OFF), "
            + "online (ONLINE | IDLE | SIGNED_OUT), appVersion. orderBy = lastSeen | displayName | username | officeName | devices | "
            + "access; sortOrder = ASC | DESC.")
    public Page<OfficerAppUserData> officers(@QueryParam("officeId") final Long officeId, @QueryParam("search") final String search,
            @QueryParam("access") final String access, @QueryParam("online") final String online,
            @QueryParam("appVersion") final String appVersion, @QueryParam("all") final Boolean all,
            @QueryParam("orderBy") final String orderBy, @QueryParam("sortOrder") final String sortOrder,
            @QueryParam("offset") final Integer offset, @QueryParam("limit") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(READ_RESOURCE);
        return readService.retrieveOfficers(OfficerAppSearch.builder().officeId(officeId).search(search).access(access).online(online)
                .appVersion(appVersion).all(all).orderBy(orderBy).sortOrder(sortOrder).offset(offset).limit(limit).build());
    }

    @GET
    @Path("officers/{userId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "One staff user", description = "App access, devices, roles and 30-day figures. Works for any staff user in "
            + "scope, also one who has never used the app.")
    public OfficerAppUserData officer(@PathParam("userId") final Long userId) {
        context.authenticatedUser().validateHasReadPermission(READ_RESOURCE);
        return readService.retrieveOfficer(userId);
    }

    @GET
    @Path("officers/{userId}/events")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activity timeline of one staff user", description = "Newest first. category: LOGIN, ACTION, SYNC, REPORT, "
            + "ACCESS, SESSION (comma-separated); result: SUCCESS, PENDING, FAILED, REFUSED; deviceId.")
    public Page<OfficerAppEventData> officerEvents(@PathParam("userId") final Long userId, @QueryParam("deviceId") final Long deviceId,
            @QueryParam("category") final String category, @QueryParam("result") final String result,
            @QueryParam("offset") final Integer offset, @QueryParam("limit") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(READ_RESOURCE);
        return readService.retrieveEvents(userId, deviceId, category, result, offset, limit);
    }

    @GET
    @Path("devices")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Devices", description = "Every phone that has signed in. Filters: officeId, userId, search (code, model, "
            + "officer), status (ACTIVE | BLOCKED), signedIn, appVersion. Newest seen first (sortOrder = ASC for oldest).")
    public Page<OfficerAppDeviceData> devices(@QueryParam("officeId") final Long officeId, @QueryParam("userId") final Long userId,
            @QueryParam("search") final String search, @QueryParam("status") final String status,
            @QueryParam("signedIn") final Boolean signedIn, @QueryParam("appVersion") final String appVersion,
            @QueryParam("sortOrder") final String sortOrder, @QueryParam("offset") final Integer offset,
            @QueryParam("limit") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(READ_RESOURCE);
        return readService.retrieveDevices(OfficerAppSearch.builder().officeId(officeId).userId(userId).search(search).status(status)
                .signedIn(signedIn).appVersion(appVersion).sortOrder(sortOrder).offset(offset).limit(limit).build());
    }

    @GET
    @Path("devices/{deviceId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "One device")
    public OfficerAppDeviceData device(@PathParam("deviceId") final Long deviceId) {
        context.authenticatedUser().validateHasReadPermission(READ_RESOURCE);
        return readService.retrieveDevice(deviceId);
    }

    @GET
    @Path("events")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activity of all officers in scope", description = "Newest first. Same filters as the officer timeline.")
    public Page<OfficerAppEventData> events(@QueryParam("deviceId") final Long deviceId, @QueryParam("category") final String category,
            @QueryParam("result") final String result, @QueryParam("offset") final Integer offset,
            @QueryParam("limit") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(READ_RESOURCE);
        return readService.retrieveEvents(null, deviceId, category, result, offset, limit);
    }

    @POST
    @Path("officers/{userId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Change a staff user's app access", description = "command = turnOff (body: reason required, blockDevices "
            + "default false, notify default true) | turnOn (reason required, notify) | signOutAll (reason optional). The web-app "
            + "login is never changed.")
    public CommandProcessingResult changeAccess(@PathParam("userId") final Long userId, @QueryParam("command") final String command,
            @Parameter(hidden = true) final String body) {
        return submit(USER_COMMANDS.get(StringUtils.defaultString(command)), command, USER_ENTITY, userId, "/officerapp/officers/" + userId,
                body);
    }

    @POST
    @Path("devices/{deviceId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Block, unblock or sign out a device", description = "command = block (reason required, notify) | unblock | "
            + "signOut (reason optional).")
    public CommandProcessingResult changeDevice(@PathParam("deviceId") final Long deviceId, @QueryParam("command") final String command,
            @Parameter(hidden = true) final String body) {
        return submit(DEVICE_COMMANDS.get(StringUtils.defaultString(command)), command, DEVICE_ENTITY, deviceId,
                "/officerapp/devices/" + deviceId, body);
    }

    private CommandProcessingResult submit(final String action, final String commandParam, final String entity, final Long id,
            final String href, final String body) {
        if (action == null) {
            throw new UnrecognizedQueryParamException("command", commandParam);
        }
        final CommandWrapper wrapper = new CommandWrapper(null, null, null, null, null, action, entity, id, null, href,
                StringUtils.isBlank(body) ? "{}" : body, null, null, null, null, null, null, null, null, null);
        return commandsSourceWritePlatformService.logCommandSource(wrapper);
    }
}
