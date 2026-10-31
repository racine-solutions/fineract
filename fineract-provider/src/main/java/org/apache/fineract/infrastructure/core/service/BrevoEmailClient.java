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

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sends email through the Brevo transactional email API (HTTPS), for servers where outgoing SMTP is blocked.
 *
 * <p>
 * Turned on by setting {@code BREVO_API_KEY}. While it is set, every email the platform sends goes through Brevo
 * (notifications, user and password emails, two-factor codes, email campaigns and report emails); without it, the SMTP
 * settings under External Services are used as before. The sender is {@code CONTACT_SENDER_EMAIL} (a sender verified in
 * Brevo), falling back to the SMTP "from" address.
 */
@Slf4j
@Component
public class BrevoEmailClient {

    private static final Gson GSON = new Gson();

    private final String apiKey;
    private final String senderEmail;
    private final String senderName;
    private final String url;
    private final HttpClient httpClient;

    public BrevoEmailClient(@Value("${fineract.email.brevo.api-key:}") final String apiKey,
            @Value("${fineract.email.brevo.sender-email:}") final String senderEmail,
            @Value("${fineract.email.brevo.sender-name:}") final String senderName,
            @Value("${fineract.email.brevo.url:https://api.brevo.com/v3/smtp/email}") final String url) {
        this.apiKey = StringUtils.trimToEmpty(apiKey);
        this.senderEmail = StringUtils.trimToEmpty(senderEmail);
        this.senderName = StringUtils.trimToEmpty(senderName);
        this.url = url;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        if (isEnabled()) {
            log.info("Email is sent through the Brevo API (sender {})", StringUtils.defaultIfBlank(this.senderEmail, "from SMTP settings"));
        }
    }

    /** True when a Brevo API key is configured; then Brevo replaces SMTP for every email. */
    public boolean isEnabled() {
        return StringUtils.isNotBlank(apiKey);
    }

    /**
     * Sends one email.
     *
     * @param to
     *            one address, or several separated by commas or semicolons
     * @param toName
     *            recipient name, may be null
     * @param html
     *            true when {@code body} is HTML, false for plain text
     * @param attachments
     *            files to attach, may be null
     * @param fallbackSenderEmail
     *            sender to use when {@code CONTACT_SENDER_EMAIL} is not set; callers may pass null
     */
    public void send(final String to, final String toName, final String subject, final String body, final boolean html,
            final List<File> attachments, final String fallbackSenderEmail) {
        final String from = StringUtils.defaultIfBlank(senderEmail, StringUtils.trimToNull(fallbackSenderEmail));
        if (from == null) {
            throw new PlatformEmailSendException(new IllegalStateException(
                    "Brevo is enabled but no sender is set: set CONTACT_SENDER_EMAIL to a sender verified in Brevo"));
        }
        final List<Map<String, String>> recipients = new ArrayList<>();
        Arrays.stream(StringUtils.defaultString(to).split("[,;]")).map(String::trim).filter(StringUtils::isNotBlank).forEach(address -> {
            final Map<String, String> recipient = new LinkedHashMap<>();
            recipient.put("email", address);
            if (StringUtils.isNotBlank(toName)) {
                recipient.put("name", toName);
            }
            recipients.add(recipient);
        });
        if (recipients.isEmpty()) {
            throw new PlatformEmailSendException(new IllegalArgumentException("No recipient address"));
        }

        final Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("email", from);
        if (StringUtils.isNotBlank(senderName)) {
            sender.put("name", senderName);
        }
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender);
        payload.put("to", recipients);
        payload.put("subject", StringUtils.defaultIfBlank(subject, "(no subject)"));
        payload.put(html ? "htmlContent" : "textContent", StringUtils.defaultIfBlank(body, " "));
        final List<Map<String, String>> files = attachmentsOf(attachments);
        if (!files.isEmpty()) {
            payload.put("attachment", files);
        }

        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("api-key", apiKey)
                .header("accept", "application/json").header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8)).build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new PlatformEmailSendException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PlatformEmailSendException(e);
        }
        if (response.statusCode() / 100 != 2) {
            // Brevo answers e.g. 401 {"code":"unauthorized","message":"Key not found"}; the API key is never logged
            throw new PlatformEmailSendException(
                    new IllegalStateException("Brevo refused the email: HTTP " + response.statusCode() + " " + response.body()));
        }
        log.info("Email sent through Brevo to {} recipient(s)", recipients.size());
    }

    private static List<Map<String, String>> attachmentsOf(final List<File> attachments) {
        final List<Map<String, String>> files = new ArrayList<>();
        if (attachments == null) {
            return files;
        }
        for (final File file : attachments) {
            if (file == null) {
                continue;
            }
            try {
                files.add(Map.of("name", file.getName(), "content", Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()))));
            } catch (IOException e) {
                throw new PlatformEmailSendException(e);
            }
        }
        return files;
    }
}
