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
<<<<<<<< HEAD:fineract-provider/src/main/java/org/apache/fineract/infrastructure/jobs/service/aggregationjob/data/JournalEntryAggregationSummaryData.java
package org.apache.fineract.infrastructure.jobs.service.aggregationjob.data;
========
package org.apache.fineract.portfolio.collateralmanagement.data;
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-provider/src/main/java/org/apache/fineract/portfolio/collateralmanagement/data/ClientCollateralUpdateResponse.java

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
<<<<<<<< HEAD:fineract-provider/src/main/java/org/apache/fineract/infrastructure/jobs/service/aggregationjob/data/JournalEntryAggregationSummaryData.java
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder(toBuilder = true)
public class JournalEntryAggregationSummaryData {

    private Long productId;
    private Long glAccountId;
    private Long office;
    private Long entityTypeEnum;
    private LocalDate submittedOnDate;
    private LocalDate aggregatedOnDate;
    private Long externalOwnerId;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private Boolean manualEntry;
    private String currencyCode;
    private Long jobExecutionId;
========
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientCollateralUpdateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long resourceId;

    private Long clientId;

    private Changes changes;

    @Data
    @Builder
    public static class Changes implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private BigDecimal quantity;

        private String locale;
    }
>>>>>>>> origin/racine-dev-1.15.0-preview:fineract-provider/src/main/java/org/apache/fineract/portfolio/collateralmanagement/data/ClientCollateralUpdateResponse.java
}
