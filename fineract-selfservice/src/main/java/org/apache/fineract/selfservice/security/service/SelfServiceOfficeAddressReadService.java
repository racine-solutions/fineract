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
package org.apache.fineract.selfservice.security.service;

/** Read platform service for retrieving office address-related data. */
public interface SelfServiceOfficeAddressReadService {

    /**
     * Retrieves the country name of the office address associated with the given client.
     *
     * <p>
     * The lookup chain is: Client → Office → Office Address → Address → Country (CodeValue). Returns an empty string if
     * any link in the chain is missing or null.
     *
     * @param clientId
     *            the client identifier; may be {@code null}
     * @return the country name, or an empty string as fallback
     */
    String retrieveOfficeCountryByClientId(Long clientId);
}
