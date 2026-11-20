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

import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.ACCESS_ALLOWED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.ACCESS_OFF;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.DEVICE_ACTIVE;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.END_REPLACED;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.racine.officerapp.domain.DeviceInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Devices, app sessions and per-user app access of the field officer app. App sessions are separate from the staff
 * login: they only exist for requests made through the app, so ending one never affects the web-app.
 */
@Service
@RequiredArgsConstructor
public class OfficerAppSessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final OfficerAppSettings settings;

    /** A device row as needed at sign-in and on each request. */
    /** firstSignIn: this phone had never signed in with this user before. */
    public record DeviceRow(Long id, Long appUserId, String shortCode, String label, String status, String blockReason, String appVersion,
            boolean firstSignIn) {
    }

    /** An app session joined with its device. */
    public record SessionRow(Long id, Long deviceId, Long appUserId, LocalDateTime createdOn, LocalDateTime lastActivityOn,
            LocalDateTime endedOn, String endReason, String deviceStatus, String deviceBlockReason, String deviceLabel, String shortCode,
            String appVersion) {
    }

    /** The access decision for one staff user. */
    public record Access(boolean allowed, String status, String reason) {
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    /** Short code shown on the phone and the dashboard: first 8 hex digits of SHA-256(installation id), "7F3A-91C2". */
    public static String shortCode(final String installationId) {
        final String hex = sha256(installationId).substring(0, 8).toUpperCase(Locale.ROOT);
        return hex.substring(0, 4) + "-" + hex.substring(4);
    }

    public static String hashToken(final String token) {
        return sha256(token);
    }

    private static String sha256(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Access access(final Long appUserId) {
        final List<String[]> rows = jdbcTemplate.query("select status, reason from m_officerapp_access where appuser_id = ?",
                (rs, n) -> new String[] { rs.getString("status"), rs.getString("reason") }, appUserId);
        if (rows.isEmpty()) {
            final boolean allowed = settings.accessByDefault();
            return new Access(allowed, allowed ? ACCESS_ALLOWED : ACCESS_OFF, null);
        }
        return new Access(ACCESS_ALLOWED.equals(rows.get(0)[0]), rows.get(0)[0], rows.get(0)[1]);
    }

    /** Saves an admin's access decision (insert or update). */
    @Transactional
    public void saveAccess(final Long appUserId, final String status, final String reason, final Long changedById) {
        final Timestamp now = Timestamp.valueOf(nowUtc());
        final int updated = jdbcTemplate.update(
                "update m_officerapp_access set status = ?, reason = ?, changed_by_id = ?, changed_on_utc = ? where appuser_id = ?", status,
                reason, changedById, now, appUserId);
        if (updated == 0) {
            jdbcTemplate.update("insert into m_officerapp_access (appuser_id, status, reason, changed_by_id, changed_on_utc)"
                    + " values (?, ?, ?, ?, ?)", appUserId, status, reason, changedById, now);
        }
    }

    /** Registers the phone for this user, or refreshes its details. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeviceRow registerDevice(final Long appUserId, final DeviceInfo info, final String ip) {
        final Timestamp now = Timestamp.valueOf(nowUtc());
        final String model = StringUtils.left(StringUtils.trimToNull(info.model()), 100);
        final String manufacturer = StringUtils.left(StringUtils.trimToNull(info.manufacturer()), 100);
        final String osVersion = StringUtils.left(StringUtils.trimToNull(info.osVersion()), 50);
        final String appVersion = StringUtils.left(StringUtils.trimToNull(info.appVersion()), 50);
        final String appBuild = StringUtils.left(StringUtils.trimToNull(info.appBuild()), 50);
        final int updated = jdbcTemplate.update(
                "update m_officerapp_device set manufacturer = ?, model = ?, os_version = ?,"
                        + " app_version = ?, app_build = ?, last_seen_on_utc = ?, last_ip = ? where appuser_id = ? and installation_id = ?",
                manufacturer, model, osVersion, appVersion, appBuild, now, StringUtils.left(ip, 64), appUserId, info.installationId());
        final boolean firstSignIn = updated == 0;
        if (firstSignIn) {
            jdbcTemplate.update(
                    "insert into m_officerapp_device (appuser_id, installation_id, short_code, manufacturer, model,"
                            + " os_version, app_version, app_build, status, first_seen_on_utc, last_seen_on_utc, last_ip)"
                            + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    appUserId, info.installationId(), shortCode(info.installationId()), manufacturer, model, osVersion, appVersion,
                    appBuild, DEVICE_ACTIVE, now, now, StringUtils.left(ip, 64));
        }
        return jdbcTemplate.queryForObject(
                "select id, appuser_id, short_code, manufacturer, model, status, block_reason, app_version"
                        + " from m_officerapp_device where appuser_id = ? and installation_id = ?",
                (rs, n) -> new DeviceRow(rs.getLong("id"), rs.getLong("appuser_id"), rs.getString("short_code"),
                        DeviceInfo.label(rs.getString("manufacturer"), rs.getString("model")), rs.getString("status"),
                        rs.getString("block_reason"), rs.getString("app_version"), firstSignIn),
                appUserId, info.installationId());
    }

    /** Starts a session on the device and returns its token. Other open sessions on the same device end. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String startSession(final Long deviceId, final Long appUserId) {
        final Timestamp now = Timestamp.valueOf(nowUtc());
        jdbcTemplate.update("update m_officerapp_session set ended_on_utc = ?, end_reason = ? where device_id = ? and ended_on_utc is null",
                now, END_REPLACED, deviceId);
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbcTemplate.update("insert into m_officerapp_session (device_id, appuser_id, token_hash, created_on_utc, last_activity_on_utc)"
                + " values (?, ?, ?, ?, ?)", deviceId, appUserId, hashToken(token), now, now);
        return token;
    }

    public SessionRow findSession(final String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        final List<SessionRow> rows = jdbcTemplate.query(
                "select s.id, s.device_id, s.appuser_id, s.created_on_utc, s.last_activity_on_utc, s.ended_on_utc, s.end_reason,"
                        + " d.status, d.block_reason, d.manufacturer, d.model, d.short_code, d.app_version"
                        + " from m_officerapp_session s join m_officerapp_device d on d.id = s.device_id where s.token_hash = ?",
                (rs, n) -> new SessionRow(rs.getLong("id"), rs.getLong("device_id"), rs.getLong("appuser_id"),
                        toLocal(rs.getTimestamp("created_on_utc")), toLocal(rs.getTimestamp("last_activity_on_utc")),
                        toLocal(rs.getTimestamp("ended_on_utc")), rs.getString("end_reason"), rs.getString("status"),
                        rs.getString("block_reason"), DeviceInfo.label(rs.getString("manufacturer"), rs.getString("model")),
                        rs.getString("short_code"), rs.getString("app_version")),
                hashToken(token.trim()));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Saves the session's last activity and the device's last seen details. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touch(final SessionRow session, final String ip, final String appVersion) {
        final Timestamp now = Timestamp.valueOf(nowUtc());
        jdbcTemplate.update("update m_officerapp_session set last_activity_on_utc = ? where id = ?", now, session.id());
        jdbcTemplate.update(
                "update m_officerapp_device set last_seen_on_utc = ?, last_ip = ?, app_version = coalesce(?, app_version)"
                        + " where id = ?",
                now, StringUtils.left(ip, 64), StringUtils.left(StringUtils.trimToNull(appVersion), 50), session.deviceId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int endSession(final Long sessionId, final String reason) {
        return jdbcTemplate.update("update m_officerapp_session set ended_on_utc = ?, end_reason = ? where id = ? and ended_on_utc is null",
                Timestamp.valueOf(nowUtc()), reason, sessionId);
    }

    public int endSessionsOfUser(final Long appUserId, final String reason) {
        return jdbcTemplate.update(
                "update m_officerapp_session set ended_on_utc = ?, end_reason = ? where appuser_id = ? and ended_on_utc is null",
                Timestamp.valueOf(nowUtc()), reason, appUserId);
    }

    public int endSessionsOfDevice(final Long deviceId, final String reason) {
        return jdbcTemplate.update(
                "update m_officerapp_session set ended_on_utc = ?, end_reason = ? where device_id = ? and ended_on_utc is null",
                Timestamp.valueOf(nowUtc()), reason, deviceId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int endAllSessions(final String reason) {
        return jdbcTemplate.update("update m_officerapp_session set ended_on_utc = ?, end_reason = ? where ended_on_utc is null",
                Timestamp.valueOf(nowUtc()), reason);
    }

    static LocalDateTime toLocal(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
