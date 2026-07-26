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
package org.apache.fineract.portfolio.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientScreeningReadPlatformServiceImpl implements ClientScreeningReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final ConfigurationReadPlatformService configurationReadPlatformService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    @Value("${fineract.screening.watchman-url:http://209.38.225.231:8084}")
    private String defaultWatchmanUrl;

    @Value("${fineract.screening.match-threshold:90.0}")
    private double defaultThreshold;

    @Value("${fineract.screening.limit:5}")
    private int defaultLimit;

    private static class ClientScreeningInfo {
        String displayName;
        Integer legalFormEnum;
        String address;
        String city;
        String state;
        String postalCode;
        String country;
    }

    @Override
    public String screenClient(Long clientId) {
        // Validate if there is a pending review screening run
        final String checkSql = "SELECT COUNT(1) FROM m_client_screening WHERE client_id = ? AND review_status = 'PENDING'";
        try {
            Integer pendingCount = this.jdbcTemplate.queryForObject(checkSql, Integer.class, clientId);
            if (pendingCount != null && pendingCount > 0) {
                log.warn("Blocking screening run for client ID {}: previous run is still PENDING review", clientId);
                JsonObject errorResponse = createEmptyResponse("", 0.90, 5);
                errorResponse.addProperty("error", "Existing screening results are pending review. You must review the previous search before initiating a new one.");
                return gson.toJson(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error checking pending screening status for client ID: {}", clientId, e);
        }

        final String sql = "SELECT c.display_name AS displayName, c.legal_form_enum AS legalFormEnum, "
                + "a.street AS street, a.address_line_1 AS addressLine1, a.address_line_2 AS addressLine2, a.address_line_3 AS addressLine3, "
                + "a.city AS city, state.code_value AS stateName, a.postal_code AS postalCode, country.code_value AS countryName "
                + "FROM m_client c "
                + "LEFT JOIN m_client_address ca ON c.id = ca.client_id AND ca.is_active = true "
                + "LEFT JOIN m_address a ON a.id = ca.address_id "
                + "LEFT JOIN m_code_value state ON state.id = a.state_province_id "
                + "LEFT JOIN m_code_value country ON country.id = a.country_id "
                + "WHERE c.id = ? LIMIT 1";

        ClientScreeningInfo info;
        try {
            info = this.jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                ClientScreeningInfo infoObj = new ClientScreeningInfo();
                infoObj.displayName = rs.getString("displayName");
                infoObj.legalFormEnum = rs.getObject("legalFormEnum") != null ? rs.getInt("legalFormEnum") : null;
                
                StringBuilder addressSb = new StringBuilder();
                String street = rs.getString("street");
                String line1 = rs.getString("addressLine1");
                if (StringUtils.isNotBlank(street)) {
                    addressSb.append(street);
                }
                if (StringUtils.isNotBlank(line1)) {
                    if (addressSb.length() > 0) addressSb.append(", ");
                    addressSb.append(line1);
                }
                if (addressSb.length() > 0) {
                    infoObj.address = addressSb.toString();
                }

                infoObj.city = rs.getString("city");
                infoObj.state = rs.getString("stateName");
                infoObj.postalCode = rs.getString("postalCode");
                infoObj.country = rs.getString("countryName");
                return infoObj;
            }, clientId);
        } catch (EmptyResultDataAccessException e) {
            log.warn("Client not found in m_client table with ID: {}", clientId);
            JsonObject errorResponse = createEmptyResponse("", 0.90, 5);
            errorResponse.addProperty("error", "Client not found");
            return gson.toJson(errorResponse);
        }

        if (info == null || StringUtils.isBlank(info.displayName)) {
            JsonObject errorResponse = createEmptyResponse("", 0.90, 5);
            errorResponse.addProperty("error", "Client display name is empty");
            return gson.toJson(errorResponse);
        }

        // Determine type from legalForm
        String type = null;
        if (info.legalFormEnum != null) {
            if (info.legalFormEnum == 1) {
                type = "person";
            } else if (info.legalFormEnum == 2) {
                type = "entity";
            }
        }

        String watchmanUrl = defaultWatchmanUrl;
        double threshold = defaultThreshold / 100.0;
        int maxResults = defaultLimit;

        // Load configs from c_configuration
        try {
            final GlobalConfigurationPropertyData urlConfig = this.configurationReadPlatformService
                    .retrieveGlobalConfiguration(GlobalConfigurationConstants.CLIENT_SCREENING_WATCHMAN_URL);
            if (urlConfig.isEnabled() && StringUtils.isNotBlank(urlConfig.getStringValue())) {
                watchmanUrl = urlConfig.getStringValue().trim();
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve watchman url configuration, using default: {}", watchmanUrl, e);
        }

        try {
            final GlobalConfigurationPropertyData thresholdConfig = this.configurationReadPlatformService
                    .retrieveGlobalConfiguration(GlobalConfigurationConstants.CLIENT_SCREENING_MATCH_THRESHOLD);
            if (thresholdConfig.isEnabled() && thresholdConfig.getValue() != null) {
                threshold = thresholdConfig.getValue().doubleValue() / 100.0;
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve match threshold configuration, using default: {}", threshold, e);
        }

        try {
            final GlobalConfigurationPropertyData limitConfig = this.configurationReadPlatformService
                    .retrieveGlobalConfiguration(GlobalConfigurationConstants.CLIENT_SCREENING_LIMIT);
            if (limitConfig.isEnabled() && limitConfig.getValue() != null) {
                maxResults = limitConfig.getValue().intValue();
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve search limit configuration, using default: {}", maxResults, e);
        }

        // Build query parameter map
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("name", info.displayName);
        queryParams.put("type", type);
        queryParams.put("address", info.address);
        queryParams.put("city", info.city);
        queryParams.put("state", info.state);
        queryParams.put("postalCode", info.postalCode);
        queryParams.put("country", info.country);
        queryParams.put("minMatch", String.valueOf(threshold));
        queryParams.put("limit", String.valueOf(maxResults));

        String searchUrl = buildUrl(watchmanUrl, queryParams);
        log.info("Performing watchman client screening using URL: '{}'", searchUrl);

        try {
            String responseBody = restTemplate.getForObject(searchUrl, String.class);
            if (responseBody == null) {
                return gson.toJson(createEmptyResponse(info.displayName, threshold, maxResults));
            }

            JsonObject originalResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonObject filteredResponse = new JsonObject();
            boolean isMatchFound = false;

            for (Map.Entry<String, JsonElement> entry : originalResponse.entrySet()) {
                String key = entry.getKey();
                JsonElement element = entry.getValue();
                if (element != null && element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    JsonArray filteredArray = new JsonArray();
                    for (JsonElement item : array) {
                        if (item.isJsonObject()) {
                            JsonObject itemObj = item.getAsJsonObject();
                            if (itemObj.has("match") && itemObj.get("match").isJsonPrimitive()) {
                                double matchScore = itemObj.get("match").getAsDouble();
                                if (matchScore >= threshold) {
                                    filteredArray.add(itemObj);
                                    isMatchFound = true;
                                }
                            }
                        }
                    }
                    if (filteredArray.size() > 0) {
                        filteredResponse.add(key, filteredArray);
                    }
                }
            }

            filteredResponse.addProperty("displayName", info.displayName);
            filteredResponse.addProperty("matchThreshold", threshold);
            filteredResponse.addProperty("limit", maxResults);
            filteredResponse.addProperty("isMatchFound", isMatchFound);
            filteredResponse.addProperty("reviewStatus", "PENDING");

            String resultJson = gson.toJson(filteredResponse);

            // Persist screening results in DB with PENDING review_status
            final String insertSql = "INSERT INTO m_client_screening (client_id, is_match_found, match_threshold, screening_date, results_json, review_status) "
                    + "VALUES (?, ?, ?, ?, ?, 'PENDING')";
            
            org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
            final double finalThreshold = threshold;
            final boolean finalIsMatchFound = isMatchFound;
            final String finalResultJson = resultJson;
            this.jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement ps = connection.prepareStatement(insertSql, java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, clientId);
                ps.setBoolean(2, finalIsMatchFound);
                ps.setDouble(3, finalThreshold);
                ps.setTimestamp(4, new java.sql.Timestamp(new java.util.Date().getTime()));
                ps.setString(5, finalResultJson);
                return ps;
            }, keyHolder);

            Long generatedId = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
            if (generatedId != null) {
                filteredResponse.addProperty("id", generatedId);
                filteredResponse.addProperty("screeningId", generatedId);
                resultJson = gson.toJson(filteredResponse);
            }

            return resultJson;

        } catch (Exception e) {
            log.error("Error calling Watchman API screening service: ", e);
            JsonObject errorResponse = createEmptyResponse(info.displayName, threshold, maxResults);
            errorResponse.addProperty("error", "Failed to contact screening service: " + e.getMessage());
            return gson.toJson(errorResponse);
        }
    }

    @Override
    public String getScreeningResults(Long clientId) {
        final String sql = "SELECT results_json, review_status, id FROM m_client_screening "
                + "WHERE client_id = ? "
                + "ORDER BY screening_date DESC";
        try {
            return this.jdbcTemplate.query(sql, rs -> {
                JsonArray array = new JsonArray();
                while (rs.next()) {
                    String res = rs.getString("results_json");
                    String statusVal = rs.getString("review_status");
                    long screeningId = rs.getLong("id");
                    if (StringUtils.isNotBlank(res)) {
                        try {
                            JsonObject obj = gson.fromJson(res, JsonObject.class);
                            obj.addProperty("reviewStatus", statusVal);
                            obj.addProperty("id", screeningId);
                            obj.addProperty("screeningId", screeningId);
                            array.add(obj);
                        } catch (Exception parseEx) {
                            try {
                                JsonElement el = gson.fromJson(res, JsonElement.class);
                                array.add(el);
                            } catch (Exception parseEx2) {
                                array.add(res);
                            }
                        }
                    }
                }
                return gson.toJson(array);
            }, clientId);
        } catch (Exception e) {
            log.error("Error retrieving screening results for client ID: {}", clientId, e);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("clientId", clientId);
            errorResponse.addProperty("error", "Failed to retrieve screening results: " + e.getMessage());
            return gson.toJson(errorResponse);
        }
    }

    @Override
    public String reviewScreening(Long screeningId, String reviewStatus) {
        if (StringUtils.isBlank(reviewStatus) 
                || (!reviewStatus.equalsIgnoreCase("MATCH") && !reviewStatus.equalsIgnoreCase("NOT_MATCH"))) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", "Invalid review status. Must be MATCH or NOT_MATCH");
            return gson.toJson(errorResponse);
        }

        final String finalReviewStatus = reviewStatus.toUpperCase();

        try {
            // Update status in DB by screening record primary key ID
            final String updateSql = "UPDATE m_client_screening SET review_status = ? WHERE id = ?";
            int rows = this.jdbcTemplate.update(updateSql, finalReviewStatus, screeningId);
            
            if (rows == 0) {
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("error", "Screening record not found with ID: " + screeningId);
                return gson.toJson(errorResponse);
            }

            JsonObject successResponse = new JsonObject();
            successResponse.addProperty("screeningId", screeningId);
            successResponse.addProperty("status", "reviewed");
            successResponse.addProperty("result", finalReviewStatus);
            return gson.toJson(successResponse);

        } catch (Exception e) {
            log.error("Error reviewing screening for ID {}: ", screeningId, e);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", "Failed to complete review: " + e.getMessage());
            return gson.toJson(errorResponse);
        }
    }

    private String buildUrl(String baseUrl, Map<String, String> queryParams) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        StringBuilder urlBuilder = new StringBuilder(baseUrl).append("/search");
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (StringUtils.isNotBlank(entry.getValue())) {
                if (first) {
                    urlBuilder.append("?");
                    first = false;
                } else {
                    urlBuilder.append("&");
                }
                try {
                    urlBuilder.append(entry.getKey()).append("=")
                            .append(java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8.name()));
                } catch (Exception e) {
                    urlBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                }
            }
        }
        return urlBuilder.toString();
    }

    private JsonObject createEmptyResponse(String name, double threshold, int limit) {
        JsonObject res = new JsonObject();
        res.addProperty("displayName", name);
        res.addProperty("matchThreshold", threshold);
        res.addProperty("limit", limit);
        res.addProperty("isMatchFound", false);
        return res;
    }
}
