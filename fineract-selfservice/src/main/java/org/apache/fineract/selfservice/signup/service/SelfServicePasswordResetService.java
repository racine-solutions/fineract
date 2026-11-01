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
package org.apache.fineract.selfservice.signup.service;

import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.CODE_VALIDITY;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.RESEND_COOLDOWN;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.RESET_VALIDITY;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.SEND_WINDOW;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.selfservice.audit.data.SelfServiceEventEntry;
import org.apache.fineract.selfservice.audit.service.SelfServiceEventRecorder;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistration;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistrationRepository;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRequestType;
import org.apache.fineract.selfservice.security.service.SelfServicePasswordChangeService;
import org.apache.fineract.selfservice.security.service.SelfServiceUserIdentifierResolver;
import org.apache.fineract.selfservice.signup.exception.SelfServiceCodeException;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserRepository;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot password with a 6-digit code.
 *
 * <p>
 * The member gives their username, email or phone. When it points to exactly one app user who may log in, a code goes
 * to the phone on their client record (SMS), or to the app user's email when the SMS fails. The answer is the same
 * whether or not an account was found, codes were sent or limits were hit, so the screen cannot be used to discover
 * accounts. A correct code with the returned resetId sets the new password and ends the user's sessions.
 */
@Slf4j
@Service
public class SelfServicePasswordResetService {

    static final String RESET_EVENT = "PASSWORD_RENEWED";

    private final SelfServiceRegistrationRepository registrationRepository;
    private final SelfServiceUserIdentifierResolver identifierResolver;
    private final AppSelfServiceUserRepository appUserRepository;
    private final SelfServiceCodeSender codeSender;
    private final SelfServicePasswordChangeService passwordChangeService;
    private final SelfServiceSupportContacts supportContacts;
    private final JdbcTemplate jdbcTemplate;
    private final SelfServiceEventRecorder eventRecorder;
    private final ClientRepository clientRepository;
    Supplier<LocalDateTime> clock = SelfServiceCodeChecks.systemClock();

