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

import org.apache.fineract.selfservice.security.exception.SelfServiceDisabledException;
import org.apache.fineract.selfservice.security.exception.SelfServiceLockedException;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

public class SelfServiceUserDetailsChecker implements UserDetailsChecker {

    private final UserDetailsChecker delegate;
    private final SelfServiceContactConflictService contactConflictService;

    public SelfServiceUserDetailsChecker(UserDetailsChecker delegate) {
        this(delegate, null);
    }

    public SelfServiceUserDetailsChecker(UserDetailsChecker delegate, SelfServiceContactConflictService contactConflictService) {
        this.delegate = delegate;
        this.contactConflictService = contactConflictService;
    }

    /**
     * The account-status checks applied at login and on every later request: disabled, locked, account expired and
     * credentials expired.
     */
    public static SelfServiceUserDetailsChecker accountStatusChecker() {
        return new SelfServiceUserDetailsChecker(new AccountStatusUserDetailsChecker());
    }

    /**
     * The account-status checks, then the shared-contact check: a member whose phone or email is also on another
     * client's record is refused at login and on every later request until staff correct the records.
     */
    public static SelfServiceUserDetailsChecker accountStatusChecker(SelfServiceContactConflictService contactConflictService) {
        return new SelfServiceUserDetailsChecker(new AccountStatusUserDetailsChecker(), contactConflictService);
    }

    @Override
    public void check(UserDetails user) {
        try {
            delegate.check(user);
        } catch (LockedException ex) {
            if (user instanceof AppSelfServiceUser) {
                throw new SelfServiceLockedException(ex.getMessage(), (AppSelfServiceUser) user);
            }
            throw ex;
        } catch (DisabledException ex) {
            if (user instanceof AppSelfServiceUser) {
                throw new SelfServiceDisabledException(ex.getMessage(), (AppSelfServiceUser) user);
            }
            throw ex;
        }
        if (contactConflictService != null && user instanceof AppSelfServiceUser appUser) {
            contactConflictService.checkUser(appUser);
        }
    }
}
