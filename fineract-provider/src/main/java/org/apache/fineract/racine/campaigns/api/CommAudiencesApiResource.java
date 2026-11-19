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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.campaigns.data.AudienceClientData;
import org.apache.fineract.racine.campaigns.data.AudienceCountData;
import org.apache.fineract.racine.campaigns.data.SavedAudienceData;
import org.apache.fineract.racine.campaigns.domain.CommChannel;
import org.apache.fineract.racine.campaigns.domain.CommPurpose;
import org.apache.fineract.racine.campaigns.service.AudienceQueryBuilder;
import org.apache.fineract.racine.campaigns.service.AudienceService;
import org.apache.fineract.racine.campaigns.service.SavedAudienceService;
import org.apache.fineract.racine.campaigns.service.StaffAudienceQueryBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Path("/v1/comm/audiences")
@Component
@Tag(name = "Communication Audiences", description = "Who a campaign reaches: filter options, live count with skip reasons, and a sample")
@RequiredArgsConstructor
public class CommAudiencesApiResource {

    static final String SAVED_RESOURCE = "COMMAUDIENCE";

    private final PlatformSecurityContext context;
    private final AudienceService audienceService;
    private final JdbcTemplate jdbcTemplate;
    private final SavedAudienceService savedAudienceService;
    private final PortfolioCommandSourceWritePlatformService commands;

    @GET
    @Path("template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Filter options", description = "Fields, and the values for offices, genders, client types, classifications, "
            + "loan products and purposes, savings products, loan officers, groups and delinquency ranges.")
    public Map<String, Object> template() {
        context.authenticatedUser().validateHasReadPermission(CommCampaignsApiResource.RESOURCE);
        final Map<String, Object> options = new LinkedHashMap<>();
        options.put("fields", AudienceQueryBuilder.FIELDS);
        options.put("staffFields", StaffAudienceQueryBuilder.FIELDS);
        options.put("roles", jdbcTemplate.queryForList("select id, name from m_role where is_disabled = ? order by name", false));
        // Staff and system users without a staff record, for "Specific staff"; ids are "STAFF:3" and "USER:5"
        final List<Map<String, Object>> people = new java.util.ArrayList<>();
        jdbcTemplate.query("select s.id, s.firstname, s.lastname, s.display_name from m_staff s join m_office so on so.id = s.office_id"
                + " where s.is_active = ? and so.hierarchy like ? order by s.firstname, s.lastname", rs -> {
                    people.add(Map.of("id", "STAFF:" + rs.getLong("id"), "name", org.apache.fineract.notification.service.SmsText
                            .personName(rs.getString("firstname"), rs.getString("lastname"), rs.getString("display_name"))));
                }, true, context.officeHierarchy() + "%");
        jdbcTemplate.query(
                "select u.id, u.firstname, u.lastname, u.username from m_appuser u join m_office uo on uo.id = u.office_id"
                        + " where u.is_deleted = ? and u.enabled = ? and u.staff_id is null and uo.hierarchy like ? order by u.firstname",
                rs -> {
                    final String name = StringUtils.normalizeSpace(StringUtils.defaultString(rs.getString("firstname")) + " "
                            + StringUtils.defaultString(rs.getString("lastname")));
                    people.add(Map.of("id", "USER:" + rs.getLong("id"), "name",
                            StringUtils.defaultIfBlank(name, rs.getString("username")) + " (system user)"));
                }, false, true, context.officeHierarchy() + "%");
        options.put("staffPeople", people);
        options.put("offices", jdbcTemplate.queryForList("select id, name from m_office where hierarchy like ? order by hierarchy",
                context.officeHierarchy() + "%"));
        // Campaigns target Female and Male; Trans-Gender stays a Gender value elsewhere but is not offered here
        options.put("genders", codeValues("Gender").stream().filter(value -> !isTransGender(value.get("name"))).toList());
        options.put("clientTypes", codeValues("ClientType"));
        options.put("classifications", codeValues("ClientClassification"));
        options.put("loanProducts", jdbcTemplate.queryForList("select id, name from m_product_loan order by name"));
        options.put("loanPurposes", codeValues("LoanPurpose"));
        options.put("savingsProducts", jdbcTemplate.queryForList("select id, name from m_savings_product order by name"));
        options.put("loanOfficers", jdbcTemplate.queryForList(
                "select s.id, concat(s.firstname, ' ', s.lastname) name from m_staff s join m_office so on so.id = s.office_id"
                        + " where s.is_active = ? and s.is_loan_officer = ? and so.hierarchy like ? order by s.firstname, s.lastname",
                true, true, context.officeHierarchy() + "%"));
        options.put("groups",
                jdbcTemplate.queryForList(
                        "select g.id, g.display_name name from m_group g join m_office gof"
                                + " on gof.id = g.office_id where g.status_enum = ? and gof.hierarchy like ? order by g.display_name",
                        300, context.officeHierarchy() + "%"));
        options.put("delinquencyRanges", jdbcTemplate
                .queryForList("select id, classification name, min_age_days, max_age_days from m_delinquency_range order by min_age_days"));
        return options;
    }

