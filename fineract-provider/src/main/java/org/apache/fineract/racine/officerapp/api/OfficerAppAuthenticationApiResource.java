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
package org.apache.fineract.racine.officerapp.api;

import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_ACCESS_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_DEVICE_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_DISABLED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_SESSION_EXPIRED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.CODE_UPDATE_REQUIRED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.DEVICE_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.END_LOGOUT;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_ACCESS_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_DEVICE_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_DISABLED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_SESSION_EXPIRED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.MSG_UPDATE_REQUIRED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.SESSION_HEADER;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.api.AuthenticationApiResource;
import org.apache.fineract.infrastructure.security.exception.PasswordResetRequiredException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.officerapp.domain.DeviceInfo;
import org.apache.fineract.racine.officerapp.domain.OfficerAppErrors;
import org.apache.fineract.racine.officerapp.domain.OfficerAppEvent;
import org.apache.fineract.racine.officerapp.domain.OfficerAppVersion;
import org.apache.fineract.racine.officerapp.service.OfficerAppEventRecorder;
import org.apache.fineract.racine.officerapp.service.OfficerAppRequests;
import org.apache.fineract.racine.officerapp.service.OfficerAppSessionService;
import org.apache.fineract.racine.officerapp.service.OfficerAppSessionService.Access;
import org.apache.fineract.racine.officerapp.service.OfficerAppSessionService.DeviceRow;
import org.apache.fineract.racine.officerapp.service.OfficerAppSessionService.SessionRow;
import org.apache.fineract.racine.officerapp.service.OfficerAppSettings;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Sign-in, sign-out and session check for the field officer Android app. Sign-in checks the staff credentials exactly
 * like POST /authentication, then checks the user's app access and the device, registers the device and starts an app
 * session. The app sends the session token in {@code X-Racine-App-Session} with every later request.
 */
@Component
@ConditionalOnProperty("fineract.security.basicauth.enabled")
@Path("/v1/officerapp")
@Tag(name = "Mobile Officer App", description = "Sign-in and session of the field officer app")
@RequiredArgsConstructor
public class OfficerAppAuthenticationApiResource {

    private static final Pattern INSTALLATION_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final Gson GSON = new Gson();

    private final AuthenticationApiResource authenticationApiResource;
    private final OfficerAppSettings settings;
    private final OfficerAppSessionService sessions;
    private final OfficerAppEventRecorder recorder;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;

