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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.racine.campaigns.service.EmailEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Receives Brevo's transactional email webhook. Brevo can't log in, so the endpoint is open to the network and checks a
 * shared token instead. Set {@code BREVO_WEBHOOK_TOKEN} on the server and register in Brevo the URL
 * {@code https://<host>/fineract-provider/api/v1/comm/webhooks/brevo?tenantIdentifier=<tenant>&token=<token>} for the
 * events delivered, soft bounce, hard bounce, invalid email, blocked, error and spam (one URL per tenant). While no
 * token is set, every call is refused.
 */
@Slf4j
@Path("/v1/comm/webhooks/brevo")
@Component
@Tag(name = "Email Webhook", description = "Delivery events for campaign emails from the email service")
public class BrevoWebhookApiResource {

    private final EmailEventService eventService;
    private final byte[] token;

    public BrevoWebhookApiResource(final EmailEventService eventService,
            @Value("${fineract.email.brevo.webhook-token:}") final String token) {
        this.eventService = eventService;
        this.token = StringUtils.trimToEmpty(token).getBytes(StandardCharsets.UTF_8);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Brevo email events", description = "Called by Brevo, not by staff. Query: tenantIdentifier and token. Body: one "
            + "event or an array of them.")
    public Response receive(@QueryParam("token") final String given, final String body) {
        if (token.length == 0 || given == null || !MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Brevo webhook call refused: {}", token.length == 0 ? "BREVO_WEBHOOK_TOKEN is not set" : "wrong token");
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "forbidden")).build();
        }
        final JsonElement payload;
        try {
            payload = StringUtils.isBlank(body) ? null : JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "body is not JSON")).build();
        }
        // Always 200 for a valid call, so Brevo doesn't keep re-sending events for emails we don't track
        return Response.ok(Map.of("updated", eventService.apply(payload))).build();
    }
}
