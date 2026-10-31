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
package org.apache.fineract.selfservice.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.exception.OfficeNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SelfServiceReportReadServiceTest {

    private JdbcTemplate jdbcTemplate;
    private SelfServiceReportReadService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        when(context.officeHierarchy()).thenReturn(".1.2.");
        service = new SelfServiceReportReadService(jdbcTemplate, context);
    }

    @Test
    void withoutOffice_coversTheStaffMembersOfficeRange() {
        assertThat(service.scopeHierarchy(null)).isEqualTo(".1.2.");
    }

    @Test
    void childOffice_narrowsTheReport() {
        when(jdbcTemplate.queryForList(startsWith("select hierarchy from m_office"), eq(String.class), eq(5L))).thenReturn(List.of(".1.2.5."));

        assertThat(service.scopeHierarchy(5L)).isEqualTo(".1.2.5.");
    }

    @Test
    void officeOutsideTheStaffMembersRange_isNotFound() {
        when(jdbcTemplate.queryForList(startsWith("select hierarchy from m_office"), eq(String.class), eq(3L))).thenReturn(List.of(".1.3."));

        assertThatThrownBy(() -> service.scopeHierarchy(3L)).isInstanceOf(OfficeNotFoundException.class);
    }

    @Test
    void refusalReasons_areReadFromTheEventDetails() {
        assertThat(SelfServiceReportReadService
                .refusalReason("{\"reasonCode\":\"error.msg.selfservice.transfer.limit.per.transfer.exceeded\"}"))
                .isEqualTo("PER_TRANSFER_LIMIT");
        assertThat(SelfServiceReportReadService.refusalReason("{\"reasonCode\":\"error.msg.selfservice.transfer.limit.daily.exceeded\"}"))
                .isEqualTo("DAILY_LIMIT");
        assertThat(SelfServiceReportReadService.refusalReason("{\"reasonCode\":\"error.msg.saving.account.id.invalid\"}"))
                .isEqualTo("OWNERSHIP");
        assertThat(SelfServiceReportReadService.refusalReason("not json")).isEqualTo("OTHER");
        assertThat(SelfServiceReportReadService.refusalReason(null)).isEqualTo("OTHER");
    }

    @Test
    void adoptionRate_isAPercentageWithOneDecimal() {
        assertThat(SelfServiceReportReadService.rate(1, 3)).isEqualByComparingTo("33.3");
        assertThat(SelfServiceReportReadService.rate(5, 0)).isEqualByComparingTo("0");
    }
}
