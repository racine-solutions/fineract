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
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.MAX_ATTEMPTS;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.RESEND_COOLDOWN;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.SEND_WINDOW;
import static org.apache.fineract.selfservice.signup.service.SelfServiceCodePolicy.SIGNUP_VALIDITY;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.domain.BasicPasswordEncodablePlatformUser;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.notification.service.SmsText;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.selfservice.audit.data.SelfServiceEventEntry;
import org.apache.fineract.selfservice.audit.service.SelfServiceEventRecorder;
import org.apache.fineract.selfservice.notification.SelfServiceNotificationEvent;
import org.apache.fineract.selfservice.registration.SelfServiceApiConstants;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistration;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistrationRepository;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRequestType;
import org.apache.fineract.selfservice.security.service.SelfServiceContactConflictService;
import org.apache.fineract.selfservice.security.service.SelfServicePasswordChangeService;
import org.apache.fineract.selfservice.signup.exception.SelfServiceCodeException;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserClientMappingRepository;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.exception.RoleNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Mobile app sign-up with a 6-digit code.
 *
 * <p>
 * The member enters their account number, names, phone, email and password. When they match an active client record, a
 * code goes to the phone on that record (SMS), or to the email on that record when the SMS fails. The app keeps the
 * returned registrationId, so the member can leave the screen, resend or come back later and type the code. A correct
 * code creates the app user with the email as username, linked to that client.
 *
 * <p>
 * Safety: codes are only sent to contact details on the client record, never to ones typed in the request; a code only
 * works with its own registrationId; starting again replaces any earlier sign-up for the client, so an old reference or
 * code cannot complete it; and the client, username and link are checked again just before the user is created.
 */
@Slf4j
@Service
public class SelfServiceSignupService {

    static final String SIGNUP_CODE_EVENT = "ENROLLMENT_TOKEN";

    private final SelfServiceRegistrationRepository registrationRepository;
    private final ClientRepository clientRepository;
    private final SelfServiceCodeSender codeSender;
    private final SelfServicePasswordChangeService passwordChangeService;
    private final PlatformPasswordEncoder platformPasswordEncoder;
    private final SelfServiceSupportContacts supportContacts;
    private final JdbcTemplate jdbcTemplate;
    private final RoleRepository roleRepository;
    private final AppSelfServiceUserRepository appSelfServiceUserRepository;
    private final AppSelfServiceUserClientMappingRepository clientMappingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SelfServiceEventRecorder eventRecorder;
    private final SelfServiceContactConflictService contactConflictService;
    Supplier<LocalDateTime> clock = SelfServiceCodeChecks.systemClock();

    public SelfServiceSignupService(final SelfServiceRegistrationRepository registrationRepository, final ClientRepository clientRepository,
            final SelfServiceCodeSender codeSender, final SelfServicePasswordChangeService passwordChangeService,
            final PlatformPasswordEncoder platformPasswordEncoder, final SelfServiceSupportContacts supportContacts,
            final JdbcTemplate jdbcTemplate, final RoleRepository roleRepository,
            final AppSelfServiceUserRepository appSelfServiceUserRepository,
            final AppSelfServiceUserClientMappingRepository clientMappingRepository, final ApplicationEventPublisher eventPublisher,
            final SelfServiceEventRecorder eventRecorder, final SelfServiceContactConflictService contactConflictService) {
        this.registrationRepository = registrationRepository;
        this.clientRepository = clientRepository;
        this.codeSender = codeSender;
        this.passwordChangeService = passwordChangeService;
        this.platformPasswordEncoder = platformPasswordEncoder;
        this.supportContacts = supportContacts;
        this.jdbcTemplate = jdbcTemplate;
        this.roleRepository = roleRepository;
        this.appSelfServiceUserRepository = appSelfServiceUserRepository;
        this.clientMappingRepository = clientMappingRepository;
        this.eventPublisher = eventPublisher;
        this.eventRecorder = eventRecorder;
        this.contactConflictService = contactConflictService;
    }

    public record StartRequest(String accountNumber, String firstName, String lastName, String mobileNumber, String email, String password,
            String repeatPassword, String channel) {
    }

