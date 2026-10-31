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
package org.apache.fineract.infrastructure.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrevoEmailClientTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private int status = 201;
    private String url;

    @TempDir
    File tempDir;

    @BeforeEach
    void startFakeBrevo() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            final byte[] answer = (status == 201 ? "{\"messageId\":\"<1@brevo>\"}"
                    : "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void withoutKey_brevoIsOff() {
        assertThat(new BrevoEmailClient("", "sender@example.com", "", url).isEnabled()).isFalse();
        assertThat(new BrevoEmailClient("  ", "sender@example.com", "", url).isEnabled()).isFalse();
    }

    @Test
    void sendsHtmlEmailWithSenderRecipientsAndAttachment() throws Exception {
        File report = new File(tempDir, "report.csv");
        Files.writeString(report.toPath(), "a,b");
        BrevoEmailClient client = new BrevoEmailClient("test-key", "sender@example.com", "Racine", url);

        client.send("one@example.com; two@example.com", null, "Hello", "<p>Hi</p>", true, List.of(report), null);

        assertThat(apiKeyHeader.get()).isEqualTo("test-key");
        JsonObject json = JsonParser.parseString(body.get()).getAsJsonObject();
        assertThat(json.getAsJsonObject("sender").get("email").getAsString()).isEqualTo("sender@example.com");
        assertThat(json.getAsJsonObject("sender").get("name").getAsString()).isEqualTo("Racine");
        assertThat(json.getAsJsonArray("to")).hasSize(2);
        assertThat(json.get("htmlContent").getAsString()).isEqualTo("<p>Hi</p>");
        assertThat(json.has("textContent")).isFalse();
        assertThat(json.getAsJsonArray("attachment").get(0).getAsJsonObject().get("name").getAsString()).isEqualTo("report.csv");
    }

    @Test
    void plainTextEmail_usesFallbackSenderWhenNoneConfigured() {
        BrevoEmailClient client = new BrevoEmailClient("test-key", "", "", url);

        client.send("to@example.com", "To", "Subject", "Body", false, null, "smtp-from@example.com");

        JsonObject json = JsonParser.parseString(body.get()).getAsJsonObject();
        assertThat(json.getAsJsonObject("sender").get("email").getAsString()).isEqualTo("smtp-from@example.com");
        assertThat(json.get("textContent").getAsString()).isEqualTo("Body");
        assertThat(json.getAsJsonArray("to").get(0).getAsJsonObject().get("name").getAsString()).isEqualTo("To");
    }

    @Test
    void refusedByBrevo_throwsWithTheReasonButNotTheKey() {
        status = 401;
        BrevoEmailClient client = new BrevoEmailClient("secret-key", "sender@example.com", "", url);

        assertThatThrownBy(() -> client.send("to@example.com", null, "S", "B", false, null, null))
                .isInstanceOf(PlatformEmailSendException.class).hasStackTraceContaining("HTTP 401").hasStackTraceContaining("Key not found")
                .satisfies(e -> assertThat(e.getCause().getMessage()).doesNotContain("secret-key"));
    }

    @Test
    void noSenderAnywhere_isRefusedBeforeCallingBrevo() {
        BrevoEmailClient client = new BrevoEmailClient("test-key", "", "", url);

        assertThatThrownBy(() -> client.send("to@example.com", null, "S", "B", false, null, null))
                .isInstanceOf(PlatformEmailSendException.class);
        assertThat(body.get()).isNull();
    }
}
