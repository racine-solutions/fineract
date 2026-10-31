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
package org.apache.fineract.selfservice.loanaccount.service;

import java.util.Collection;
import org.apache.fineract.selfservice.loanaccount.data.SelfPublicLoanProductData;

/**
 * Reads loan product data for public (unauthenticated) self-service endpoints.
 *
 * <p>
 * This service bypasses all core Fineract service-layer auth checks by using direct SQL queries. Only active products
 * are returned, with simulation-relevant fields only.
 *
 * <p>
 * Bytecode analysis performed against {@code fineract-provider:1.15.0-SNAPSHOT} (artifact {@code
 * 20260329.095314-5}).
 */
public interface SelfPublicLoanProductReadService {

    /**
     * Retrieves all active loan products with simulation-relevant fields (currency, principal ranges, interest rates,
     * repayment configuration, etc.).
     *
     * <p>
     * Active products are those where {@code close_date IS NULL OR close_date >=
     * currentBusinessDate}.
     *
     * @return a collection of {@link SelfPublicLoanProductData} for active products
     */
    Collection<SelfPublicLoanProductData> retrieveAllActiveLoanProducts();
}
