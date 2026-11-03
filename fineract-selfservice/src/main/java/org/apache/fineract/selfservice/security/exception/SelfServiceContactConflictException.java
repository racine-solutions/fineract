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
package org.apache.fineract.selfservice.security.exception;

import java.util.List;
import lombok.Getter;
import org.springframework.security.authentication.AccountStatusException;

/**
 * The member's phone or email is also on another client's record. Mobile app access is blocked until staff correct the
 * records; the reference lets the member and staff find the case.
 */
@Getter
public class SelfServiceContactConflictException extends AccountStatusException {

    private final String reference;
    private final transient List<String> supportPhoneNumbers;

    public SelfServiceContactConflictException(final String message, final String reference, final List<String> supportPhoneNumbers) {
        super(message);
        this.reference = reference;
        this.supportPhoneNumbers = supportPhoneNumbers;
    }
}
