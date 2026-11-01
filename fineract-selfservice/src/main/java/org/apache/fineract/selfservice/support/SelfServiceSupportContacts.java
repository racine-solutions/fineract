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
package org.apache.fineract.selfservice.support;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.springframework.stereotype.Service;

/**
 * The phone numbers mobile app clients call for help (setting {@code self-service-support-phone-numbers}, comma
 * separated). Shown when login is refused (suspended, closed, locked) and in the messages sent when staff change a
 * client's app access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfServiceSupportContacts {

    static final String FALLBACK = "contact your branch";

    private final ConfigurationDomainService configurationDomainService;

    /** The numbers in the order staff entered them; empty when none are set. */
    public List<String> phoneNumbers() {
        final String value;
        try {
            value = configurationDomainService.retrieveSelfServiceSupportPhoneNumbers();
        } catch (RuntimeException e) {
            log.warn("Could not read the mobile app support phone numbers", e);
            return List.of();
        }
        if (StringUtils.isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;\\n]")).map(String::trim).filter(StringUtils::isNotBlank).distinct().toList();
    }

    /** "call +256 700 000 001 or +256 700 000 002", or "contact your branch" when no numbers are set. */
    public String callInstruction() {
        final List<String> numbers = phoneNumbers();
        if (numbers.isEmpty()) {
            return FALLBACK;
        }
        if (numbers.size() == 1) {
            return "call " + numbers.get(0);
        }
        return "call " + String.join(", ", numbers.subList(0, numbers.size() - 1)) + " or " + numbers.get(numbers.size() - 1);
    }
}
