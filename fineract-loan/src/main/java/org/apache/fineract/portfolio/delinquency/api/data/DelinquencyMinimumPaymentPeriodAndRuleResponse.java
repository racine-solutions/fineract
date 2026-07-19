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
<<<<<<<< HEAD:fineract-progressive-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/data/CapitalizedIncomeDetails.java
package org.apache.fineract.portfolio.loanaccount.data;

========

package org.apache.fineract.portfolio.delinquency.api.data;

>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-loan/src/main/java/org/apache/fineract/portfolio/delinquency/api/data/DelinquencyMinimumPaymentPeriodAndRuleResponse.java
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.fineract.infrastructure.core.data.StringEnumOptionData;

<<<<<<<< HEAD:fineract-progressive-loan/src/main/java/org/apache/fineract/portfolio/loanaccount/data/CapitalizedIncomeDetails.java
@AllArgsConstructor
@ToString
@Getter
@Setter
public class CapitalizedIncomeDetails {

    private BigDecimal amount;
    private BigDecimal amortizedAmount;
    private BigDecimal unrecognizedAmount;
    private BigDecimal amountAdjustment;
    private BigDecimal chargedOffAmount;
========
@ToString
@AllArgsConstructor
@Getter
@Setter
public class DelinquencyMinimumPaymentPeriodAndRuleResponse {

    private Integer frequency;
    private StringEnumOptionData frequencyType;
    private BigDecimal minimumPayment;
    private StringEnumOptionData minimumPaymentType;
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-loan/src/main/java/org/apache/fineract/portfolio/delinquency/api/data/DelinquencyMinimumPaymentPeriodAndRuleResponse.java
}
