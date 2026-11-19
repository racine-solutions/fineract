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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends email through the Brevo transactional email API (HTTPS), for servers where outgoing SMTP is blocked.
 *
 * <p>
 * Turned on by setting {@code BREVO_API_KEY}. While it is set, every email the platform sends goes through Brevo
 * (notifications, user and password emails, two-factor codes, email campaigns and report emails); without it, the SMTP
 * settings under External Services are used as before. The sender is {@code CONTACT_SENDER_EMAIL} (a sender verified in
 * Brevo), falling back to the SMTP "from" address. The name shown as the sender is the tenant's
 * {@code email-sender-name} configuration when it is switched on and filled in, else {@code CONTACT_SENDER_NAME}.
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
    private ObjectProvider<JdbcTemplate> jdbcTemplates;

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

    @Autowired
    void setJdbcTemplates(final ObjectProvider<JdbcTemplate> jdbcTemplates) {
        this.jdbcTemplates = jdbcTemplates;
    }

    /**
     * The current tenant's own sender name (configuration email-sender-name), or the server's when it is off, empty or
     * there is no tenant (e.g. at startup).
     */
    String senderName() {
        final JdbcTemplate jdbcTemplate = jdbcTemplates == null ? null : jdbcTemplates.getIfAvailable();
        if (jdbcTemplate != null) {
            try {
                final List<Map<String, Object>> setting = jdbcTemplate
                        .queryForList("select enabled, string_value from c_configuration where name = 'email-sender-name'");
                if (!setting.isEmpty() && Boolean.TRUE.equals(setting.get(0).get("enabled"))) {
                    final String name = StringUtils.trimToNull((String) setting.get(0).get("string_value"));
                    if (name != null) {
                        return StringUtils.abbreviate(name, 70);
                    }
                }
            } catch (RuntimeException e) {
                log.debug("No tenant sender name, using the server's: {}", e.getMessage());
            }
        }
        return senderName;
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
        final List<Map<String, String>> recipients = recipients(to, toName);
        if (recipients.isEmpty()) {
            throw new PlatformEmailSendException(new IllegalArgumentException("No recipient address"));
        }
        final Map<String, Object> payload = payload(recipients, subject, body, html, fallbackSenderEmail);
        final List<Map<String, String>> files = attachmentsOf(attachments);
        if (!files.isEmpty()) {
            payload.put("attachment", files);
        }
        final HttpResponse<String> response;
        try {
            response = post(payload);
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

    /**
     * What Brevo answered to one email.
     *
     * @param httpStatus
     *            Brevo's HTTP status, or 0 when Brevo could not be reached
     * @param messageId
     *            Brevo's message id when accepted, used to match its webhook events
     * @param response
     *            Brevo's answer (or the connection error), never containing the API key
     */
    public record Result(int httpStatus, String messageId, String response) {

        public boolean isAccepted() {
            return httpStatus / 100 == 2;
        }
    }

    /**
     * Sends one HTML email to one address and reports what Brevo answered instead of throwing, so a caller sending many
     * emails can tell a bad address from a refused key or a used-up daily allowance.
     */
    public Result sendHtml(final String to, final String toName, final String subject, final String html) {
        final List<Map<String, String>> recipients = recipients(to, toName);
        if (recipients.size() != 1) {
            return new Result(400, null, "Expected one recipient address");
        }
        final Map<String, Object> payload;
        try {
            payload = payload(recipients, subject, html, true, null);
        } catch (PlatformEmailSendException e) {
            return new Result(0, null, e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
        try {
            final HttpResponse<String> response = post(payload);
            String messageId = null;
            if (response.statusCode() / 100 == 2) {
                try {
                    final Map<?, ?> answer = GSON.fromJson(response.body(), Map.class);
                    messageId = answer == null || answer.get("messageId") == null ? null : answer.get("messageId").toString();
                } catch (RuntimeException e) {
                    log.warn("Brevo accepted an email but its answer had no message id: {}", e.getMessage());
                }
            }
            return new Result(response.statusCode(), messageId, response.body());
        } catch (IOException e) {
            return new Result(0, null, "Could not reach the email service: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(0, null, "Interrupted while sending");
        }
    }

    private static List<Map<String, String>> recipients(final String to, final String toName) {
        final List<Map<String, String>> recipients = new ArrayList<>();
        Arrays.stream(StringUtils.defaultString(to).split("[,;]")).map(String::trim).filter(StringUtils::isNotBlank).forEach(address -> {
            final Map<String, String> recipient = new LinkedHashMap<>();
            recipient.put("email", address);
            if (StringUtils.isNotBlank(toName)) {
                recipient.put("name", toName);
            }
            recipients.add(recipient);
        });
        return recipients;
    }

    private Map<String, Object> payload(final List<Map<String, String>> recipients, final String subject, final String body,
            final boolean html, final String fallbackSenderEmail) {
        final String from = StringUtils.defaultIfBlank(senderEmail, StringUtils.trimToNull(fallbackSenderEmail));
        if (from == null) {
            throw new PlatformEmailSendException(new IllegalStateException(
                    "Brevo is enabled but no sender is set: set CONTACT_SENDER_EMAIL to a sender verified in Brevo"));
        }
        final Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("email", from);
        final String name = senderName();
        if (StringUtils.isNotBlank(name)) {
            sender.put("name", name);
        }
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender);
        payload.put("to", recipients);
        payload.put("subject", StringUtils.defaultIfBlank(subject, "(no subject)"));
        payload.put(html ? "htmlContent" : "textContent", StringUtils.defaultIfBlank(body, " "));
        return payload;
    }

    private HttpResponse<String> post(final Map<String, Object> payload) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("api-key", apiKey)
                .header("accept", "application/json").header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
