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

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC-backed implementation of {@link SelfServiceOfficeAddressReadService}.
 *
 * <p>
 * Resolves the country by traversing: m_client → m_selfservice_office_address → m_address → m_code_value. Returns an
 * empty string for any missing link rather than throwing, as the country is optional/enrichment data for the
 * authentication response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SelfServiceOfficeAddressReadServiceImpl implements SelfServiceOfficeAddressReadService {

    private static final String COUNTRY_QUERY = "SELECT cv.code_value " + "FROM m_client c "
            + "INNER JOIN m_office_address oa ON c.office_id = oa.office_id AND oa.is_active = true "
            + "INNER JOIN m_address a ON oa.address_id = a.id " + "INNER JOIN m_code_value cv ON a.country_id = cv.id " + "WHERE c.id = ?";

    private final JdbcTemplate jdbcTemplate;

    private volatile boolean officeAddressTableAvailable;

    @PostConstruct
    void checkTableAvailability() {
        try {
            jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conn -> {
                try (ResultSet rs = conn.getMetaData().getTables(null, null, "m_office_address", null)) {
                    officeAddressTableAvailable = rs.next();
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to detect m_office_address table availability; address country retrieval disabled", e);
            officeAddressTableAvailable = false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String retrieveOfficeCountryByClientId(final Long clientId) {
        if (clientId == null || !officeAddressTableAvailable) {
            log.debug("clientId is null or m_office_address table not available; returning empty country");
            return "";
        }
        try {
            final String country = jdbcTemplate.queryForObject(COUNTRY_QUERY, String.class, clientId);
            return country != null ? country : "";
        } catch (final EmptyResultDataAccessException e) {
            log.debug("No active office address with country found for clientId={}; returning empty", clientId);
            return "";
        } catch (final Exception e) {
            log.warn("Unexpected error retrieving country for clientId={}; returning empty", clientId, e);
            return "";
        }
    }
}
