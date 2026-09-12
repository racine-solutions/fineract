# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
FROM azul/zulu-openjdk-alpine:21 AS builder

RUN apk update && apk add wget

COPY . fineract
WORKDIR /fineract


RUN ./gradlew --no-daemon -q  -x compileTestJava -x test bootJar
RUN mv /fineract/fineract-provider/build/libs/*.jar /fineract/fineract-provider/build/libs/fineract-provider.jar


# https://issues.apache.org/jira/browse/LEGAL-462
# https://issues.apache.org/jira/browse/FINERACT-762
# We include an alternative JDBC driver (which is faster, but not allowed to be default in Apache distribution)
# allowing implementations to switch the driver used by changing start-up parameters (for both tenants and each tenant DB)
# The commented out lines in the docker-compose.yml illustrate how to do this.
WORKDIR /app/libs
RUN wget -q https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.23/mysql-connector-java-8.0.23.jar
# =========================================

FROM azul/zulu-openjdk:21 AS fineract

# RUN apk add --no-cache fontconfig
COPY --from=builder /fineract/fineract-report/pentahoReports/*.properties /root/.mifosx/pentahoReports/
COPY --from=builder /fineract/fineract-report/pentahoReports/*.prpt /root/.mifosx/pentahoReports/
COPY --from=builder /fineract/fineract-report/pentahoReports/fonts/*.ttf /usr/local/share/fonts/
COPY --from=builder /fineract/fineract-provider/build/libs/ /app
COPY --from=builder /app/libs /app/libs

ENV TZ="UTC"
ENV FINERACT_HIKARI_DRIVER_SOURCE_CLASS_NAME="com.mysql.cj.jdbc.Driver"
ENV FINERACT_HIKARI_JDBC_URL="jdbc:mysql://localhost:3306/fineract_tenants"
ENV FINERACT_HIKARI_USERNAME="root"
ENV FINERACT_HIKARI_PASSWORD="mysql"
ENV FINERACT_HIKARI_MINIMUM_IDLE="1"
ENV FINERACT_HIKARI_MAXIMUM_POOL_SIZE="20"
ENV FINERACT_HIKARI_IDLE_TIMEOUT="120000"
ENV FINERACT_HIKARI_CONNECTION_TIMEOUT="300000"
ENV FINERACT_HIKARI_TEST_QUERY="SELECT 1"
ENV FINERACT_HIKARI_AUTO_COMMIT="true"
ENV FINERACT_HIKARI_DS_PROPERTIES_CACHE_PREP_STMTS="true"
ENV FINERACT_HIKARI_DS_PROPERTIES_PREP_STMT_CACHE_SIZE="250"
ENV FINERACT_HIKARI_DS_PROPERTIES_PREP_STMT_CACHE_SQL_LIMIT="2048"
ENV FINERACT_HIKARI_DS_PROPERTIES_USE_SERVER_PREP_STMTS="true"
ENV FINERACT_HIKARI_DS_PROPERTIES_USE_LOCAL_SESSION_STATE="true"
ENV FINERACT_HIKARI_DS_PROPERTIES_REWRITE_BATCHED_STATEMENTS="true"
ENV FINERACT_HIKARI_DS_PROPERTIES_CACHE_RESULT_SET_METADATA="true"
ENV FINERACT_HIKARI_DS_PROPERTIES_CACHE_SERVER_CONFIGURATION="true"
ENV FINERACT_HIKARI_DS_PROPERTIES_ELIDE_SET_AUTO_COMMITS="true"
ENV FINERACT_HIKARI_DS_PROPERTIES_MAINTAIN_TIME_STATS="false"
ENV FINERACT_HIKARI_DS_PROPERTIES_LOG_SLOW_QUERIES="true"
ENV FINERACT_HIKARI_DS_PROPERTIES_DUMP_QUERIES_IN_EXCEPTION="true"
ENV FINERACT_DEFAULT_TENANTDB_HOSTNAME="localhost"
ENV FINERACT_DEFAULT_TENANTDB_PORT="3306"
ENV FINERACT_DEFAULT_TENANTDB_UID="root"
ENV FINERACT_DEFAULT_TENANTDB_PWD="mysql"
ENV FINERACT_DEFAULT_TENANTDB_TIMEZONE="Africa/Kampala"
ENV FINERACT_DEFAULT_TENANTDB_IDENTIFIER="showroom"
ENV FINERACT_DEFAULT_TENANTDB_NAME="fineract_showroom"
ENV FINERACT_DEFAULT_TENANTDB_DESCRIPTION="Showroom "
ENV FINERACT_SERVER_SSL_ENABLED="true"
ENV FINERACT_SERVER_PORT="8443"

# Overrides EVERY tenant's per-tenant connection pool (tenant_server_connections.pool_max_active/pool_initial_size
# default to 40/5 each - with ~5 tenants that's up to ~200 possible pooled connections from this one JVM, too many
# for a small box). Setting these here means the per-tenant DB rows don't need to be touched at all.
ENV FINERACT_CONFIG_MAX_POOL_SIZE="10"
ENV FINERACT_CONFIG_MIN_POOL_SIZE="2"
ENV FINERACT_CONFIG_LEAK_DETECTION_THRESHOLD="60000"

# Tuned for a small, memory-constrained host running only this JVM (no headroom to spare for the OS/other
# processes). Override at `docker run`/compose time with -e JAVA_OPTS="..." if the box has more RAM available -
# e.g. on a 4GB box, "-Xms512m -Xmx2560m -XX:MaxMetaspaceSize=384m ..." leaves more room to grow.
ENV JAVA_OPTS="-Xms512m -Xmx1280m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
-XX:InitiatingHeapOccupancyPercent=35 -Xss512k -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/tmp/fineract-oom.hprof"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dloader.path=/app/libs/ -jar /app/fineract-provider.jar"]