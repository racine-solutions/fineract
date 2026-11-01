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
package org.apache.fineract.selfservice.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.selfservice.report.data.SelfServiceAdoptionReport;
import org.apache.fineract.selfservice.report.data.SelfServiceFunnelReport;
import org.apache.fineract.selfservice.report.data.SelfServiceTransactionsReport;
import org.apache.fineract.selfservice.report.service.SelfServiceReportReadService;
import org.springframework.stereotype.Component;

/**
 * Back-office reports on the mobile app (staff login, READ_SELFSERVICEREPORT). Results cover the staff member's office
 * and its child offices; officeId narrows them to one office and its child offices. Dates are yyyy-MM-dd.
 */
@Path("/v1/selfservicereports")
@Component
@Tag(name = "Self Service Reports (back office)", description = "Mobile app adoption, sign-up funnel and app transactions")
@RequiredArgsConstructor
public class SelfServiceReportsApiResource {

    /** Permission entity: READ_SELFSERVICEREPORT. */
    public static final String RESOURCE_NAME = "SELFSERVICEREPORT";

    private final PlatformSecurityContext context;
    private final SelfServiceReportReadService reportService;

    @GET
    @Path("adoption")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Mobile app adoption", description = "Per office: active clients, clients using the app, app users, "
            + "active app users and app users who logged in in the last 30 days. Per month (last `months`, default 12, max 36): "
            + "sign-ups and first logins.")
    public SelfServiceAdoptionReport adoption(@QueryParam("officeId") @Parameter(description = "officeId") final Long officeId,
            @QueryParam("months") @Parameter(description = "months") final Integer months) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        return reportService.adoption(officeId, months);
    }

    @GET
    @Path("funnel")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Sign-up funnel", description = "Of the people who signed up between fromDate and toDate (default: the "
            + "last 30 days, at most 366 days): how many confirmed their registration code, logged in and made a transfer.")
    public SelfServiceFunnelReport funnel(@QueryParam("officeId") @Parameter(description = "officeId") final Long officeId,
            @QueryParam("fromDate") @Parameter(description = "fromDate") final String fromDate,
            @QueryParam("toDate") @Parameter(description = "toDate") final String toDate) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        return reportService.funnel(officeId, parseDate("fromDate", fromDate), parseDate("toDate", toDate));
    }

    @GET
    @Path("transactions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "App transactions", description = "Transfers made in the mobile app between fromDate and toDate (default: "
            + "the last 30 days, at most 366 days) by channel and currency and per day, and the transfers the app refused, by reason.")
    public SelfServiceTransactionsReport transactions(@QueryParam("officeId") @Parameter(description = "officeId") final Long officeId,
            @QueryParam("fromDate") @Parameter(description = "fromDate") final String fromDate,
            @QueryParam("toDate") @Parameter(description = "toDate") final String toDate) {
        context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        return reportService.transactions(officeId, parseDate("fromDate", fromDate), parseDate("toDate", toDate));
    }

    private static LocalDate parseDate(final String name, final String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    List.of(ApiParameterError.parameterErrorWithValue("validation.msg.selfservicereport." + name + ".invalid",
                            "The parameter `" + name + "` must be a date in the format yyyy-MM-dd.", name, value)));
        }
    }
}
