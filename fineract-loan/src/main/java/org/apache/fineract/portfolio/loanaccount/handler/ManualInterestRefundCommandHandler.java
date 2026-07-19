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
<<<<<<<< HEAD:fineract-provider/src/main/java/org/apache/fineract/useradministration/handler/ChangeUserPasswordCommandHandler.java
package org.apache.fineract.useradministration.handler;
========
package org.apache.fineract.portfolio.loanaccount.handler;
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/handler/ManualInterestRefundCommandHandler.java

import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
<<<<<<<< HEAD:fineract-provider/src/main/java/org/apache/fineract/useradministration/handler/ChangeUserPasswordCommandHandler.java
import org.apache.fineract.useradministration.service.AppUserWritePlatformService;
import org.springframework.beans.factory.annotation.Autowired;
========
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/handler/ManualInterestRefundCommandHandler.java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
<<<<<<<< HEAD:fineract-provider/src/main/java/org/apache/fineract/useradministration/handler/ChangeUserPasswordCommandHandler.java
@CommandType(entity = "USER", action = "CHANGEPWD")
public class ChangeUserPasswordCommandHandler implements NewCommandSourceHandler {

    private final AppUserWritePlatformService writePlatformService;

    @Autowired
    public ChangeUserPasswordCommandHandler(final AppUserWritePlatformService writePlatformService) {
        this.writePlatformService = writePlatformService;
    }
========
@RequiredArgsConstructor
@CommandType(entity = "LOAN", action = "MANUAL_INTEREST_REFUND_TRANSACTION")
public class ManualInterestRefundCommandHandler implements NewCommandSourceHandler {

    private final LoanWritePlatformService loanWritePlatformService;
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/handler/ManualInterestRefundCommandHandler.java

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
<<<<<<<< HEAD:fineract-provider/src/main/java/org/apache/fineract/useradministration/handler/ChangeUserPasswordCommandHandler.java
        final Long userId = command.entityId();
        return this.writePlatformService.changeUserPassword(userId, command);
========
        return loanWritePlatformService.makeManualInterestRefund(command.getLoanId(), command.entityId(), command);
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/handler/ManualInterestRefundCommandHandler.java
    }
}
