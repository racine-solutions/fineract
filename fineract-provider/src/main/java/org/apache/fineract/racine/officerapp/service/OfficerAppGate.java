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
package org.apache.fineract.racine.officerapp.service;

import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CAPTURED_HEADER;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_ACCESS_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_DEVICE_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_DISABLED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_SESSION_EXPIRED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_UPDATE_REQUIRED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.DEVICE_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.END_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.END_IDLE;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.END_LOGOUT;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.END_SWITCHED_OFF;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_ACCESS_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_DEVICE_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_DISABLED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_SESSION_EXPIRED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_SIGNED_OUT;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_UPDATE_REQUIRED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.SESSION_HEADER;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.TOUCH_SECONDS;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.SynchronousCommandProcessingService;
import org.apache.fineract.infrastructure.core.service.RequestChannel;
import org.apache.fineract.racine.officerapp.domain.OfficerAppEvent;
import org.apache.fineract.racine.officerapp.domain.OfficerAppVersion;
import org.apache.fineract.racine.officerapp.service.OfficerAppSessionService.Access;
import org.apache.fineract.racine.officerapp.service.OfficerAppSessionService.SessionRow;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Checks every request made through the field officer app (one that carries {@code X-Racine-App-Session}): app switched
 * on, session valid and not idle, officer's app access on, device not blocked, app version allowed. Requests without
 * the header (the web-app, integrations) are only tagged with their channel. After an app request it records what the
 * officer did.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficerAppGate {

    static final String SESSION_ATTRIBUTE = "RACINE_OFFICERAPP_SESSION";
    private static final List<String> AMOUNT_KEYS = List.of("transactionAmount", "amount", "principal", "approvedLoanAmount",
            "depositAmount", "withdrawalAmount");
    private static final Map<String, String> VERBS = Map.ofEntries(Map.entry("CREATE", "Created"), Map.entry("UPDATE", "Updated"),
            Map.entry("DELETE", "Deleted"), Map.entry("APPROVE", "Approved"), Map.entry("REJECT", "Rejected"),
            Map.entry("DISBURSE", "Disbursed"), Map.entry("REPAYMENT", "Repayment on"), Map.entry("ACTIVATE", "Activated"),
            Map.entry("CLOSE", "Closed"), Map.entry("DEPOSIT", "Deposit to"), Map.entry("WITHDRAWAL", "Withdrawal from"),
            Map.entry("WITHDRAW", "Withdrew"), Map.entry("ADJUST", "Adjusted"), Map.entry("UNDOAPPROVAL", "Undid approval of"),
            Map.entry("WRITEOFF", "Wrote off"), Map.entry("ASSIGNSTAFF", "Assigned officer to"), Map.entry("SAVE", "Saved"));

    /** What to send back instead of running the request. */
    public record Refusal(int status, String body) {
    }

    private final OfficerAppSettings settings;
    private final OfficerAppSessionService sessions;
    private final OfficerAppEventRecorder recorder;
    private final JdbcTemplate jdbcTemplate;

    /** Runs before the request. Returns a refusal, or null to let the request run. */
    public Refusal check(final HttpServletRequest request, final HttpServletResponse response) {
        final String path = StringUtils.defaultString(request.getRequestURI());
        final String token = StringUtils.trimToNull(request.getHeader(SESSION_HEADER));
        if (token == null) {
            request.setAttribute(RequestChannel.CHANNEL_ATTRIBUTE, path.contains("/self/") ? RequestChannel.SELF_SERVICE
                    : OfficerAppRequests.isBrowser(request) ? RequestChannel.WEB : RequestChannel.API);
            return checkWithoutSession(request, path);
        }
        request.setAttribute(RequestChannel.CHANNEL_ATTRIBUTE, RequestChannel.OFFICER_APP);
        if (path.endsWith("/officerapp/authentication")) {
            return null;
        }
        if (!settings.isOn()) {
            final int ended = sessions.endAllSessions(END_SWITCHED_OFF);
            if (ended > 0) {
                log.warn("Field officer app is switched off: ended {} app sessions", ended);
            }
            return refusal(503, CODE_DISABLED, MSG_DISABLED, null);
        }
        final AppUser user = currentUser();
        if (user == null) {
            // Not signed in: the normal security rules refuse the request
            return null;
        }
        final boolean logout = path.endsWith("/officerapp/logout");
        final SessionRow session = sessions.findSession(token);
        if (session == null || !session.appUserId().equals(user.getId())) {
            return logout ? null : refusal(401, CODE_SESSION_EXPIRED, MSG_SESSION_EXPIRED, Map.of("reason", "UNKNOWN"));
        }
        request.setAttribute(RequestChannel.OFFICER_APP_DEVICE_ATTRIBUTE, session.deviceId());
        if (logout) {
            return null;
        }
        // Access and device first: a session ended because access was turned off must show "access is off", not "signed
        // out"
        final Access access = sessions.access(user.getId());
        if (!access.allowed()) {
            sessions.endSessionsOfUser(user.getId(), END_BLOCKED);
            return refusal(403, CODE_ACCESS_BLOCKED, MSG_ACCESS_BLOCKED, reason(access.reason()));
        }
        if (DEVICE_BLOCKED.equals(session.deviceStatus())) {
            sessions.endSessionsOfDevice(session.deviceId(), END_BLOCKED);
            return refusal(403, CODE_DEVICE_BLOCKED, MSG_DEVICE_BLOCKED, reason(session.deviceBlockReason()));
        }
        if (session.endedOn() != null) {
            final String why = StringUtils.defaultIfBlank(session.endReason(), END_IDLE);
            final boolean byAdmin = !END_IDLE.equals(why) && !END_LOGOUT.equals(why);
            return refusal(401, CODE_SESSION_EXPIRED, byAdmin ? MSG_SIGNED_OUT : MSG_SESSION_EXPIRED, Map.of("reason", why));
        }
        final LocalDateTime now = OfficerAppSessionService.nowUtc();
        final int timeout = settings.sessionTimeoutMinutes();
        if (session.lastActivityOn().plusMinutes(timeout).isBefore(now)) {
            if (sessions.endSession(session.id(), END_IDLE) > 0) {
                recorder.record(event(user, session, request).eventType("SESSION_TIMEOUT").category(OfficerAppEvent.CATEGORY_SESSION)
                        .result(OfficerAppEvent.RESULT_SUCCESS).actor(OfficerAppEvent.ACTOR_SYSTEM)
                        .description("Session ended after " + timeout + " minutes without activity").build());
            }
            return refusal(401, CODE_SESSION_EXPIRED, MSG_SESSION_EXPIRED, Map.of("reason", END_IDLE));
        }
        final String headerVersion = OfficerAppRequests.appVersion(request);
        final String minimum = settings.minimumVersion();
        if (OfficerAppVersion.isOlder(headerVersion != null ? headerVersion : session.appVersion(), minimum)) {
            return updateRequired(minimum);
        }
        if (!session.lastActivityOn().plusSeconds(TOUCH_SECONDS).isAfter(now)) {
            sessions.touch(session, OfficerAppRequests.clientIp(request), headerVersion);
        }
        request.setAttribute(SESSION_ATTRIBUTE, session);
        response.setHeader(org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.TIMEOUT_HEADER, String.valueOf(timeout));
        return null;
    }

    /**
     * Old app versions send no session. Once every officer has the new app, admins switch on
     * officer-app-require-session so phone apps can't skip the checks above. Browsers and the self-service app are
     * never affected.
     */
    private Refusal checkWithoutSession(final HttpServletRequest request, final String path) {
        if (!OfficerAppRequests.isPhoneAppClient(request) || path.contains("/self/") || path.endsWith("/officerapp/authentication")) {
            return null;
        }
        final String authorization = StringUtils.defaultString(request.getHeader("Authorization"));
        final boolean signingIn = path.matches(".*/api/v\\d+/authentication/?$");
        if (!authorization.startsWith("Basic ") && !signingIn) {
            return null;
        }
        return settings.requireSession() ? updateRequired(settings.minimumVersion()) : null;
    }

    /** Runs after an app request: records what the officer did. Never fails the request. */
    public void afterRequest(final HttpServletRequest request, final HttpServletResponse response) {
        if (!(request.getAttribute(SESSION_ATTRIBUTE) instanceof SessionRow session)) {
            return;
        }
        try {
            final AppUser user = currentUser();
            if (user == null) {
                return;
            }
            final LocalDateTime captured = capturedOn(request);
            final String category = captured != null ? OfficerAppEvent.CATEGORY_SYNC : OfficerAppEvent.CATEGORY_ACTION;
            final Object commandId = request.getAttribute(SynchronousCommandProcessingService.COMMAND_SOURCE_ID);
            final String method = request.getMethod();
            final String path = StringUtils.defaultString(request.getRequestURI());
            if (commandId instanceof Number number) {
                recordCommand(number.longValue(), user, session, request, category, captured);
            } else if ("GET".equalsIgnoreCase(method)) {
                if (path.contains("/runreports/")) {
                    final String name = URLDecoder.decode(StringUtils.substringAfterLast(path, "/runreports/"), StandardCharsets.UTF_8);
                    recorder.record(event(user, session, request).eventType("REPORT_RUN").category(OfficerAppEvent.CATEGORY_REPORT)
                            .result(response.getStatus() / 100 == 2 ? OfficerAppEvent.RESULT_SUCCESS : OfficerAppEvent.RESULT_FAILED)
                            .description("Ran report " + name).build());
                }
            } else if (!path.contains("/officerapp/")) {
                recorder.record(event(user, session, request).eventType("REQUEST").category(category)
                        .result(response.getStatus() / 100 == 2 ? OfficerAppEvent.RESULT_SUCCESS : OfficerAppEvent.RESULT_FAILED)
                        .description(method + " " + StringUtils.defaultIfBlank(StringUtils.substringAfter(path, "/api/v1"), path))
                        .capturedOnUtc(captured).build());
            }
        } catch (RuntimeException e) {
            log.error("Could not record officer app activity for {} {}", request.getMethod(), request.getRequestURI(), e);
        }
    }

    private void recordCommand(final Long commandId, final AppUser user, final SessionRow session, final HttpServletRequest request,
            final String category, final LocalDateTime captured) {
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList("select c.action_name, c.entity_name, c.resource_id,"
                + " c.client_id, c.loan_id, c.savings_account_id, c.status, c.command_as_json, cl.display_name as client_name,"
                + " l.account_no as loan_no, s.account_no as savings_no, l.currency_code as loan_currency,"
                + " s.currency_code as savings_currency from m_portfolio_command_source c left join m_client cl on cl.id = c.client_id"
                + " left join m_loan l on l.id = c.loan_id left join m_savings_account s on s.id = c.savings_account_id where c.id = ?",
                commandId);
        if (rows.isEmpty()) {
            return;
        }
        final Map<String, Object> row = rows.get(0);
        final String action = (String) row.get("action_name");
        final String entity = (String) row.get("entity_name");
        final int status = row.get("status") instanceof Number n ? n.intValue() : 0;
        final String result = switch (status) {
            case 1, 4 -> OfficerAppEvent.RESULT_SUCCESS;
            case 2 -> OfficerAppEvent.RESULT_PENDING;
            default -> OfficerAppEvent.RESULT_FAILED;
        };
        String description = describe(action, entity, toLong(row.get("resource_id")), (String) row.get("client_name"),
                (String) row.get("loan_no"), (String) row.get("savings_no"));
        if (captured != null) {
            description = "Offline: " + description;
        }
        if (status == 2) {
            description = description + " (waiting for approval)";
        }
        final String currency = row.get("loan_currency") != null ? (String) row.get("loan_currency") : (String) row.get("savings_currency");
        recorder.record(event(user, session, request).eventType(category.equals(OfficerAppEvent.CATEGORY_SYNC) ? "SYNC" : "COMMAND")
                .category(category).result(result).commandId(commandId).entityName(entity).actionName(action)
                .resourceId(toLong(row.get("resource_id"))).clientId(toLong(row.get("client_id"))).loanId(toLong(row.get("loan_id")))
                .savingsId(toLong(row.get("savings_account_id"))).amount(amountOf((String) row.get("command_as_json")))
                .currencyCode(currency).description(description).capturedOnUtc(captured).build());
    }

    static String describe(final String action, final String entity, final Long resourceId, final String clientName, final String loanNo,
            final String savingsNo) {
        final String a = StringUtils.defaultString(action).toUpperCase(Locale.ROOT);
        final String e = StringUtils.defaultString(entity).toUpperCase(Locale.ROOT);
        final String verb = VERBS.getOrDefault(a, StringUtils.capitalize(a.toLowerCase(Locale.ROOT).replace('_', ' ')));
        final StringBuilder text = new StringBuilder(verb).append(' ').append(e.toLowerCase(Locale.ROOT).replace('_', ' '));
        if (loanNo != null && e.contains("LOAN")) {
            text.append(' ').append(loanNo);
        } else if (savingsNo != null && e.contains("SAVING")) {
            text.append(' ').append(savingsNo);
        } else if (resourceId != null) {
            text.append(" #").append(resourceId);
        }
        if (StringUtils.isNotBlank(clientName)) {
            text.append(" · ").append(clientName);
        }
        return text.toString();
    }

    static BigDecimal amountOf(final String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            final JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            for (final String key : AMOUNT_KEYS) {
                final JsonElement value = object.get(key);
                if (value != null && value.isJsonPrimitive()) {
                    final String cleaned = value.getAsString().replace(",", "").trim();
                    if (!cleaned.isEmpty()) {
                        return new BigDecimal(cleaned);
                    }
                }
            }
        } catch (RuntimeException e) {
            // amounts typed with a locale format we can't read are left out
        }
        return null;
    }

    private OfficerAppEvent.OfficerAppEventBuilder event(final AppUser user, final SessionRow session, final HttpServletRequest request) {
        return OfficerAppEvent.builder().appUserId(user.getId()).username(user.getUsername())
                .officeId(user.getOffice() == null ? null : user.getOffice().getId()).deviceId(session.deviceId())
                .deviceLabel(session.deviceLabel()).actor(OfficerAppEvent.ACTOR_OFFICER).ipAddress(OfficerAppRequests.clientIp(request));
    }

    private Refusal updateRequired(final String minimum) {
        final Map<String, Object> extra = new HashMap<>();
        extra.put("minimumVersion", minimum);
        extra.put("updateUrl", settings.updateUrl());
        return refusal(426, CODE_UPDATE_REQUIRED, MSG_UPDATE_REQUIRED, extra);
    }

    private static Refusal refusal(final int status, final String code, final String message, final Map<String, Object> extra) {
        return new Refusal(status, org.apache.fineract.racine.officerapp.domain.OfficerAppErrors.json(status, code, message, extra));
    }

    private static Map<String, Object> reason(final String reason) {
        final Map<String, Object> extra = new HashMap<>();
        extra.put("reason", reason);
        return extra;
    }

    private static LocalDateTime capturedOn(final HttpServletRequest request) {
        final String header = StringUtils.trimToNull(request.getHeader(CAPTURED_HEADER));
        if (header == null) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(header), ZoneOffset.UTC);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Long toLong(final Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static AppUser currentUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AppUser user ? user : null;
    }
}
