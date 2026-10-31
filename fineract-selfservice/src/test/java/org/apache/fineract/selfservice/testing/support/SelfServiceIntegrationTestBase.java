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

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public abstract class SelfServiceIntegrationTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(SelfServiceIntegrationTestBase.class);

    private static final Network network = Network.newNetwork();

    protected static PostgreSQLContainer<?> postgres;
    protected static GenericContainer<?> fineract;

    protected static boolean isSkipContainers() {
        return Boolean.getBoolean("fineract.it.skipcontainers") || "true".equalsIgnoreCase(System.getenv("FINERACT_IT_SKIPCONTAINERS"))
                || Boolean.getBoolean("cargoDisabled");
    }

    private static String cachedTenantId = null;

    public static String getTenantId() {
        return "default";
    }

    static {
        if (!isSkipContainers()) {
            postgres = new PostgreSQLContainer<>("postgres:15-alpine").withNetwork(network).withNetworkAliases("db")
                    .withDatabaseName("fineract_default")
                    // Use 'postgres' superuser to easily create the second DB
                    .withUsername("postgres").withPassword("postgres");
            postgres.start();

            // 2. Pre-Initialize the Master Tenant Database
            try {
                postgres.execInContainer("psql", "-U", "postgres", "-c", "CREATE DATABASE fineract_tenants;");
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize fineract_tenants database in Testcontainers", e);
            }

            // 3. Fineract Container with Strict Env Variables
            //
            // Image selection (first match wins): -Dfineract.it.image, then env FINERACT_IT_IMAGE, else
            // apache/fineract:develop (Docker Hub — tracks Fineract develop, aligned with README and
            // mifosx/docker-compose.yml). Use FINERACT_IT_IMAGE (-Dfineract.it.image) to pin a digest or a
            // locally built image that matches pom fineract.version exactly.
            final String fromEnv = System.getenv("FINERACT_IT_IMAGE");
            final String fromProperty = System.getProperty("fineract.it.image");
            final String defaultImage = "apache/fineract:develop";
            final String fineractImage = fromProperty != null && !fromProperty.isBlank() ? fromProperty
                    : (fromEnv != null && !fromEnv.isBlank() ? fromEnv : defaultImage);
            final DockerImageName dockerImageName = DockerImageName.parse(fineractImage).asCompatibleSubstituteFor("apache/fineract");

            fineract = new GenericContainer<>(dockerImageName).withNetwork(network).withExposedPorts(8443)

                    // Hikari Master Configuration (fineract_tenants)
                    .withEnv("FINERACT_HIKARI_JDBC_URL", "jdbc:postgresql://db:5432/fineract_tenants")
                    .withEnv("FINERACT_HIKARI_USERNAME", "postgres").withEnv("FINERACT_HIKARI_PASSWORD", "postgres")
                    .withEnv("FINERACT_HIKARI_DRIVER_SOURCE_CLASS_NAME", "org.postgresql.Driver")

                    // Default Tenant Database Properties (fineract_default)
                    .withEnv("FINERACT_DEFAULT_TENANTDB_HOSTNAME", "db").withEnv("FINERACT_DEFAULT_TENANTDB_PORT", "5432")
                    .withEnv("FINERACT_DEFAULT_TENANTDB_UID", "postgres").withEnv("FINERACT_DEFAULT_TENANTDB_PWD", "postgres")
                    .withEnv("FINERACT_DEFAULT_TENANTDB_CONN_PARAMS", "")

                    // Enable the self-service module (matches Fineract docker-compose.override.yml)
                    .withEnv("FINERACT_MODULE_SELFSERVICE_ENABLED", "true").withEnv("SPRING_MAIN_ALLOW_BEAN_DEFINITION_OVERRIDING", "true")
                    .withEnv("FINERACT_MODULES_SELFSERVICE_RUNREPORTS_ALLOWLIST", "Client Details")

                    // Timezone (Optional but highly recommended to prevent sync errors)
                    .withEnv("TZ", "UTC").withEnv("JAVA_TOOL_OPTIONS", "-Xmx2G")

                    // Keep SSL enabled (default for Fineract container images); tests use relaxed HTTPS
                    // validation.
                    .withEnv("FINERACT_SERVER_SSL_ENABLED", "true").withEnv("FINERACT_SERVER_PORT", "8443")

                    .withCopyFileToContainer(
                            MountableFile.forHostPath("fineract-selfservice/build/libs/fineract-selfservice-1.11.1-SNAPSHOT.jar"),
                            "/app/plugins/selfservice-plugin.jar")
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("fineract-provider/src/main/resources/db/changelog/db.changelog-master.xml"),
                            "/app/resources/db/changelog/db.changelog-master.xml")

                    // Prepend the plugin JAR to the JIB container's classpath.
                    .withCreateContainerCmdModifier(cmd -> {
                        cmd.withEntrypoint("sh", "-c", "CLASSPATH=$(cat /app/jib-classpath-file) && " + "exec java $JAVA_TOOL_OPTIONS "
                                + "-Duser.home=/tmp -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom "
                                + "-cp /app/plugins/selfservice-plugin.jar:$CLASSPATH " + "org.apache.fineract.ServerApplication");
                        cmd.withCmd();
                    })

                    // Stream container stdout/stderr to test output for diagnostic visibility.
                    // This reveals the actual failure reason instead of generic ContainerLaunchException.
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("fineract"))

                    // HostPortWaitStrategy still runs an internal port check that requires bash (missing in
                    // apache/fineract images). HTTPS + allowInsecure waits until the app serves actuator.
                    .waitingFor(Wait.forHttps("/fineract-provider/actuator/health").allowInsecure().forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(7)));

            fineract.start();
        } else {
            LOG.info("SkipContainers is true. External Fineract and Database will be used.");
        }
    }

    protected static int getFineractPort() {
        if (isSkipContainers()) {
            return Integer.getInteger("fineract.it.port", 8443);
        }
        return fineract.getMappedPort(8443);
    }

    protected static java.sql.Connection getJdbcConnection() throws java.sql.SQLException {
        String url;
        String user;
        String password;
        if (isSkipContainers()) {
            url = System.getProperty("fineract.it.jdbcUrl", "jdbc:mariadb://localhost:3318/fineract_default");
            user = System.getProperty("fineract.it.dbUsername", "root");
            password = System.getProperty("fineract.it.dbPassword", "mysql");
        } else {
            url = postgres.getJdbcUrl();
            user = postgres.getUsername();
            password = postgres.getPassword();
        }
        return java.sql.DriverManager.getConnection(url, user, password);
    }

    /**
     * Executes a SQL statement inside the test database.
     */
    protected static void executeSqlInPostgres(String sql) {
        try (java.sql.Connection connection = getJdbcConnection(); java.sql.Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to execute SQL: " + sql, e);
        }
    }

    /**
     * Executes a SQL statement inside the test database after safely rendering parameters as SQL literals for test-only
     * usage.
     */
    protected static void executeSqlInPostgres(String sqlTemplate, Object... parameters) {
        String[] rendered = new String[parameters.length];
        for (int index = 0; index < parameters.length; index++) {
            rendered[index] = sqlLiteral(parameters[index]);
        }
        executeSqlInPostgres(sqlTemplate.formatted((Object[]) rendered));
    }

    /** Queries a single scalar value from the test database. */
    protected static String querySingleValueInPostgres(String sql) {
        try (java.sql.Connection connection = getJdbcConnection();
                java.sql.Statement statement = connection.createStatement();
                java.sql.ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                String value = resultSet.getString(1);
                return value != null ? value.trim() : "";
            }
            return "";
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to query test database: " + sql, e);
        }
    }

    protected static String querySingleValue(String sql, String parameter) {
        try (java.sql.Connection connection = getJdbcConnection();
                java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String value = resultSet.getString(1);
                    return value != null ? value.trim() : "";
                }
                return "";
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Test database query failed: " + sql, e);
        }
    }

    /**
     * Wraps a Java string value as a SQL literal, escaping single quotes. Returns the string {@code
     * NULL} for null input.
     */
    protected static String sqlLiteral(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("'", "''") + "'";
    }

    /** Wraps a Java value as a SQL literal for test statements executed via JDBC. */
    protected static String sqlLiteral(Object value) {
        return sqlLiteral(value == null ? null : String.valueOf(value));
    }
}
