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
package org.apache.fineract.selfservice.admin.service;

import lombok.Builder;
import lombok.Getter;

/** Filters, paging and sorting for the back-office list of self-service users. */
@Getter
@Builder
public class SelfServiceUserSearchParameters {

    private final Long officeId;
    private final Integer status;
    private final Boolean locked;
    private final Long clientId;
    private final String search;
    private final Integer offset;
    private final Integer limit;
    private final String orderBy;
    private final String sortOrder;
}
