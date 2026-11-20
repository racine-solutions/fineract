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
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.BLOCK_DEVICES_PARAM;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.DEVICE_ACTIVE;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.DEVICE_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.END_BLOCKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.END_REVOKED;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.NOTIFY_PARAM;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.REASON_MAX_LENGTH;
import static org.apache.fineract.racine.officerapp.domain.OfficerAppConstants.REASON_PARAM;

import com.google.gson.Gson;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.racine.officerapp.data.OfficerAppDeviceData;
import org.apache.fineract.racine.officerapp.data.OfficerAppUserData;
import org.apache.fineract.racine.officerapp.domain.OfficerAppConstants;
import org.apache.fineract.racine.officerapp.domain.OfficerAppEvent;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin changes to field officer app access. Run through the command pipeline, so permissions and maker-checker apply.
 * None of these change the staff account itself: web-app login, roles and password stay as they are.
 */
@Service
@RequiredArgsConstructor
public class OfficerAppWriteService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final OfficerAppReadService readService;
    private final OfficerAppSessionService sessions;
    private final OfficerAppEventRecorder recorder;
    private final OfficerAppNotifier notifier;
    private final StaffAccountLock staffAccounts;

    @Transactional
    public CommandProcessingResult turnOff(final Long userId, final JsonCommand command) {
        final String reason = reason(command, true);
        final OfficerAppUserData user = readService.retrieveOfficer(userId);
        if (ACCESS_OFF.equals(user.accessStatus()) && user.accessExplicit()) {
            throw new GeneralPlatformDomainRuleException("error.msg.officerapp.access.already.off",
                    "Mobile app access is already off for user " + userId, userId);
        }
        final AppUser staff = context.authenticatedUser();
        sessions.saveAccess(userId, ACCESS_OFF, reason, staff.getId());
        final int ended = sessions.endSessionsOfUser(userId, END_BLOCKED);
        int blocked = 0;
        if (Boolean.TRUE.equals(command.booleanObjectValueOfParameterNamed(BLOCK_DEVICES_PARAM))) {
            blocked = jdbcTemplate.update(
                    "update m_officerapp_device set status = ?, block_reason = ?, status_changed_by_id = ?,"
                            + " status_changed_on_utc = ? where appuser_id = ? and status = ?",
                    DEVICE_BLOCKED, reason, staff.getId(), now(), userId, DEVICE_ACTIVE);
        }
        audit(user, null, "ACCESS_OFF", "Mobile app access turned off" + (ended > 0 ? "; signed out of " + ended + " device(s)" : "")
                + (blocked > 0 ? "; " + blocked + " device(s) blocked" : ""), reason);
        notify(userId, command, OfficerAppNotifier.accessTurnedOff(reason));
        final Map<String, Object> changes = changes(reason);
        changes.put("accessStatus", ACCESS_OFF);
        changes.put("sessionsEnded", ended);
        changes.put("devicesBlocked", blocked);
        return result(command, userId, user.officeId(), changes);
    }

    @Transactional
    public CommandProcessingResult turnOn(final Long userId, final JsonCommand command) {
        final String reason = reason(command, true);
        final OfficerAppUserData user = readService.retrieveOfficer(userId);
        if (ACCESS_ALLOWED.equals(user.accessStatus()) && user.accessExplicit()) {
            throw new GeneralPlatformDomainRuleException("error.msg.officerapp.access.already.on",
                    "Mobile app access is already on for user " + userId, userId);
        }
        sessions.saveAccess(userId, ACCESS_ALLOWED, reason, context.authenticatedUser().getId());
        audit(user, null, "ACCESS_ON", "Mobile app access turned on", reason);
        notify(userId, command, OfficerAppNotifier.accessTurnedOn(reason));
        final Map<String, Object> changes = changes(reason);
        changes.put("accessStatus", ACCESS_ALLOWED);
        return result(command, userId, user.officeId(), changes);
    }

    @Transactional
    public CommandProcessingResult signOutAll(final Long userId, final JsonCommand command) {
        final String reason = reason(command, false);
        final OfficerAppUserData user = readService.retrieveOfficer(userId);
        final int ended = sessions.endSessionsOfUser(userId, END_REVOKED);
        audit(user, null, "SIGNED_OUT_ALL", "Signed out of the app on all devices (" + ended + " session(s) ended)", reason);
        if (ended > 0) {
            notifier.sendAfterSave(userId, OfficerAppNotifier.signedOutByAdmin("all your devices", reason));
        }
        final Map<String, Object> changes = changes(reason);
        changes.put("sessionsEnded", ended);
        return result(command, userId, user.officeId(), changes);
    }

    /** Unlocks a staff account locked after too many failed sign-ins (web app and field officer app). */
    @Transactional
    public CommandProcessingResult unlock(final Long userId, final JsonCommand command) {
        final String reason = reason(command, false);
        final OfficerAppUserData user = readService.retrieveOfficer(userId);
        if (!"LOCKED".equals(user.webStatus())) {
            throw new GeneralPlatformDomainRuleException("error.msg.officerapp.user.not.locked", "User " + userId + " is not locked",
                    userId);
        }
        staffAccounts.unlock(userId);
        audit(user, null, "ACCOUNT_UNLOCKED", "Account unlocked", reason);
        notifier.sendAfterSave(userId, OfficerAppNotifier.accountUnlocked());
        final Map<String, Object> changes = changes(reason);
        changes.put("locked", false);
        return result(command, userId, user.officeId(), changes);
    }

    @Transactional
    public CommandProcessingResult blockDevice(final Long deviceId, final JsonCommand command) {
        final String reason = reason(command, true);
        final OfficerAppDeviceData device = readService.retrieveDevice(deviceId);
        if (DEVICE_BLOCKED.equals(device.status())) {
            throw new GeneralPlatformDomainRuleException("error.msg.officerapp.device.already.blocked",
                    "Device " + deviceId + " is already blocked", deviceId);
        }
        setDeviceStatus(deviceId, DEVICE_BLOCKED, reason);
        final int ended = sessions.endSessionsOfDevice(deviceId, END_BLOCKED);
        audit(readService.retrieveOfficer(device.userId()), device, "DEVICE_BLOCKED",
                "Device " + device.label() + " (" + device.shortCode() + ") blocked", reason);
        notify(device.userId(), command,
                OfficerAppNotifier.deviceBlocked(device.label() + " (Device ID " + device.shortCode() + ")", reason));
        final Map<String, Object> changes = changes(reason);
        changes.put("status", DEVICE_BLOCKED);
        changes.put("sessionsEnded", ended);
        return result(command, deviceId, null, changes);
    }

    @Transactional
    public CommandProcessingResult unblockDevice(final Long deviceId, final JsonCommand command) {
        final String reason = reason(command, false);
        final OfficerAppDeviceData device = readService.retrieveDevice(deviceId);
        if (!DEVICE_BLOCKED.equals(device.status())) {
            throw new GeneralPlatformDomainRuleException("error.msg.officerapp.device.not.blocked",
                    "Device " + deviceId + " is not blocked", deviceId);
        }
        setDeviceStatus(deviceId, DEVICE_ACTIVE, null);
        audit(readService.retrieveOfficer(device.userId()), device, "DEVICE_UNBLOCKED",
                "Device " + device.label() + " (" + device.shortCode() + ") unblocked", reason);
        final Map<String, Object> changes = changes(reason);
        changes.put("status", DEVICE_ACTIVE);
        return result(command, deviceId, null, changes);
    }

    @Transactional
    public CommandProcessingResult signOutDevice(final Long deviceId, final JsonCommand command) {
        final String reason = reason(command, false);
        final OfficerAppDeviceData device = readService.retrieveDevice(deviceId);
        final int ended = sessions.endSessionsOfDevice(deviceId, END_REVOKED);
        audit(readService.retrieveOfficer(device.userId()), device, "DEVICE_SIGNED_OUT",
                "Signed out of " + device.label() + " (" + device.shortCode() + ")", reason);
        if (ended > 0) {
            notifier.sendAfterSave(device.userId(),
                    OfficerAppNotifier.signedOutByAdmin(device.label() + " (Device ID " + device.shortCode() + ")", reason));
        }
        final Map<String, Object> changes = changes(reason);
        changes.put("sessionsEnded", ended);
        return result(command, deviceId, null, changes);
    }

    private void setDeviceStatus(final Long deviceId, final String status, final String reason) {
        jdbcTemplate
                .update("update m_officerapp_device set status = ?, block_reason = ?, status_changed_by_id = ?, status_changed_on_utc = ?"
                        + " where id = ?", status, reason, context.authenticatedUser().getId(), now(), deviceId);
    }

    private void audit(final OfficerAppUserData user, final OfficerAppDeviceData device, final String eventType, final String description,
            final String reason) {
        recorder.recordAfterCommit(OfficerAppEvent.builder().appUserId(user.userId()).username(user.username()).officeId(user.officeId())
                .deviceId(device == null ? null : device.id()).deviceLabel(device == null ? null : device.label()).eventType(eventType)
                .category(OfficerAppEvent.CATEGORY_ACCESS).result(OfficerAppEvent.RESULT_SUCCESS).actor(OfficerAppEvent.ACTOR_STAFF)
                .staffUsername(context.authenticatedUser().getUsername()).description(description)
                .details(reason == null ? null : new Gson().toJson(Map.of(REASON_PARAM, reason))).build());
    }

    /** Access changes: sent unless the admin unticked "Tell the officer" in the dialog. */
    private void notify(final Long userId, final JsonCommand command, final OfficerAppNotifier.StaffMessage message) {
        final Boolean notify = command.booleanObjectValueOfParameterNamed(NOTIFY_PARAM);
        if (notify == null || notify) {
            notifier.sendAfterSave(userId, message);
        }
    }

    private static String reason(final JsonCommand command, final boolean required) {
        final String reason = StringUtils.trimToNull(command.stringValueOfParameterNamed(REASON_PARAM));
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(OfficerAppConstants.USER_ENTITY.toLowerCase());
        if (required) {
            validator.reset().parameter(REASON_PARAM).value(reason).notBlank().notExceedingLengthOf(REASON_MAX_LENGTH);
        } else {
            validator.reset().parameter(REASON_PARAM).value(reason).ignoreIfNull().notExceedingLengthOf(REASON_MAX_LENGTH);
        }
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.", errors);
        }
        return reason;
    }

    private static Map<String, Object> changes(final String reason) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (reason != null) {
            changes.put(REASON_PARAM, reason);
        }
        return changes;
    }

    private static CommandProcessingResult result(final JsonCommand command, final Long entityId, final Long officeId,
            final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(entityId).withOfficeId(officeId)
                .with(changes).build();
    }

    private static Timestamp now() {
        return Timestamp.valueOf(OfficerAppSessionService.nowUtc());
    }
}