    @Transactional(noRollbackFor = SelfServiceCodeException.class)
    public Map<String, Object> start(final StartRequest request) {
        final String accountNumber = required(request.accountNumber(), "accountNumber");
        final String firstName = required(request.firstName(), "firstName");
        final String lastName = required(request.lastName(), "lastName");
        final String mobileNumber = required(request.mobileNumber(), "mobileNumber");
        final String email = required(request.email(), "email");
        if (!SelfServiceContactFormats.looksLikeEmail(email) || email.trim().length() > 100) {
            throw new IllegalArgumentException("Enter a valid email address. It becomes your username.");
        }
        final String repeat = request.repeatPassword() == null ? request.password() : request.repeatPassword();
        passwordChangeService.validateNewPassword(request.password(), repeat);
        final SelfServiceCodeSender.Channel channel = SelfServiceCodeSender.Channel.parse(request.channel(),
                SelfServiceCodeSender.Channel.SMS);

        // One answer for every mismatch, so the form cannot be used to find out which detail is wrong
        final Client client = clientRepository.getClientByAccountNumber(accountNumber.trim());
        if (client == null || !sameName(client.getFirstname(), firstName) || !sameName(client.getLastname(), lastName)
                || !SelfServiceContactFormats.samePhone(client.getMobileNo(), mobileNumber)) {
            throw new SelfServiceCodeException(400, "error.msg.signup.details.mismatch",
                    "These details do not match our records. Check them, or " + supportContacts.callInstruction() + ".");
        }
        if (!client.isActive()) {
            throw new SelfServiceCodeException(400, "error.msg.signup.client.not.active",
                    "Your membership is not active yet, so you cannot sign up. " + StringUtils.capitalize(supportContacts.callInstruction())
                            + ".");
        }
        final String username = SelfServiceContactFormats.normaliseEmail(email);
        checkClientAndUsernameFree(client, username);
        // A phone or email shared with another client could send codes or data to the wrong member: block and explain
        contactConflictService.checkSignup(client.getId(), client.getAccountNumber(), client.getMobileNo(), client.getEmailAddress(),
                username);

        final LocalDateTime now = clock.get();
        SelfServiceCodeChecks.checkCanSend(
                registrationRepository.findSentSince(client.getId(), SelfServiceRequestType.SIGNUP, now.minus(SEND_WINDOW)), now);

        // Starting again replaces any earlier sign-up for this client: its reference and code stop working
        registrationRepository.findOpenByClientAndType(client.getId(), SelfServiceRequestType.SIGNUP)
                .forEach(SelfServiceRegistration::close);

        final String registrationId = UUID.randomUUID().toString();
        final SelfServiceRegistration registration = SelfServiceRegistration.instance(client, client.getAccountNumber(),
                client.getFirstname(), client.getMiddlename(), client.getLastname(), client.getMobileNo(), username, null, registrationId,
                username, encodePassword(request.password()), SelfServiceRequestType.SIGNUP, now.plus(SIGNUP_VALIDITY));
        return sendCode(registration, client, channel, now);
    }

    @Transactional(noRollbackFor = SelfServiceCodeException.class)
    public Map<String, Object> resend(final String registrationId, final String channelValue) {
        final SelfServiceCodeSender.Channel channel = SelfServiceCodeSender.Channel.parse(channelValue, SelfServiceCodeSender.Channel.SMS);
        final LocalDateTime now = clock.get();
        final SelfServiceRegistration registration = openSignup(registrationId, now);
        final Client client = registration.getClient();
        checkStillAllowed(registration, client);
        SelfServiceCodeChecks.checkCanSend(
                registrationRepository.findSentSince(client.getId(), SelfServiceRequestType.SIGNUP, now.minus(SEND_WINDOW)), now);
        return sendCode(registration, client, channel, now);
    }

