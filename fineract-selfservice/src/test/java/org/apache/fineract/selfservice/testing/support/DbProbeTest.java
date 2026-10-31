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
package org.apache.fineract.selfservice.testing.support;

import java.sql.*;
import org.junit.jupiter.api.Test;

public class DbProbeTest extends SelfServiceIntegrationTestBase {

    @Test
    public void testProbe() throws Exception {
        System.out.println("=== DB PROBE START ===");
        try (Connection conn = getJdbcConnection()) {
            System.out.println("CONNECTED TO DEFAULT DB! URL: " + conn.getMetaData().getURL());
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT id, name FROM m_office")) {
                System.out.println("Offices:");
                while (rs.next()) {
                    System.out.println(" - ID: " + rs.getInt("id") + ", Name: " + rs.getString("name"));
                }
            }
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT id, username, email, is_deleted, enabled FROM m_appuser")) {
                System.out.println("m_appuser entries:");
                while (rs.next()) {
                    System.out.println(
                            " - ID: " + rs.getLong("id") + ", Username: " + rs.getString("username") + ", Email: " + rs.getString("email")
                                    + ", Deleted: " + rs.getBoolean("is_deleted") + ", Enabled: " + rs.getBoolean("enabled"));
                }
            }
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT id, username, email, is_deleted, enabled FROM m_appselfservice_user")) {
                System.out.println("m_appselfservice_user entries:");
                while (rs.next()) {
                    System.out.println(
                            " - ID: " + rs.getLong("id") + ", Username: " + rs.getString("username") + ", Email: " + rs.getString("email")
                                    + ", Deleted: " + rs.getBoolean("is_deleted") + ", Enabled: " + rs.getBoolean("enabled"));
                }
            }
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM m_cache")) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                System.out.println("m_cache entries:");
                while (rs.next()) {
                    StringBuilder sb = new StringBuilder(" - ");
                    for (int i = 1; i <= cols; i++) {
                        sb.append(meta.getColumnName(i)).append(": ").append(rs.getObject(i)).append(", ");
                    }
                    System.out.println(sb.toString());
                }
            }
        } catch (Exception e) {
            System.err.println("Default DB Error: " + e.getMessage());
            e.printStackTrace();
        }

        // Also let's try connecting to fineract_tenants DB
        String tenantsUrl = System.getProperty("fineract.it.tenantsUrl", "jdbc:mariadb://localhost:3318/fineract_tenants");
        String user = System.getProperty("fineract.it.dbUsername", "root");
        String password = System.getProperty("fineract.it.dbPassword", "mysql");
        try (Connection conn = DriverManager.getConnection(tenantsUrl, user, password)) {
            System.out.println("CONNECTED TO TENANTS DB! URL: " + conn.getMetaData().getURL());
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                System.out.println("Tables in fineract_tenants:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString(1));
                }
            }
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM tenant_server_connections")) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                System.out.println("tenant_server_connections Details:");
                while (rs.next()) {
                    StringBuilder sb = new StringBuilder(" - ");
                    for (int i = 1; i <= cols; i++) {
                        sb.append(meta.getColumnName(i)).append(": ").append(rs.getObject(i)).append(", ");
                    }
                    System.out.println(sb.toString());
                }
            }
        } catch (Exception e) {
            System.err.println("Tenants DB Error: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("=== DB PROBE END ===");
    }
}
