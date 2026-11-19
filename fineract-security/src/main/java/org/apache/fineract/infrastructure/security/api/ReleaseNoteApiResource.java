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
package org.apache.fineract.infrastructure.security.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.ReleaseNoteService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Component;

/**
 * Lets the logged-in staff user see and record whether they have read the current release notes. Acts on the caller
 * only, so it needs no permission beyond being logged in.
 */
@Component
@Path("/v1/releasenote")
@Tag(name = "Release Notes", description = "Whether the logged-in user has read the current release notes.")
@RequiredArgsConstructor
public class ReleaseNoteApiResource {

    private final PlatformSecurityContext context;
    private final ReleaseNoteService releaseNoteService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Release-note status of the logged-in user", description = "The current release-note version, whether the user has read it and when they last marked release notes as read.")
    public String status() {
        final AppUser user = this.context.authenticatedUser();
        return toJson(this.releaseNoteService.currentVersion(), this.releaseNoteService.hasViewed(user.getId()),
                this.releaseNoteService.viewedOn(user.getId()));
    }

    @PUT
    @Path("viewed")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Mark the release notes as read", description = "Records that the logged-in user has read the given version of the release notes. Example body: {\"version\": \"1.15.2\"}")
    public String markViewed(final String apiRequestBodyAsJson) {
        final AppUser user = this.context.authenticatedUser();
        final JsonObject body = StringUtils.isBlank(apiRequestBodyAsJson) ? new JsonObject()
                : new Gson().fromJson(apiRequestBodyAsJson, JsonObject.class);
        final String version = body != null && body.has("version") && !body.get("version").isJsonNull()
                ? StringUtils.trimToNull(body.get("version").getAsString())
                : null;
        if (version == null) {
            throw new PlatformApiDataValidationException("validation.msg.releasenote.version.cannot.be.blank",
                    "Say which version of the release notes was read.", "version");
        }
        final String current = this.releaseNoteService.currentVersion();
        if (current != null && !current.equals(version)) {
            throw new PlatformApiDataValidationException("validation.msg.releasenote.version.out.of.date",
                    "These release notes are out of date. Reload the page.", "version", version);
        }
        final LocalDateTime viewedOn = this.releaseNoteService.markViewed(user.getId(), version);
        return toJson(current, true, viewedOn);
    }

    private static String toJson(final String version, final boolean viewed, final LocalDateTime viewedOn) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", version);
        result.put("viewedReleaseNote", viewed);
        result.put("viewedOn", viewedOn == null ? null : viewedOn.toString());
        return new Gson().toJson(result);
    }
}