    /** Where the sign-up stands, for an app that comes back to the code screen. */
    @Transactional(readOnly = true)
    public Map<String, Object> resume(final String registrationId) {
        final LocalDateTime now = clock.get();
        final SelfServiceRegistration registration = openSignup(registrationId, now);
        final Client client = registration.getClient();
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("registrationId", registration.getExternalAuthorizationToken());
        body.put("channel", registration.getDeliveryChannel());
        body.put("destination",
                "EMAIL".equals(registration.getDeliveryChannel()) ? SelfServiceContactFormats.maskEmail(client.getEmailAddress())
                        : SelfServiceContactFormats.maskPhone(client.getMobileNo()));
        final boolean usable = registration.hasUsableCode(now, MAX_ATTEMPTS);
        body.put("codeLength", SelfServiceVerificationCode.LENGTH);
        body.put("codeActive", usable);
        body.put("codeExpiresInSeconds", usable ? SelfServiceCodeChecks.secondsBetween(now, registration.getCodeExpiresAt()) : 0);
        body.put("attemptsLeft", usable ? MAX_ATTEMPTS - registration.getFailedAttempts() : 0);
        body.put("resendAfterSeconds", registration.getLastSentOn() == null ? 0
                : SelfServiceCodeChecks.secondsBetween(now, registration.getLastSentOn().plus(RESEND_COOLDOWN)));
        return body;
    }

    /** Checks the code and creates the app user linked to the client. */
    @Transactional(noRollbackFor = SelfServiceCodeException.class)
    public Map<String, Object> verify(final String registrationId, final String code) {
        final LocalDateTime now = clock.get();
        final SelfServiceRegistration registration = openSignup(registrationId, now);
        SelfServiceCodeChecks.checkCode(registration, code, now, true);

        final Client client = registration.getClient();
        checkStillAllowed(registration, client);
        if (!client.isActive()) {
            registration.close();
            throw new SelfServiceCodeException(400, "error.msg.signup.client.not.active",
                    "Your membership is not active, so you cannot sign up. " + StringUtils.capitalize(supportContacts.callInstruction())
                            + ".");
        }

        contactConflictService.checkSignup(client.getId(), client.getAccountNumber(), client.getMobileNo(), client.getEmailAddress(),
                registration.getUsername());

        final AppSelfServiceUser appUser = createUser(registration, client);
        registration.close();
        registrationRepository.saveAndFlush(registration);
        publishUserCreated(appUser, registration);
        log.info("Mobile app user {} signed up for client {}", appUser.getId(), client.getId());

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", appUser.getId());
        body.put("username", appUser.getUsername());
        body.put("clientId", client.getId());
        return body;
    }

    private Map<String, Object> sendCode(final SelfServiceRegistration registration, final Client client,
            final SelfServiceCodeSender.Channel channel, final LocalDateTime now) {
        final String code = SelfServiceVerificationCode.generate();
        registration.issueCode(SelfServiceVerificationCode.hash(code), now, CODE_VALIDITY, SEND_WINDOW);
        // Only the phone and email on the client record: never contact details typed in the request
        final SelfServiceCodeSender.Delivery delivery = codeSender.send(SelfServiceCodeSender.Purpose.SIGNUP, code, client.getMobileNo(),
                client.getEmailAddress(), SmsText.recipientName(client.getLastname(), client.getFirstname()), channel,
                SelfServiceCodePolicy.codeValidityMinutes());
        registration.recordDelivery(delivery == null ? null : delivery.channel().name());
        registrationRepository.saveAndFlush(registration);
        if (delivery == null) {
            throw SelfServiceCodeChecks.deliveryFailed(supportContacts,
                    Map.of("registrationId", registration.getExternalAuthorizationToken()));
        }
        recordCodeSent(client, registration.getUsername(), delivery);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("registrationId", registration.getExternalAuthorizationToken());
        body.putAll(SelfServiceCodeChecks.codeSentFields(delivery));
        return body;
    }

    private SelfServiceRegistration openSignup(final String registrationId, final LocalDateTime now) {
        if (StringUtils.isBlank(registrationId)) {
            throw new IllegalArgumentException("registrationId is required");
        }
        final SelfServiceRegistration registration = registrationRepository.getRequestByExternalAuthorizationToken(registrationId.trim(),
                SelfServiceRequestType.SIGNUP);
        if (registration == null || registration.isConsumed() || registration.isExpired(now)) {
            throw new SelfServiceCodeException(404, "error.msg.signup.not.found",
                    "This sign-up has expired or was replaced by a newer one. Please start again.");
        }
        return registration;
    }

    /** The client must still have no app user and the username must still be free. */
    private void checkStillAllowed(final SelfServiceRegistration registration, final Client client) {
        try {
            checkClientAndUsernameFree(client, registration.getUsername());
        } catch (SelfServiceCodeException e) {
            registration.close();
            throw e;
        }
    }

