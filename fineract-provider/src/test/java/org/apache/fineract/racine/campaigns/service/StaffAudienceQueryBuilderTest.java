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

import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.racine.campaigns.data.AudienceFilter;
import org.junit.jupiter.api.Test;

class StaffAudienceQueryBuilderTest {

    private static final AudienceFilter STAFF = new AudienceFilter("RECIPIENTS", "IN", List.of("STAFF"), null);

    private static StaffAudienceQueryBuilder build(final AudienceFilter... filters) {
        return StaffAudienceQueryBuilder.of(List.of(filters), ".1.");
    }

    @Test
    void theRecipientsFilterMarksAStaffAudience() {
        assertThat(StaffAudienceQueryBuilder.isStaffAudience(List.of(STAFF))).isTrue();
        assertThat(StaffAudienceQueryBuilder.isStaffAudience(List.of(new AudienceFilter("GENDER", "IN", List.of("1"), null)))).isFalse();
        assertThat(StaffAudienceQueryBuilder.isStaffAudience(null)).isFalse();
    }

    @Test
    void activeStaffAndEnabledSystemUsersWithoutAStaffRecordByDefault() {
        final StaffAudienceQueryBuilder query = build(STAFF);
        assertThat(query.includeStaff()).isTrue();
        assertThat(query.includeUsers()).isTrue();
        assertThat(query.staffWhere()).contains("o.hierarchy like :s0").contains("s.is_active = :s1");
        assertThat(query.userWhere()).contains("u.is_deleted = :s2").contains("u.enabled = :s1").contains("u.staff_id is null");
    }

    @Test
    void loanOfficersOnlyLeavesOutSystemUsersWithoutAStaffRecord() {
        final StaffAudienceQueryBuilder query = build(STAFF, new AudienceFilter("STAFF_TYPE", "IN", List.of("LOAN_OFFICER"), null));
        assertThat(query.staffWhere()).contains("s.is_loan_officer = :");
        assertThat(query.includeUsers()).isFalse();
        final StaffAudienceQueryBuilder users = build(STAFF,
                new AudienceFilter("STAFF_TYPE", "IN", List.of("LOAN_OFFICER", "SYSTEM_USER"), null));
        assertThat(users.staffWhere()).contains(" or exists (select 1 from m_appuser lu");
        assertThat(users.includeUsers()).isTrue();
    }

    @Test
    void specificStaffAndUsersCanBeChosenOrLeftOut() {
        final StaffAudienceQueryBuilder chosen = build(STAFF, new AudienceFilter("STAFF_MEMBER", "IN", List.of("STAFF:3"), null));
        assertThat(chosen.staffWhere()).contains("s.id in (:");
        assertThat(chosen.includeUsers()).isFalse();
        final StaffAudienceQueryBuilder leftOut = build(STAFF,
                new AudienceFilter("STAFF_MEMBER", "NOT_IN", List.of("STAFF:3", "USER:2"), null));
        assertThat(leftOut.staffWhere()).contains("s.id not in (:");
        assertThat(leftOut.userWhere()).contains("u.id not in (:");
        assertThat(leftOut.includeStaff()).isTrue();
        assertThatThrownBy(() -> build(STAFF, new AudienceFilter("STAFF_MEMBER", "IN", List.of("7"), null)))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rolesAndOfficesApplyToStaffAndUsers() {
        final StaffAudienceQueryBuilder query = build(STAFF, new AudienceFilter("STAFF_ROLE", "IN", List.of("1"), null),
                new AudienceFilter("STAFF_OFFICE", "IN", List.of("2"), true));
        assertThat(query.staffWhere()).contains("ar.role_id in (:").contains("o.hierarchy like concat(po.hierarchy, '%')");
        assertThat(query.userWhere()).contains("ar.appuser_id = u.id").contains("o.hierarchy like concat(po.hierarchy, '%')");
    }

    @Test
    void isAlsoAClientIsAnsweredAfterTheQuery() {
        assertThat(StaffAudienceQueryBuilder.isClientWanted(List.of(STAFF))).isNull();
        assertThat(
                StaffAudienceQueryBuilder.isClientWanted(List.of(STAFF, new AudienceFilter("STAFF_IS_CLIENT", "IN", List.of("YES"), null))))
                .isTrue();
        assertThat(StaffAudienceQueryBuilder.isClientWanted(List.of(new AudienceFilter("STAFF_IS_CLIENT", "IN", List.of("NO"), null))))
                .isFalse();
        assertThat(build(STAFF, new AudienceFilter("STAFF_IS_CLIENT", "IN", List.of("YES"), null)).staffWhere())
                .doesNotContain("STAFF_IS_CLIENT");
    }

    @Test
    void savingChecksStaffFiltersWithTheStaffRules() {
        AudienceService.validate(List.of(STAFF, new AudienceFilter("STAFF_TYPE", "IN", List.of("SYSTEM_USER"), null)), ".1.");
        assertThatThrownBy(
                () -> AudienceService.validate(List.of(STAFF, new AudienceFilter("STAFF_TYPE", "IN", List.of("BOSS"), null)), ".1."))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }
}