    @POST
    @Path("authentication")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Sign in to the field officer app", description = "Body: {\"username\", \"password\", \"device\": "
            + "{\"installationId\", \"manufacturer\", \"model\", \"osVersion\", \"appVersion\", \"appBuild\"}}. Returns the same body as "
            + "POST /authentication plus appSessionToken, sessionTimeoutMinutes, deviceId and deviceCode. Refusals: 403 "
            + "error.msg.officerapp.access.blocked / device.blocked (with reason), 426 error.msg.officerapp.update.required, 503 "
            + "error.msg.officerapp.disabled.")
    public Response authenticate(@Parameter(hidden = true) final String body, @Context final HttpServletRequest request) {
        final JsonObject json = parse(body);
        final String username = text(json, "username");
        final String password = text(json, "password");
        final DeviceInfo device = deviceOf(json);
        final String ip = OfficerAppRequests.clientIp(request);

        if (!settings.isOn()) {
            return refuse(503, CODE_DISABLED, MSG_DISABLED, Map.of());
        }
        final String minimum = settings.minimumVersion();
        if (OfficerAppVersion.isOlder(device.appVersion(), minimum)) {
            recordLogin(lookupUser(username), username, null, device.label(), "LOGIN_REFUSED", OfficerAppEvent.RESULT_REFUSED,
                    "Sign-in refused: app version " + StringUtils.defaultIfBlank(device.appVersion(), "unknown") + " is too old", ip);
            return updateRequired(minimum);
        }

        final JsonObject authenticated;
        try {
            final Map<String, String> credentials = new HashMap<>();
            credentials.put("username", username);
            credentials.put("password", password);
            authenticated = JsonParser.parseString(authenticationApiResource.authenticate(GSON.toJson(credentials))).getAsJsonObject();
        } catch (AuthenticationException e) {
            recordLogin(lookupUser(username), username, null, device.label(), "LOGIN_FAILED",
                    e instanceof PasswordResetRequiredException ? OfficerAppEvent.RESULT_REFUSED : OfficerAppEvent.RESULT_FAILED,
                    "Sign-in failed: " + whyFailed(e), ip);
            throw e;
        }

        final Long userId = authenticated.get("userId").getAsLong();
        final Access access = sessions.access(userId);
        if (!access.allowed()) {
            recordLogin(userId, username, null, device.label(), "LOGIN_REFUSED", OfficerAppEvent.RESULT_REFUSED,
                    "Sign-in refused: mobile app access is off", ip);
            return refuse(403, CODE_ACCESS_BLOCKED, MSG_ACCESS_BLOCKED, reasonOf(access.reason()));
        }
        final DeviceRow registered = sessions.registerDevice(userId, device, ip);
        if (DEVICE_BLOCKED.equals(registered.status())) {
            recordLogin(userId, username, registered.id(), registered.label(), "LOGIN_REFUSED", OfficerAppEvent.RESULT_REFUSED,
                    "Sign-in refused: device " + registered.shortCode() + " is blocked", ip);
            return refuse(403, CODE_DEVICE_BLOCKED, MSG_DEVICE_BLOCKED, reasonOf(registered.blockReason()));
        }

        final String token = sessions.startSession(registered.id(), userId);
        recordLogin(userId, username, registered.id(), registered.label(), "LOGIN", OfficerAppEvent.RESULT_SUCCESS,
                "Signed in on " + registered.label() + " (app " + StringUtils.defaultIfBlank(device.appVersion(), "unknown") + ", Android "
                        + StringUtils.defaultIfBlank(device.osVersion(), "unknown") + ")",
                ip);

        authenticated.addProperty("appSessionToken", token);
        authenticated.addProperty("sessionTimeoutMinutes", settings.sessionTimeoutMinutes());
        authenticated.addProperty("deviceId", registered.id());
        authenticated.addProperty("deviceCode", registered.shortCode());
        return Response.ok(authenticated.toString(), MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("logout")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Sign out of the field officer app", description = "Ends the app session sent in X-Racine-App-Session.")
    public Response logout(@HeaderParam(SESSION_HEADER) @Parameter(hidden = true) final String token,
            @Context final HttpServletRequest request) {
        final AppUser user = context.authenticatedUser();
        final SessionRow session = sessions.findSession(token);
        boolean ended = false;
        if (session != null && session.appUserId().equals(user.getId()) && session.endedOn() == null) {
            ended = sessions.endSession(session.id(), END_LOGOUT) > 0;
            recorder.record(OfficerAppEvent.builder().appUserId(user.getId()).username(user.getUsername()).deviceId(session.deviceId())
                    .deviceLabel(session.deviceLabel()).eventType("LOGOUT").category(OfficerAppEvent.CATEGORY_SESSION)
                    .result(OfficerAppEvent.RESULT_SUCCESS).actor(OfficerAppEvent.ACTOR_OFFICER).description("Signed out")
                    .ipAddress(OfficerAppRequests.clientIp(request)).build());
        }
        return Response.ok("{\"signedOut\":" + ended + "}", MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("session")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Check the field officer app session", description = "Called when the app comes back to the foreground. "
            + "Refusals are the same as for any app request.")
    public Response session(@HeaderParam(SESSION_HEADER) @Parameter(hidden = true) final String token) {
        final AppUser user = context.authenticatedUser();
        final SessionRow session = sessions.findSession(token);
        if (session == null || !session.appUserId().equals(user.getId()) || session.endedOn() != null) {
            return refuse(401, CODE_SESSION_EXPIRED, MSG_SESSION_EXPIRED, Map.of());
        }
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ACTIVE");
        body.put("username", user.getUsername());
        body.put("deviceId", session.deviceId());
        body.put("deviceCode", session.shortCode());
        body.put("deviceLabel", session.deviceLabel());
        body.put("signedInOn", session.createdOn() + "Z");
        body.put("sessionTimeoutMinutes", settings.sessionTimeoutMinutes());
        body.put("minimumVersion", settings.minimumVersion());
        body.put("updateUrl", settings.updateUrl());
        return Response.ok(GSON.toJson(body), MediaType.APPLICATION_JSON).build();
    }

    private Response updateRequired(final String minimum) {
        final Map<String, Object> extra = new HashMap<>();
        extra.put("minimumVersion", minimum);
        extra.put("updateUrl", settings.updateUrl());
        return refuse(426, CODE_UPDATE_REQUIRED, MSG_UPDATE_REQUIRED, extra);
    }

    private static Response refuse(final int status, final String code, final String message, final Map<String, Object> extra) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(OfficerAppErrors.json(status, code, message, extra)).build();
    }

    private static Map<String, Object> reasonOf(final String reason) {
        final Map<String, Object> extra = new HashMap<>();
        extra.put("reason", reason);
        return extra;
    }

    private void recordLogin(final Long userId, final String username, final Long deviceId, final String deviceLabel,
            final String eventType, final String result, final String description, final String ip) {
        recorder.record(OfficerAppEvent.builder().appUserId(userId).username(StringUtils.left(username, 100)).deviceId(deviceId)
                .deviceLabel(deviceLabel).eventType(eventType).category(OfficerAppEvent.CATEGORY_LOGIN).result(result)
                .actor(OfficerAppEvent.ACTOR_OFFICER).description(description).ipAddress(ip).build());
    }

    private Long lookupUser(final String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        final List<Long> ids = jdbcTemplate.queryForList("select id from m_appuser where username = ? and is_deleted = false", Long.class,
                username);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static String whyFailed(final AuthenticationException e) {
        if (e instanceof PasswordResetRequiredException) {
            return "password must be changed";
        }
        if (e instanceof BadCredentialsException) {
            return "wrong username or password";
        }
        if (e instanceof LockedException) {
            return "account locked";
        }
        if (e instanceof DisabledException) {
            return "account disabled";
        }
        return StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
    }

    private static JsonObject parse(final String body) {
        try {
            final JsonElement element = JsonParser.parseString(StringUtils.defaultIfBlank(body, "{}"));
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (RuntimeException e) {
            // reported below
        }
        throw invalid("body", "error.msg.officerapp.body.invalid", "The request body must be a JSON object");
    }

    private static DeviceInfo deviceOf(final JsonObject json) {
        final JsonObject device = json.has("device") && json.get("device").isJsonObject() ? json.getAsJsonObject("device")
                : new JsonObject();
        final String installationId = text(device, "installationId");
        if (installationId == null || !INSTALLATION_ID.matcher(installationId).matches()) {
            throw invalid("device.installationId", "validation.msg.officerapp.device.installationId.invalid",
                    "device.installationId is required: 8 to 64 letters, digits or dashes");
        }
        return new DeviceInfo(installationId, text(device, "manufacturer"), text(device, "model"), text(device, "osVersion"),
                text(device, "appVersion"), text(device, "appBuild"));
    }

    private static String text(final JsonObject json, final String name) {
        final JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? null : StringUtils.trimToNull(value.getAsString());
    }

    private static PlatformApiDataValidationException invalid(final String parameter, final String code, final String message) {
        final List<ApiParameterError> errors = new ArrayList<>();
        errors.add(ApiParameterError.parameterError(code, message, parameter));
        return new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.", errors);
    }
}
