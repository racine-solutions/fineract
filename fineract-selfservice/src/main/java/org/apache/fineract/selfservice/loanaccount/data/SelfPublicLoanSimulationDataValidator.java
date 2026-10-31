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
package org.apache.fineract.selfservice.loanaccount.data;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

/**
 * Validates public loan simulation requests. This validator is a security gate-keeper only — it does NOT re-validate
 * loan-specific fields ({@code productId}, {@code principal}, etc.) because the core
 * {@code LoanScheduleCalculationPlatformService} handles that and returns proper 400 errors.
 *
 * <p>
 * This validator enforces exactly two constraints:
 *
 * <ol>
 * <li>{@code command} query parameter must be exactly {@code "calculateLoanSchedule"} — without this, the core method's
 * else-branch creates a real loan via {@code
 *       CommandWrapperBuilder.createLoanApplication()}
 * <li>Request body must NOT contain {@code clientId} — anonymous users cannot associate simulations with real clients
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class SelfPublicLoanSimulationDataValidator {

    private static final String CALCULATE_LOAN_SCHEDULE = "calculateLoanSchedule";

    private final FromJsonHelper fromJsonHelper;

    /**
     * Validates a public simulation request.
     *
     * @param commandParam
     *            the {@code command} query parameter value
     * @param json
     *            the request body JSON
     * @throws UnrecognizedQueryParamException
     *             if {@code command} is not {@code
     *     "calculateLoanSchedule"}
     * @throws InvalidJsonException
     *             if the request body is blank
     * @throws PlatformApiDataValidationException
     *             if the request body contains {@code clientId}
     */
    public void validatePublicSimulationRequest(final String commandParam, final String json) {
        validateCommand(commandParam);
        validateRequestBody(json);
    }

    private void validateCommand(final String commandParam) {
        if (!CALCULATE_LOAN_SCHEDULE.equals(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, CALCULATE_LOAN_SCHEDULE);
        }
    }

    /**
     * Validates the request body JSON structure. Rejects blank/null JSON and bodies containing {@code
     * clientId}. Malformed JSON ({@code JsonSyntaxException}) is translated to {@link InvalidJsonException} for
     * consistent 400 error responses.
     */
    private void validateRequestBody(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final com.google.gson.JsonElement element;
        try {
            element = this.fromJsonHelper.parse(json);
        } catch (com.google.gson.JsonSyntaxException e) {
            throw new InvalidJsonException();
        }

        if (this.fromJsonHelper.parameterExists("clientId", element)) {
            final List<ApiParameterError> errors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan.simulation");
            baseDataValidator.reset().parameter("clientId").failWithCode("not.allowed.for.public.simulation",
                    "clientId is not allowed in public loan simulation requests");
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