    public SelfServicePasswordResetService(final SelfServiceRegistrationRepository registrationRepository,
            final SelfServiceUserIdentifierResolver identifierResolver, final AppSelfServiceUserRepository appUserRepository,
            final SelfServiceCodeSender codeSender, final SelfServicePasswordChangeService passwordChangeService,
            final SelfServiceSupportContacts supportContacts, final JdbcTemplate jdbcTemplate, final SelfServiceEventRecorder eventRecorder,
            final ClientRepository clientRepository) {
        this.registrationRepository = registrationRepository;
        this.identifierResolver = identifierResolver;
        this.appUserRepository = appUserRepository;
        this.codeSender = codeSender;
        this.passwordChangeService = passwordChangeService;
        this.supportContacts = supportContacts;
        this.jdbcTemplate = jdbcTemplate;
        this.eventRecorder = eventRecorder;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public Map<String, Object> start(final String identifier) {
        if (StringUtils.isBlank(identifier)) {
            throw new IllegalArgumentException("Enter your username, email or phone number.");
        }
        final List<SelfServiceUserIdentifierResolver.Match> matches = identifierResolver.candidates(identifier);
        if (matches.size() != 1) {
            log.info("Password reset asked for an identifier that fits {} app users; nothing sent", matches.size());
            return neutralAnswer(UUID.randomUUID().toString());
        }
        final AppSelfServiceUser user = appUserRepository.findById(matches.get(0).userId()).orElse(null);
        final Client client = user == null ? null : linkedClient(user);
        if (user == null || client == null || !mayReset(user)) {
            log.info("Password reset asked for app user {} who cannot reset now; nothing sent", user == null ? null : user.getId());
            return neutralAnswer(UUID.randomUUID().toString());
        }

        final LocalDateTime now = clock.get();
        if (!canSend(client, now)) {
            return neutralAnswer(UUID.randomUUID().toString());
        }
        registrationRepository.findOpenByClientAndType(client.getId(), SelfServiceRequestType.RESET_CODE)
                .forEach(SelfServiceRegistration::close);
        final String resetId = UUID.randomUUID().toString();
        final SelfServiceRegistration request = SelfServiceRegistration.instance(client, client.getAccountNumber(), client.getFirstname(),
                client.getMiddlename(), client.getLastname(), client.getMobileNo(), StringUtils.defaultString(user.getEmail()), null,
                resetId, user.getUsername(), SelfServiceRegistration.PASSWORD_RESET_SENTINEL, SelfServiceRequestType.RESET_CODE,
                now.plus(RESET_VALIDITY));
        sendCode(request, client, user, now);
        return neutralAnswer(resetId);
    }

    @Transactional
    public Map<String, Object> resend(final String resetId) {
        final LocalDateTime now = clock.get();
        final SelfServiceRegistration request = openRequest(resetId, now);
        if (request == null) {
            return neutralAnswer(StringUtils.defaultIfBlank(resetId, UUID.randomUUID().toString()));
        }
        final Client client = request.getClient();
        final AppSelfServiceUser user = appUserRepository.findAppSelfServiceUserByName(request.getUsername());
        if (user != null && mayReset(user) && canSend(client, now)) {
            sendCode(request, client, user, now);
        }
        return neutralAnswer(request.getExternalAuthorizationToken());
    }

    /** Checks the code and sets the new password. Returns the username so the app can fill in the login screen. */
    @Transactional(noRollbackFor = SelfServiceCodeException.class)
    public Map<String, Object> verify(final String resetId, final String code, final String password, final String repeatPassword) {
        passwordChangeService.validateNewPassword(password, repeatPassword);
        final LocalDateTime now = clock.get();
        final SelfServiceRegistration request = openRequest(resetId, now);
        if (request == null) {
            throw wrongCode();
        }
        try {
            SelfServiceCodeChecks.checkCode(request, code, now, false);
        } catch (SelfServiceCodeException e) {
            // The same answer as for a made-up resetId, even once the tries are used up
            throw wrongCode();
        }

        final AppSelfServiceUser user = appUserRepository.findAppSelfServiceUserByName(request.getUsername());
        final Client linked = user == null ? null : linkedClient(user);
        if (user == null || linked == null || !linked.getId().equals(request.getClient().getId()) || !mayReset(user)) {
            request.close();
            throw new SelfServiceCodeException(400, "error.msg.password.reset.not.allowed",
                    "Your password cannot be reset in the app. " + StringUtils.capitalize(supportContacts.callInstruction()) + ".");
        }
        passwordChangeService.setNewPassword(user, password, repeatPassword);
        request.close();
        registrationRepository.saveAndFlush(request);
        eventRecorder.record(SelfServiceEventEntry.builder().appUserId(user.getId()).username(user.getUsername()).clientId(linked.getId())
                .eventType(RESET_EVENT).category("PASSWORD").result(SelfServiceEventEntry.RESULT_SUCCESS)
                .actor(SelfServiceEventEntry.ACTOR_CLIENT).description("Password reset with a code").build());
        log.info("Mobile app user {} reset their password with a code", user.getId());

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", user.getUsername());
        return body;
    }

    private void sendCode(final SelfServiceRegistration request, final Client client, final AppSelfServiceUser user,
            final LocalDateTime now) {
        final String code = SelfServiceVerificationCode.generate();
        request.issueCode(SelfServiceVerificationCode.hash(code), now, CODE_VALIDITY, SEND_WINDOW);
        // The phone on the client record, then the app user's own email
        final SelfServiceCodeSender.Delivery delivery = codeSender.send(SelfServiceCodeSender.Purpose.PASSWORD_RESET, code,
                client.getMobileNo(), user.getEmail(), user.getFirstname(), SelfServiceCodeSender.Channel.SMS,
                SelfServiceCodePolicy.codeValidityMinutes());
        request.recordDelivery(delivery == null ? null : delivery.channel().name());
        registrationRepository.saveAndFlush(request);
        if (delivery == null) {
            log.warn("Password reset code for app user {} could not be sent by SMS or email", user.getId());
        }
    }

    private boolean canSend(final Client client, final LocalDateTime now) {
        try {
            SelfServiceCodeChecks.checkCanSend(
                    registrationRepository.findSentSince(client.getId(), SelfServiceRequestType.RESET_CODE, now.minus(SEND_WINDOW)), now);
            return true;
        } catch (SelfServiceCodeException e) {
            log.info("Password reset code for client {} not sent: {}", client.getId(), e.getCode());
            return false;
        }
    }

    private SelfServiceRegistration openRequest(final String resetId, final LocalDateTime now) {
        if (StringUtils.isBlank(resetId)) {
            return null;
        }
        final SelfServiceRegistration request = registrationRepository.getRequestByExternalAuthorizationToken(resetId.trim(),
                SelfServiceRequestType.RESET_CODE);
        return request == null || request.isConsumed() || request.isExpired(now) ? null : request;
    }

    private Client linkedClient(final AppSelfServiceUser user) {
        final List<Long> clientIds = jdbcTemplate
                .queryForList("select client_id from m_selfservice_user_client_mapping where appuser_id = ?", Long.class, user.getId());
        if (clientIds.size() != 1) {
            return null;
        }
        return clientRepository.findById(clientIds.get(0)).orElse(null);
    }

    /**
     * Only users who may log in: active or view only, not deleted. Suspended, closed and pending users call support.
     */
    private static boolean mayReset(final AppSelfServiceUser user) {
        final SelfServiceUserStatus status = user.getStatus();
        return !user.isDeleted() && (status == SelfServiceUserStatus.ACTIVE || status == SelfServiceUserStatus.VIEW_ONLY);
    }

    private Map<String, Object> neutralAnswer(final String resetId) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("resetId", resetId);
        body.put("codeLength", SelfServiceVerificationCode.LENGTH);
        body.put("codeExpiresInSeconds", CODE_VALIDITY.toSeconds());
        body.put("resendAfterSeconds", RESEND_COOLDOWN.toSeconds());
        body.put("message", "If an account matches, we sent a 6-digit code to its registered phone or email. If nothing arrives in a"
                + " few minutes, " + supportContacts.callInstruction() + ".");
        return body;
    }

    private static SelfServiceCodeException wrongCode() {
        return new SelfServiceCodeException(400, "error.msg.code.invalid",
                "That code is wrong or has expired. Check it, or ask for a new code.");
    }
}
