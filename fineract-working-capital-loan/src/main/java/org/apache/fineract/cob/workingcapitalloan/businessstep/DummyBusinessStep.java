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
<<<<<<<< HEAD:fineract-command/src/test/java/org/apache/fineract/command/sample/handler/DummyCommandHandler.java
package org.apache.fineract.command.sample.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.command.core.Command;
import org.apache.fineract.command.core.CommandHandler;
import org.apache.fineract.command.sample.data.DummyRequest;
import org.apache.fineract.command.sample.data.DummyResponse;
import org.apache.fineract.command.sample.service.DummyService;
========
package org.apache.fineract.cob.workingcapitalloan.businessstep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.workingcapitalloan.domain.WorkingCapitalLoan;
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-working-capital-loan/src/main/java/org/apache/fineract/cob/workingcapitalloan/businessstep/DummyBusinessStep.java
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
<<<<<<<< HEAD:fineract-command/src/test/java/org/apache/fineract/command/sample/handler/DummyCommandHandler.java
public class DummyCommandHandler implements CommandHandler<DummyRequest, DummyResponse> {

    private final DummyService dummyService;

    @Override
    public DummyResponse handle(Command<DummyRequest> command) {
        return dummyService.process(command.getPayload());
========
public class DummyBusinessStep extends WorkingCapitalLoanCOBBusinessStep {

    @Override
    public WorkingCapitalLoan execute(WorkingCapitalLoan input) {
        log.info("Executing DummyBusinessStep... WorkingCapitalLoan ID = {}", input.getId());
        return input;
    }

    @Override
    public String getEnumStyledName() {
        return "DUMMY_BUSINESS_STEP";
    }

    @Override
    public String getHumanReadableName() {
        return "Dummy Business Step";
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-working-capital-loan/src/main/java/org/apache/fineract/cob/workingcapitalloan/businessstep/DummyBusinessStep.java
    }
}
