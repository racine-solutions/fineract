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
<<<<<<<< HEAD:fineract-core/src/main/java/org/apache/fineract/infrastructure/core/serialization/gson/JsonExcludeAnnotationBasedExclusionStrategy.java
package org.apache.fineract.infrastructure.core.serialization.gson;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

/**
 * Exclusion strategy to handle {@link JsonExclude} annotations to exclude fields from serialization and
 * deserialization.
 */
public class JsonExcludeAnnotationBasedExclusionStrategy implements ExclusionStrategy {

    @Override
    public boolean shouldSkipField(FieldAttributes fieldAttributes) {
        JsonExclude annotation = fieldAttributes.getAnnotation(JsonExclude.class);
        return annotation != null;
    }

    @Override
    public boolean shouldSkipClass(Class<?> aClass) {
        return false;
========
package org.apache.fineract.portfolio.workingcapitalloanproduct.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

/**
 * A {@link RuntimeException} thrown when a Working Capital Loan Product with the same short name already exists.
 */
public class WorkingCapitalLoanProductDuplicateShortNameException extends AbstractPlatformDomainRuleException {

    public WorkingCapitalLoanProductDuplicateShortNameException(final String shortName) {
        super("error.msg.wc.product.duplicate.shortName", "Working Capital Loan Product with short name '" + shortName + "' already exists",
                shortName);
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-working-capital-loan/src/main/java/org/apache/fineract/portfolio/workingcapitalloanproduct/exception/WorkingCapitalLoanProductDuplicateShortNameException.java
    }
}
