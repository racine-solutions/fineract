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
package org.apache.fineract.selfservice.admin.service;

import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.CLIENT_IDS_PARAM;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.CURRENCY_CODE_PARAM;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.DAILY_LIMIT_PARAM;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.LIMITS_PARAM;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.METHOD_FORCE_CHANGE;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.METHOD_PARAM;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.METHOD_SEND_LINK;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.PER_TRANSFER_LIMIT_PARAM;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.REASON_MAX_LENGTH;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.REASON_PARAM;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.RESOURCE_NAME;
import static org.apache.fineract.selfservice.admin.api.SelfServiceUsersApiConstants.REVOKE_SESSIONS_PARAM;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.apache.fineract.selfservice.admin.exception.SelfServiceUserNotFoundException;
import org.apache.fineract.selfservice.notification.SelfServiceNotificationEvent;
import org.apache.fineract.selfservice.registration.service.SelfServiceForgotPasswordWritePlatformService;
import org.apache.fineract.selfservice.security.service.SelfServiceAuthenticationTokenService;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserClientMapping;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserRepository;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelfServiceUserAdminWriteServiceImpl implements SelfServiceUserAdminWriteService {

    static final String NO_REASON = "Not given";
    /** Caches of AppSelfServiceUserClientMappingRepository lookups; cleared when links change. */
    static final List<String> CLIENT_MAPPING_CACHES = List.of("appSelfServiceUserClientFetchByClientId",
            "appSelfServiceUserUserClientFetchByAppuserUsername", "appSelfServiceUserUserClientFetchByAppUserId");

    private final AppSelfServiceUserRepository appSelfServiceUserRepository;
    private final PlatformSecurityContext context;
    private final SelfServiceAuthenticationTokenService tokenService;
    private final SelfServiceForgotPasswordWritePlatformService forgotPasswordService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final ClientRepositoryWrapper clientRepository;
    private final CacheManager cacheManager;

    @Override
    @Transactional
    public CommandProcessingResult makeViewOnly(final Long userId, final JsonCommand command) {
        final AppSelfServiceUser user = changeStatus(userId, command, SelfServiceUserStatus.VIEW_ONLY,
                EnumSet.of(SelfServiceUserStatus.ACTIVE, SelfServiceUserStatus.SUSPENDED));
        final Boolean revoke = command.booleanObjectValueOfParameterNamed(REVOKE_SESSIONS_PARAM);
        if (revoke == null || revoke) {
            tokenService.revokeAllSessions(userId);
        }
        notifyClient(user, "your mobile app access is now view only. You can see your accounts but cannot make transfers or other changes.",
                command);
        return result(user, command);
    }

    @Override
    @Transactional
    public CommandProcessingResult suspend(final Long userId, final JsonCommand command) {
        final AppSelfServiceUser user = changeStatus(userId, command, SelfServiceUserStatus.SUSPENDED,
                EnumSet.of(SelfServiceUserStatus.ACTIVE, SelfServiceUserStatus.VIEW_ONLY));
        tokenService.revokeAllSessions(userId);
        notifyClient(user, "your mobile app access has been suspended. You cannot log in until your branch reactivates it.", command);
        return result(user, command);
    }

    @Override
    @Transactional
    public CommandProcessingResult reactivate(final Long userId, final JsonCommand command) {
        final AppSelfServiceUser user = changeStatus(userId, command, SelfServiceUserStatus.ACTIVE,
                EnumSet.of(SelfServiceUserStatus.VIEW_ONLY, SelfServiceUserStatus.SUSPENDED));
        notifyClient(user, "your mobile app access has been restored. You can log in and transact again.", command);
        return result(user, command);
    }

    @Override
    @Transactional
    public CommandProcessingResult close(final Long userId, final JsonCommand command) {
        final AppSelfServiceUser user = changeStatus(userId, command, SelfServiceUserStatus.CLOSED,
                EnumSet.of(SelfServiceUserStatus.PENDING, SelfServiceUserStatus.ACTIVE, SelfServiceUserStatus.VIEW_ONLY,
                        SelfServiceUserStatus.SUSPENDED));
        tokenService.revokeAllSessions(userId);
        notifyClient(user, "your mobile app access has been closed.", command);
        return result(user, command);
    }

    @Override
    @Transactional
    public CommandProcessingResult unlock(final Long userId, final JsonCommand command) {
        final String reason = validateReason(command, false);
        final AppSelfServiceUser user = findInScope(userId);
        if (user.isAccountNonLocked()) {
            throw new GeneralPlatformDomainRuleException("error.msg.selfservice.user.not.locked",
                    "Self-service user " + userId + " is not locked", userId);
        }
        user.unlock(reason, currentStaffId());
        appSelfServiceUserRepository.saveAndFlush(user);
        notifyClient(user, "your mobile app account has been unlocked. You can log in again.", command);
        return result(user, command);
    }

    @Override
    @Transactional
    public CommandProcessingResult revokeSessions(final Long userId, final JsonCommand command) {
        validateReason(command, false);
        final AppSelfServiceUser user = findInScope(userId);
        tokenService.revokeAllSessions(userId);
        notifyClient(user, "you have been signed out of the mobile app on all devices. Please log in again.", command);
        return result(user, command);
    }

    @Override
    @Transactional
    public CommandProcessingResult resetPassword(final Long userId, final JsonCommand command) {
        validateReason(command, false);
        final String method = StringUtils.defaultIfBlank(command.stringValueOfParameterNamed(METHOD_PARAM), METHOD_SEND_LINK);
        final List<ApiParameterError> errors = new ArrayList<>();
        new DataValidatorBuilder(errors).resource(RESOURCE_NAME).reset().parameter(METHOD_PARAM).value(method)
                .isOneOfTheseStringValues(List.of(METHOD_SEND_LINK, METHOD_FORCE_CHANGE));
        throwIfInvalid(errors);

        final AppSelfServiceUser user = findInScope(userId);
        if (user.getStatus() != SelfServiceUserStatus.ACTIVE) {
            throw new GeneralPlatformDomainRuleException("error.msg.selfservice.user.password.reset.requires.active",
                    "A password can only be reset for an Active self-service user", userId);
        }

        if (METHOD_FORCE_CHANGE.equalsIgnoreCase(method)) {
            user.updatePasswordResetRequired(true);
            appSelfServiceUserRepository.saveAndFlush(user);
            tokenService.revokeAllSessions(userId);
            notifyClient(user, "you must set a new password the next time you log in to the mobile app.", command);
        } else {
            final String body = "{\"username\":\"" + user.getUsername().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            if (forgotPasswordService.createForgotPasswordRequest(body) == null) {
                throw new GeneralPlatformDomainRuleException("error.msg.selfservice.user.password.reset.no.contact",
                        "The client has no email address or mobile number to send the reset code to", userId);
            }
            notifyClient(user, "your branch started a password reset. Use the code we sent you to set a new password.", command);
        }
        return result(user, command);
    }

    @Override
    @Transactional
    public CommandProcessingResult updateLimits(final Long userId, final JsonCommand command) {
        validateReason(command, false);
        final List<LimitRow> limits = parseLimits(command);
        final AppSelfServiceUser user = findInScope(userId);

        jdbcTemplate.update("delete from m_selfservice_user_limit where appuser_id = ?", userId);
        final Timestamp now = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC));
        final Long staffId = currentStaffId();
        for (final LimitRow limit : limits) {
            jdbcTemplate.update(
                    "insert into m_selfservice_user_limit (appuser_id, currency_code, per_transfer_limit, daily_limit,"
                            + " updated_on_utc, updated_by_id) values (?, ?, ?, ?, ?, ?)",
                    userId, limit.currencyCode(), limit.perTransfer(), limit.daily(), now, staffId);
        }

        final String summary = limits.isEmpty() ? "your branch removed the transfer limits on your mobile app."
                : "your mobile app transfer limits are now "
                        + String.join("; ", limits.stream().map(SelfServiceUserAdminWriteServiceImpl::describe).toList()) + ".";
        notifyClient(user, summary, command);

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(LIMITS_PARAM, limits.stream().map(LimitRow::toMap).toList());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(userId).with(changes).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateClients(final Long userId, final JsonCommand command) {
        validateReason(command, false);
        final List<Long> clientIds = parseClientIds(command);
        final AppSelfServiceUser user = findInScope(userId);
        final String staffHierarchy = context.officeHierarchy();

        final List<Client> clients = new ArrayList<>();
        for (final Long clientId : clientIds) {
            final Client client = clientRepository.findOneWithNotFoundDetection(clientId);
            if (client.getOffice() == null || !client.getOffice().getHierarchy().startsWith(staffHierarchy)) {
                throw new ClientNotFoundException(clientId);
            }
            final Integer usedElsewhere = jdbcTemplate.queryForObject(
                    "select count(*) from m_selfservice_user_client_mapping where client_id = ? and appuser_id <> ?", Integer.class,
                    clientId, userId);
            if (usedElsewhere != null && usedElsewhere > 0) {
                throw new GeneralPlatformDomainRuleException("error.msg.selfservice.user.client.already.linked",
                        "Client " + clientId + " already uses the mobile app through another app user", clientId);
            }
            clients.add(client);
        }
        final Office office = clients.get(0).getOffice();
        if (clients.stream().anyMatch(c -> !office.getId().equals(c.getOffice().getId()))) {
            throw new GeneralPlatformDomainRuleException("error.msg.selfservice.user.clients.different.offices",
                    "All clients of a mobile app user must belong to the same office", userId);
        }

        jdbcTemplate.update("delete from m_selfservice_user_client_mapping where appuser_id = ?", userId);
        for (final Client client : clients) {
            jdbcTemplate.update("insert into m_selfservice_user_client_mapping (appuser_id, client_id) values (?, ?)", userId,
                    client.getId());
        }
        // The app user belongs to the office of the clients they act for
        jdbcTemplate.update("update m_appselfservice_user set office_id = ? where id = ?", office.getId(), userId);
        evictClientMappingCaches();

        notifyClient(user, clients.get(0).getMobileNo(), "the client accounts you can use in the mobile app were changed by your branch.",
                command);

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(CLIENT_IDS_PARAM, clientIds);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(userId).withOfficeId(office.getId())
                .with(changes).build();
    }

    private List<LimitRow> parseLimits(final JsonCommand command) {
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE_NAME);
        final JsonArray array = command.parsedJson() != null && command.parsedJson().isJsonObject()
                && command.parsedJson().getAsJsonObject().has(LIMITS_PARAM)
                && command.parsedJson().getAsJsonObject().get(LIMITS_PARAM).isJsonArray()
                        ? command.parsedJson().getAsJsonObject().getAsJsonArray(LIMITS_PARAM)
                        : null;
        validator.reset().parameter(LIMITS_PARAM).value(array).notNull();
        throwIfInvalid(errors);

        final List<LimitRow> limits = new ArrayList<>();
        final Set<String> currencies = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            final String parameter = LIMITS_PARAM + "[" + i + "]";
            final JsonObject row = array.get(i).isJsonObject() ? array.get(i).getAsJsonObject() : new JsonObject();
            final String currency = StringUtils.upperCase(StringUtils.trimToNull(asString(row, CURRENCY_CODE_PARAM)));
            final BigDecimal perTransfer = asDecimal(row, PER_TRANSFER_LIMIT_PARAM);
            final BigDecimal daily = asDecimal(row, DAILY_LIMIT_PARAM);
            validator.reset().parameter(parameter + "." + CURRENCY_CODE_PARAM).value(currency).notBlank().notExceedingLengthOf(3);
            if (currency != null && currency.length() != 3) {
                validator.reset().parameter(parameter + "." + CURRENCY_CODE_PARAM).failWithCode("must.be.three.letters",
                        "Use the three-letter currency code, for example UGX");
            }
            validator.reset().parameter(parameter + "." + PER_TRANSFER_LIMIT_PARAM).value(perTransfer).ignoreIfNull().positiveAmount();
            validator.reset().parameter(parameter + "." + DAILY_LIMIT_PARAM).value(daily).ignoreIfNull().positiveAmount();
            if (perTransfer == null && daily == null) {
                validator.reset().parameter(parameter).failWithCode("no.limit.given", "Give a per-transfer limit, a daily limit, or both");
            }
            if (perTransfer != null && daily != null && daily.compareTo(perTransfer) < 0) {
                validator.reset().parameter(parameter + "." + DAILY_LIMIT_PARAM).failWithCode("less.than.per.transfer.limit",
                        "The daily limit cannot be lower than the per-transfer limit");
            }
            if (currency != null && !currencies.add(currency)) {
                validator.reset().parameter(parameter + "." + CURRENCY_CODE_PARAM).failWithCode("duplicate",
                        "Each currency can appear only once");
            }
            limits.add(new LimitRow(currency, perTransfer, daily));
        }
        throwIfInvalid(errors);
        return limits;
    }

    private List<Long> parseClientIds(final JsonCommand command) {
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE_NAME);
        final String[] values = command.arrayValueOfParameterNamed(CLIENT_IDS_PARAM);
        validator.reset().parameter(CLIENT_IDS_PARAM).value(values == null || values.length == 0 ? null : values).notNull();
        throwIfInvalid(errors);
        final List<Long> ids = new ArrayList<>();
        for (final String value : values) {
            try {
                final Long id = Long.valueOf(value.trim());
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException e) {
                validator.reset().parameter(CLIENT_IDS_PARAM).failWithCode("not.a.number", "Client ids must be numbers");
            }
        }
        throwIfInvalid(errors);
        return ids;
    }

    private static String asString(final JsonObject row, final String name) {
        final JsonElement element = row.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static BigDecimal asDecimal(final JsonObject row, final String name) {
        final JsonElement element = row.get(name);
        if (element == null || element.isJsonNull() || StringUtils.isBlank(element.getAsString())) {
            return null;
        }
        return element.getAsBigDecimal();
    }

    private static String describe(final LimitRow limit) {
        final List<String> parts = new ArrayList<>();
        if (limit.perTransfer() != null) {
            parts.add(limit.perTransfer().stripTrailingZeros().toPlainString() + " per transfer");
        }
        if (limit.daily() != null) {
            parts.add(limit.daily().stripTrailingZeros().toPlainString() + " per day");
        }
        return limit.currencyCode() + " " + String.join(" and ", parts);
    }

    private void evictClientMappingCaches() {
        for (final String cacheName : CLIENT_MAPPING_CACHES) {
            final Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    record LimitRow(String currencyCode, BigDecimal perTransfer, BigDecimal daily) {

        Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put(CURRENCY_CODE_PARAM, currencyCode);
            map.put(PER_TRANSFER_LIMIT_PARAM, perTransfer);
            map.put(DAILY_LIMIT_PARAM, daily);
            return map;
        }
    }

    private AppSelfServiceUser changeStatus(final Long userId, final JsonCommand command, final SelfServiceUserStatus target,
            final Set<SelfServiceUserStatus> allowedFrom) {
        final String reason = validateReason(command, true);
        final AppSelfServiceUser user = findInScope(userId);
        final SelfServiceUserStatus current = user.getStatus();
        if (!allowedFrom.contains(current)) {
            throw new GeneralPlatformDomainRuleException("error.msg.selfservice.user.status.change.not.allowed",
                    "A self-service user cannot be changed from " + current.name() + " to " + target.name(), userId, current.name(),
                    target.name());
        }
        user.changeStatus(target, reason, currentStaffId());
        appSelfServiceUserRepository.saveAndFlush(user);
        return user;
    }

    /** Loads the user if it is in the staff member's office or one of its child offices; otherwise "not found". */
    AppSelfServiceUser findInScope(final Long userId) {
        final AppSelfServiceUser user = appSelfServiceUserRepository.findById(userId)
                .orElseThrow(() -> new SelfServiceUserNotFoundException(userId));
        final Office office = effectiveOffice(user);
        if (office == null || office.getHierarchy() == null || !office.getHierarchy().startsWith(context.officeHierarchy())) {
            throw new SelfServiceUserNotFoundException(userId);
        }
        return user;
    }

    /** The app user belongs to the office of the client they act for, falling back to the office on the user record. */
    static Office effectiveOffice(final AppSelfServiceUser user) {
        final Client client = primaryClient(user);
        return client != null && client.getOffice() != null ? client.getOffice() : user.getOffice();
    }

    private static Client primaryClient(final AppSelfServiceUser user) {
        if (user.getAppUserClientMappings() == null) {
            return null;
        }
        return user.getAppUserClientMappings().stream().map(AppSelfServiceUserClientMapping::getClient)
                .filter(c -> c != null && c.getId() != null).min((a, b) -> Long.compare(a.getId(), b.getId())).orElse(null);
    }

    private String validateReason(final JsonCommand command, final boolean required) {
        final String reason = StringUtils.trimToNull(command.stringValueOfParameterNamed(REASON_PARAM));
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(RESOURCE_NAME);
        if (required) {
            validator.reset().parameter(REASON_PARAM).value(reason).notBlank().notExceedingLengthOf(REASON_MAX_LENGTH);
        } else {
            validator.reset().parameter(REASON_PARAM).value(reason).ignoreIfNull().notExceedingLengthOf(REASON_MAX_LENGTH);
        }
        throwIfInvalid(errors);
        return reason;
    }

    private static void throwIfInvalid(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.", errors);
        }
    }

    private Long currentStaffId() {
        return context.authenticatedUser().getId();
    }

    /** Tells the client about the change by email and SMS (both, every time). */
    private void notifyClient(final AppSelfServiceUser user, final String accessChange, final JsonCommand command) {
        final Client client = primaryClient(user);
        notifyClient(user, client != null ? client.getMobileNo() : null, accessChange, command);
    }

    private void notifyClient(final AppSelfServiceUser user, final String mobileNumber, final String accessChange,
            final JsonCommand command) {
        final Map<String, Object> contextData = new HashMap<>();
        contextData.put("accessChange", accessChange);
        contextData.put("reason", StringUtils.defaultIfBlank(command.stringValueOfParameterNamed(REASON_PARAM), NO_REASON));
        try {
            applicationEventPublisher.publishEvent(SelfServiceNotificationEvent.withTenantContext(this,
                    SelfServiceNotificationEvent.Type.ACCESS_CHANGED, user.getId(), user.getFirstname(), user.getLastname(),
                    user.getUsername(), user.getEmail(), mobileNumber, true, null, LocaleContextHolder.getLocale(), contextData));
        } catch (RuntimeException e) {
            // Never undo the access change because a message could not be queued
            log.error("Could not queue the access-changed notification for self-service user {}", user.getId(), e);
        }
    }

    private CommandProcessingResult result(final AppSelfServiceUser user, final JsonCommand command) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", user.getStatus().getCode());
        changes.put("locked", !user.isAccountNonLocked());
        final String reason = StringUtils.trimToNull(command.stringValueOfParameterNamed(REASON_PARAM));
        if (reason != null) {
            changes.put(REASON_PARAM, reason);
        }
        final Office office = effectiveOffice(user);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(user.getId())
                .withOfficeId(office != null ? office.getId() : null).with(changes).build();
    }
}