    private void checkClientAndUsernameFree(final Client client, final String username) {
        final Integer links = jdbcTemplate.queryForObject("select count(*) from m_selfservice_user_client_mapping where client_id = ?",
                Integer.class, client.getId());
        if (links != null && links > 0) {
            throw new SelfServiceCodeException(409, "error.msg.signup.already.registered",
                    "You already have a mobile app account. Log in, or use Forgot password if you cannot remember it.");
        }
        final Integer users = jdbcTemplate.queryForObject("select count(*) from m_appselfservice_user where lower(username) = ?",
                Integer.class, username);
        if (users != null && users > 0) {
            throw new SelfServiceCodeException(409, "error.msg.signup.username.taken",
                    "This email is already used by another app account. Use a different email.");
        }
    }

    private AppSelfServiceUser createUser(final SelfServiceRegistration registration, final Client client) {
        final Role role = roleRepository.getRoleByName(SelfServiceApiConstants.SELF_SERVICE_USER_ROLE);
        if (role == null) {
            throw new RoleNotFoundException(SelfServiceApiConstants.SELF_SERVICE_USER_ROLE);
        }
        final Set<Role> roles = new HashSet<>();
        roles.add(role);
        final Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("DUMMY_ROLE_NOT_USED_OR_PERSISTED_TO_AVOID_EXCEPTION"));
        // The stored password is already encoded
        final User user = new User(registration.getUsername(), registration.getPassword(), authorities);
        final AppSelfServiceUser appUser = new AppSelfServiceUser(client.getOffice(), user, roles, registration.getEmail(),
                client.getFirstname(), client.getLastname(), null, true, true, new ArrayList<>(), null);
        appUser.updatePassword(registration.getPassword());
        appSelfServiceUserRepository.saveAndFlush(appUser);
        clientMappingRepository.saveClientUserMapping(appUser.getId(), client.getId());
        return appUser;
    }

    private void recordCodeSent(final Client client, final String username, final SelfServiceCodeSender.Delivery delivery) {
        eventRecorder.record(SelfServiceEventEntry.builder().username(username).clientId(client.getId())
                .officeId(client.getOffice() == null ? null : client.getOffice().getId()).eventType(SIGNUP_CODE_EVENT)
                .category("REGISTRATION").result(SelfServiceEventEntry.RESULT_SUCCESS).actor(SelfServiceEventEntry.ACTOR_CLIENT)
                .description("Sign-up code sent by " + delivery.channel().name() + " to " + delivery.destination()).build());
    }

    private void publishUserCreated(final AppSelfServiceUser appUser, final SelfServiceRegistration registration) {
        FineractPlatformTenant tenant = null;
        try {
            tenant = ThreadLocalContextUtil.getTenant();
        } catch (IllegalStateException ignored) {
            // No tenant on this thread
        }
        HashMap<BusinessDateType, LocalDate> businessDates = null;
        try {
            final HashMap<BusinessDateType, LocalDate> dates = ThreadLocalContextUtil.getBusinessDates();
            businessDates = dates == null ? null : new HashMap<>(dates);
        } catch (IllegalArgumentException ignored) {
            // No business dates on this thread
        }
        final SelfServiceNotificationEvent event = new SelfServiceNotificationEvent(this, SelfServiceNotificationEvent.Type.USER_CREATED,
                appUser.getId(), appUser.getFirstname(), appUser.getLastname(), appUser.getUsername(), registration.getEmail(),
                registration.getMobileNumber(), false, null, LocaleContextHolder.getLocale(), tenant, businessDates);
        final Runnable publish = () -> {
            try {
                eventPublisher.publishEvent(event);
            } catch (RuntimeException e) {
                log.warn("Failed to publish USER_CREATED for app user {}", appUser.getId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private String encodePassword(final String rawPassword) {
        return platformPasswordEncoder.encode(new BasicPasswordEncodablePlatformUser().setPassword(rawPassword));
    }

    private static boolean sameName(final String recorded, final String typed) {
        return recorded != null && recorded.trim().replaceAll("\\s+", " ").equalsIgnoreCase(typed.trim().replaceAll("\\s+", " "));
    }

    private static String required(final String value, final String name) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
