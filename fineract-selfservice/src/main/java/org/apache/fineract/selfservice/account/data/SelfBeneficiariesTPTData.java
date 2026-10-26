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
package org.apache.fineract.selfservice.account.data;

import java.util.Collection;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

public class SelfBeneficiariesTPTData {

    @SuppressWarnings("unused")
    private final Long id;

    @SuppressWarnings("unused")
    private final String name;

    @SuppressWarnings("unused")
    private final String officeName;

    @SuppressWarnings("unused")
    private final String clientName;

    @SuppressWarnings("unused")
    private final EnumOptionData accountType;

    @SuppressWarnings("unused")
    private final String accountNumber;

    @SuppressWarnings("unused")
    private final Long transferLimit;

    @SuppressWarnings("unused")
    private final Collection<EnumOptionData> accountTypeOptions;

    @SuppressWarnings("unused")
    private final String customAccountNumber;

    @SuppressWarnings("unused")
    private final String holderName;

    @SuppressWarnings("unused")
    private final String holderId;

    @SuppressWarnings("unused")
    private final Integer holderIdType;

    @SuppressWarnings("unused")
    private final String currencyCode;

    @SuppressWarnings("unused")
    private final String entityCode;

    @SuppressWarnings("unused")
    private final String entityName;

    // Internal Fineract identifiers backing this beneficiary's office/client/account, needed by
    // self-service client applications to actually submit a transfer to this beneficiary (the
    // officeName/clientName/accountNumber fields above are display-only). Null for external
    // (SINPE/PIN) beneficiaries, which have no corresponding internal Fineract office/client/account.
    @SuppressWarnings("unused")
    private final Long officeId;

    @SuppressWarnings("unused")
    private final Long clientId;

    @SuppressWarnings("unused")
    private final Long accountId;

    public SelfBeneficiariesTPTData(final Collection<EnumOptionData> accountTypeOptions) {
        this.accountTypeOptions = accountTypeOptions;
        this.id = null;
        this.name = null;
        this.officeName = null;
        this.clientName = null;
        this.accountType = null;
        this.accountNumber = null;
        this.transferLimit = null;

        this.customAccountNumber = null;
        this.holderName = null;
        this.holderId = null;
        this.holderIdType = null;
        this.currencyCode = null;
        this.entityCode = null;
        this.entityName = null;

        this.officeId = null;
        this.clientId = null;
        this.accountId = null;
    }

    public SelfBeneficiariesTPTData(final Long id, final String name, final String officeName, final String clientName,
            final EnumOptionData accountType, final String accountNumber, final Long transferLimit) {
        this.accountTypeOptions = null;
        this.id = id;
        this.name = name;
        this.officeName = officeName;
        this.clientName = clientName;
        this.accountType = accountType;
        this.accountNumber = accountNumber;
        this.transferLimit = transferLimit;

        this.customAccountNumber = null;
        this.holderName = null;
        this.holderId = null;
        this.holderIdType = null;
        this.currencyCode = null;
        this.entityCode = null;
        this.entityName = null;

        this.officeId = null;
        this.clientId = null;
        this.accountId = null;
    }

    public SelfBeneficiariesTPTData(final Long id, final String name, final String officeName, final String clientName,
            final EnumOptionData accountType, final String accountNumber, final Long transferLimit, final String customAccountNumber,
            final String holderName, final String holderId, final Integer holderIdType, final String currencyCode, final String entityCode,
            final String entityName, final Long officeId, final Long clientId, final Long accountId) {
        this.accountTypeOptions = null;
        this.id = id;
        this.name = name;
        this.officeName = officeName;
        this.clientName = clientName;
        this.accountType = accountType;
        this.accountNumber = accountNumber;
        this.transferLimit = transferLimit;

        // Asignación de los campos nuevos
        this.customAccountNumber = customAccountNumber;
        this.holderName = holderName;
        this.holderId = holderId;
        this.holderIdType = holderIdType;
        this.currencyCode = currencyCode;
        this.entityCode = entityCode;
        this.entityName = entityName;

        this.officeId = officeId;
        this.clientId = clientId;
        this.accountId = accountId;
    }
}
