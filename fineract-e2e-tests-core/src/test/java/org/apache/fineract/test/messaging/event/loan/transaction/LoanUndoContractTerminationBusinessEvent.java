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
<<<<<<<< HEAD:fineract-core/src/main/java/org/apache/fineract/infrastructure/event/sms/service/SmsEventConfigurationReadPlatformService.java
package org.apache.fineract.infrastructure.event.sms.service;

import org.apache.fineract.infrastructure.event.external.data.SmsEventConfigurationData;

public interface SmsEventConfigurationReadPlatformService {

    SmsEventConfigurationData findAllSmsEventConfigurations();
========
package org.apache.fineract.test.messaging.event.loan.transaction;

public class LoanUndoContractTerminationBusinessEvent extends AbstractLoanTransactionEvent {

    @Override
    public String getEventName() {
        return "LoanUndoContractTerminationBusinessEvent";
    }
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-e2e-tests-core/src/test/java/org/apache/fineract/test/messaging/event/loan/transaction/LoanUndoContractTerminationBusinessEvent.java
}
