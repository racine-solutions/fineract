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
package org.apache.fineract.portfolio.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.service.ClientScreeningReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/clients/screening")
@Component
@Tag(name = "Client Screening", description = "Screen clients against sanctions lists using Moov Watchman API")
@RequiredArgsConstructor
public class ClientScreeningApiResource {

    private final PlatformSecurityContext context;
    private final ClientScreeningReadPlatformService clientScreeningReadPlatformService;

    @POST
    @Path("{clientId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Screen a client by ID", description = "Screen a registered client using database details against Moov Watchman and save results")
    @ApiResponse(responseCode = "200", description = "OK")
    public Response screenClient(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {
        
        this.context.authenticatedUser().validateHasReadPermission("CLIENT");

        String result = this.clientScreeningReadPlatformService.screenClient(clientId);
        
        if (result.contains("\"error\"")) {
            return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
        }
        
        return Response.ok(result, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("{clientId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get client screening results", description = "Retrieve all saved screening results for a client")
    @ApiResponse(responseCode = "200", description = "OK")
    public Response getScreeningResults(@PathParam("clientId") @Parameter(description = "clientId") final Long clientId) {
        
        this.context.authenticatedUser().validateHasReadPermission("CLIENT");

        String result = this.clientScreeningReadPlatformService.getScreeningResults(clientId);
        
        return Response.ok(result, MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("review/{screeningId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Review client screening match status by search ID", description = "Review and mark a specific screening run by its ID as MATCH or NOT_MATCH")
    @ApiResponse(responseCode = "200", description = "OK")
    public Response reviewScreening(
            @PathParam("screeningId") @Parameter(description = "screeningId") final Long screeningId,
            final String apiRequestBodyAsJson) {
        
        this.context.authenticatedUser().validateHasUpdatePermission("CLIENT");

        String status = null;
        try {
            JsonObject body = new Gson().fromJson(apiRequestBodyAsJson, JsonObject.class);
            if (body != null && body.has("status")) {
                status = body.get("status").getAsString();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Invalid request payload. Expected JSON body containing a 'status' field\"}")
                    .build();
        }

        String result = this.clientScreeningReadPlatformService.reviewScreening(screeningId, status);
        if (result.contains("\"error\"")) {
            return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
        }
        
        return Response.ok(result, MediaType.APPLICATION_JSON).build();
    }
}
