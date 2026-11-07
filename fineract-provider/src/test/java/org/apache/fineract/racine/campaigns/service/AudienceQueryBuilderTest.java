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
package org.apache.fineract.racine.campaigns.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.racine.campaigns.data.AudienceFilter;
import org.junit.jupiter.api.Test;

class AudienceQueryBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private static AudienceQueryBuilder build(final AudienceFilter... filters) {
        return AudienceQueryBuilder.of(List.of(filters), ".1.", TODAY);
    }

    @Test
    void onlyActiveClientsOfTheStaffOfficesByDefault() {
        final AudienceQueryBuilder query = build();
        assertThat(query.where()).isEqualTo("o.hierarchy like :scopeHierarchy and c.status_enum = :defaultStatus");
        assertThat(query.params().getValue("scopeHierarchy")).isEqualTo(".1.%");
        assertThat(query.params().getValue("defaultStatus")).isEqualTo(300);
    }

    @Test
    void ageBecomesDateOfBirthBounds() {
        final AudienceQueryBuilder query = build(new AudienceFilter("AGE", "BETWEEN", List.of("18", "60"), null));
        assertThat(query.where()).contains("c.date_of_birth <= :p0").contains("c.date_of_birth > :p1");
        // 18 or older: born on or before 6 Nov 2008; 60 or younger: born after 6 Nov 1965
        assertThat(query.params().getValue("p0")).isEqualTo(LocalDate.of(2008, 11, 6));
        assertThat(query.params().getValue("p1")).isEqualTo(LocalDate.of(1965, 11, 6));
    }

    @Test
    void delinquencyCanIncludeOrExcludeRanges() {
        assertThat(build(new AudienceFilter("DELINQUENCY", "IN", List.of("2", "3"), null)).where())
                .contains("and exists (select 1 from m_loan l join m_loan_delinquency_tag_history h");
        final AudienceQueryBuilder excluded = build(new AudienceFilter("DELINQUENCY", "NOT_IN", List.of("4"), null));
        assertThat(excluded.where()).contains("not exists (select 1 from m_loan l join m_loan_delinquency_tag_history h")
                .contains("h.liftedon_date is null");
    }

    @Test
    void valuesAreBoundNeverWrittenIntoTheQuery() {
        final AudienceQueryBuilder query = build(new AudienceFilter("GENDER", "IN", List.of("15"), null),
                new AudienceFilter("CLIENT_STATUS", "IN", List.of("ACTIVE", "PENDING"), null));
        assertThat(query.where()).doesNotContain("15").contains("c.gender_cv_id in (:p0)").contains("c.status_enum in (:p1)")
                .doesNotContain("defaultStatus");
        assertThat(query.params().getValue("p0")).isEqualTo(List.of(15L));
        assertThat(query.params().getValue("p1")).isEqualTo(List.of(300, 100));
    }

    @Test
    void specificClientsCanBeChosenOrLeftOut() {
        final AudienceQueryBuilder chosen = build(new AudienceFilter("CLIENT", "IN", List.of("1", "7"), null));
        assertThat(chosen.where()).contains("c.id in (:p0)");
        assertThat(chosen.params().getValue("p0")).isEqualTo(List.of(1L, 7L));
        assertThat(build(new AudienceFilter("CLIENT", "NOT_IN", List.of("2"), null)).where()).contains("c.id not in (:p0)");
    }

    @Test
    void officeWithChildOfficesUsesTheHierarchy() {
        assertThat(build(new AudienceFilter("OFFICE", "IN", List.of("3"), true)).where())
                .contains("exists (select 1 from m_office po where po.id in (:p0) and o.hierarchy like concat(po.hierarchy, '%'))");
    }

    @Test
    void badFiltersAreRefusedWithAClearMessage() {
        assertThatThrownBy(() -> build(new AudienceFilter("SALARY", "IN", List.of("1"), null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
        assertThatThrownBy(() -> build(new AudienceFilter("GENDER", "IN", List.of("1; drop table m_client"), null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
        assertThatThrownBy(() -> build(new AudienceFilter("AGE", "BETWEEN", List.of(), null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void everyOfferedFieldBuildsWithSampleValues() {
        final java.util.Map<String, List<String>> samples = new java.util.HashMap<>();
        samples.put("CLIENT_STATUS", List.of("ACTIVE"));
        samples.put("LEGAL_FORM", List.of("PERSON"));
        samples.put("AGE", List.of("18", "60"));
        samples.put("MEMBER_FOR", List.of("1", "5"));
        samples.put("BIRTHDAY", List.of("THIS_MONTH"));
        samples.put("MOBILE_APP", List.of("USES"));
        samples.put("SAVINGS_BALANCE", List.of("1000", ""));
        samples.put("LOAN_BALANCE", List.of("", "500000"));
        samples.put("DAYS_IN_ARREARS", List.of("30", "90"));
        samples.put("LOANS_TAKEN", List.of("2", ""));
        for (final String yesNo : List.of("HAS_ACTIVE_LOAN", "HAS_SAVINGS", "HAS_FIXED_DEPOSIT", "HAS_RECURRING_DEPOSIT", "HAS_SHARES",
                "HAS_PHONE", "HAS_EMAIL", "IN_GROUP", "LOAN_PENDING")) {
            samples.put(yesNo, List.of("YES"));
        }
        AudienceQueryBuilder.FIELDS.forEach(field -> {
            final String name = field.get("field");
            final AudienceQueryBuilder query = build(new AudienceFilter(name, "IN", samples.getOrDefault(name, List.of("30")), true));
            assertThat(query.where()).as(name).startsWith("o.hierarchy like :scopeHierarchy and ");
        });
    }

    @Test
    void daysInArrearsBecomesOverdueSinceBounds() {
        final AudienceQueryBuilder query = build(new AudienceFilter("DAYS_IN_ARREARS", "BETWEEN", List.of("30", "90"), null));
        assertThat(query.where()).contains("a.overdue_since_date_derived <= :p1").contains("a.overdue_since_date_derived >= :p2");
        assertThat(query.params().getValue("p1")).isEqualTo(TODAY.minusDays(30));
        assertThat(query.params().getValue("p2")).isEqualTo(TODAY.minusDays(90));
    }

    @Test
    void loanBalanceAndGroupFilters() {
        final AudienceQueryBuilder balance = build(new AudienceFilter("LOAN_BALANCE", "BETWEEN", List.of("100000", ""), null));
        assertThat(balance.where()).contains("sum(l.total_outstanding_derived)").contains(">= :p1").doesNotContain("<=");
        assertThat(build(new AudienceFilter("GROUP", "NOT_IN", List.of("3"), null)).where())
                .contains("not exists (select 1 from m_group_client gc");
        assertThat(build(new AudienceFilter("HAS_EMAIL", "IN", List.of("NO"), null)).where())
                .contains("(c.email_address is null or c.email_address = '')");
    }

    @Test
    void noDepositInNeedsASavingsAccount() {
        final AudienceQueryBuilder query = build(new AudienceFilter("NO_DEPOSIT_IN", "IN", List.of("60"), null));
        assertThat(query.where()).contains("exists (select 1 from m_savings_account s")
                .contains("not exists (select 1 from" + " m_savings_account_transaction st");
        assertThat(query.params().getValues()).containsValue(TODAY.minusDays(60));
    }
}
