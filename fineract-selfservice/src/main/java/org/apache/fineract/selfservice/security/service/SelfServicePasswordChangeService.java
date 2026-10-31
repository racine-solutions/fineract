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
package org.apache.fineract.selfservice.security.service;

import com.google.gson.JsonElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUserRepository;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicy;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.apache.fineract.useradministration.exception.UnAuthenticatedUserException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the password of the mobile app user who is logged in, and of nobody else. Also completes a forced password
 * change: it works for a user who must set a new password, clears that requirement, and ends the user's sessions so
 * they log in again with the new password.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfServicePasswordChangeService {

    static final String PASSWORD = "password";
    static final String REPEAT_PASSWORD = "repeatPassword";

    private final AppSelfServiceUserRepository appSelfServiceUserRepository;
    private final PlatformPasswordEncoder platformPasswordEncoder;
    private final PasswordValidationPolicyRepository passwordValidationPolicyRepository;
    private final SelfServiceAuthenticationTokenService tokenService;
    private final FromJsonHelper fromApiJsonHelper;
    private final PasswordEncoder passwordMatcher = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Transactional
    public Long changeOwnPassword(final String apiRequestBodyAsJson) {
        final AppSelfServiceUser user = appSelfServiceUserRepository.findById(loggedInUserId())
                .orElseThrow(UnAuthenticatedUserException::new);
        if (user.getStatus() != SelfServiceUserStatus.ACTIVE) {
            throw new IllegalArgumentException("Your password cannot be changed. Contact your branch.");
        }

        final JsonElement element = fromApiJsonHelper.parse(apiRequestBodyAsJson);
        final String password = fromApiJsonHelper.extractStringNamed(PASSWORD, element);
        final String repeatPassword = fromApiJsonHelper.extractStringNamed(REPEAT_PASSWORD, element);
        if (StringUtils.isBlank(password) || StringUtils.isBlank(repeatPassword)) {
            throw new IllegalArgumentException("Enter the new password twice.");
        }
        if (!password.equals(repeatPassword)) {
            throw new IllegalArgumentException("The two passwords do not match.");
        }
        final PasswordValidationPolicy policy = passwordValidationPolicyRepository.findActivePasswordValidationPolicy();
        if (policy != null && StringUtils.isNotBlank(policy.getRegex()) && !password.matches(policy.getRegex())) {
            throw new IllegalArgumentException("The password does not meet the password rules: " + policy.getDescription());
        }
        if (passwordMatcher.matches(password, user.getPassword())) {
            throw new GeneralPlatformDomainRuleException("error.msg.selfservice.password.unchanged",
                    "The new password must be different from the current one.");
        }

        // The encoder reads the raw password from the user, then the encoded value is stored
        user.updatePassword(password);
        user.updatePassword(platformPasswordEncoder.encode(user));
        user.updatePasswordResetRequired(false);
        appSelfServiceUserRepository.saveAndFlush(user);
        tokenService.revokeAllSessions(user.getId());
        log.info("Mobile app user {} changed their password", user.getId());
        return user.getId();
    }

    /** The id of the app user making the request, read straight from the session (it may still have to renew). */
    private static Long loggedInUserId() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppSelfServiceUser user && user.getId() != null) {
            return user.getId();
        }
        throw new UnAuthenticatedUserException();
    }
}
