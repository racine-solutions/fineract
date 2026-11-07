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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.campaigns.data.MarketingConsentData;
import org.apache.fineract.racine.campaigns.service.MarketingConsentService;
import org.springframework.stereotype.Component;

@Path("/v1/comm/clients/{clientId}/marketingconsent")
@Component
@Tag(name = "Client Marketing Consent", description = "Whether a client gets Sales and marketing messages")
@RequiredArgsConstructor
public class ClientMarketingConsentApiResource {

    private static final String RESOURCE = "CLIENTMARKETINGCONSENT";

    private final PlatformSecurityContext context;
    private final MarketingConsentService consentService;
    private final PortfolioCommandSourceWritePlatformService commands;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Whether the client gets marketing messages")
    public MarketingConsentData retrieve(@PathParam("clientId") final Long clientId) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE);
        return consentService.retrieve(clientId);
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Record the client's choice", description = "Body: receivesMarketing (true/false) and an optional note, e.g. "
            + "\"Asked at the counter\".")
    public CommandProcessingResult update(@PathParam("clientId") final Long clientId, final String body) {
        return commands.logCommandSource(
                CommCommands.of("UPDATE", RESOURCE, clientId, null, clientId, "/comm/clients/" + clientId + "/marketingconsent", body));
    }
}