    @GET
    @Path("saved")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Saved audiences", description = "Named sets of filters, by name, for the staff member's offices.")
    public Page<SavedAudienceData> retrieveSaved(@QueryParam("offset") final Integer offset, @QueryParam("limit") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(SAVED_RESOURCE);
        return savedAudienceService.retrieveAll(offset, limit);
    }

    @POST
    @Path("saved")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Save an audience", description = "Body: name and audience (filters).")
    public CommandProcessingResult createSaved(final String body) {
        return commands.logCommandSource(CommCommands.of("CREATE", SAVED_RESOURCE, null, null, null, "/comm/audiences/saved", body));
    }

    @PUT
    @Path("saved/{audienceId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Rename or change a saved audience")
    public CommandProcessingResult updateSaved(@PathParam("audienceId") final Long audienceId, final String body) {
        return commands.logCommandSource(
                CommCommands.of("UPDATE", SAVED_RESOURCE, audienceId, null, null, "/comm/audiences/saved/" + audienceId, body));
    }

    @DELETE
    @Path("saved/{audienceId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a saved audience", description = "Campaigns that used it keep their own copy of the filters.")
    public CommandProcessingResult deleteSaved(@PathParam("audienceId") final Long audienceId) {
        return commands.logCommandSource(
                CommCommands.of("DELETE", SAVED_RESOURCE, audienceId, null, null, "/comm/audiences/saved/" + audienceId, "{}"));
    }

    @POST
    @Path("count")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Count matching clients", description = "Body: audience (filters), purpose and channel (SMS or EMAIL). Returns matching clients, how many "
            + "get the message, and the skipped ones by reason.")
    public AudienceCountData count(final String body) {
        context.authenticatedUser().validateHasReadPermission(CommCampaignsApiResource.RESOURCE);
        final JsonObject json = parse(body);
        return audienceService.count(AudienceService.parse(audience(json)), context.officeHierarchy(), purpose(json), channel(json));
    }

    @POST
    @Path("clients")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Matching clients, one page at a time", description = "Body: audience, purpose, channel, show (ALL, RECIPIENTS"
            + " or SKIPPED), search (name, account number, phone or email), offset, limit (up to 500). Skipped clients carry the reason.")
    public Page<AudienceClientData> clients(final String body) {
        context.authenticatedUser().validateHasReadPermission(CommCampaignsApiResource.RESOURCE);
        final JsonObject json = parse(body);
        return audienceService.clients(AudienceService.parse(audience(json)), context.officeHierarchy(), purpose(json), channel(json),
                text(json, "show"), text(json, "search"), number(json, "offset", 0), number(json, "limit", 25));
    }

    private List<Map<String, Object>> codeValues(final String code) {
        return jdbcTemplate.queryForList("select cv.id, cv.code_value name from m_code_value cv join m_code c on c.id = cv.code_id"
                + " where c.code_name = ? and cv.is_active = ? order by cv.order_position, cv.code_value", code, true);
    }

    static boolean isTransGender(final Object name) {
        return String.valueOf(name).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").equals("transgender");
    }

    private static String text(final JsonObject json, final String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }

    private static int number(final JsonObject json, final String name, final int fallback) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsInt() : fallback;
    }

    private static JsonObject parse(final String body) {
        return StringUtils.isBlank(body) ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
    }

    private static String audience(final JsonObject json) {
        return json.has("audience") ? json.get("audience").toString() : "[]";
    }

    private static CommChannel channel(final JsonObject json) {
        return json.has("channel") && !json.get("channel").isJsonNull() && "EMAIL".equalsIgnoreCase(json.get("channel").getAsString())
                ? CommChannel.EMAIL
                : CommChannel.SMS;
    }

    private static CommPurpose purpose(final JsonObject json) {
        return json.has("purpose") && "MARKETING".equalsIgnoreCase(json.get("purpose").getAsString()) ? CommPurpose.MARKETING
                : CommPurpose.GENERAL;
    }
}
