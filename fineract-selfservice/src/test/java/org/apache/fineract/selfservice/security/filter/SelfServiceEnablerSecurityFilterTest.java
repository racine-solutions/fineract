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
package org.apache.fineract.selfservice.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.selfservice.security.service.SelfServiceSwitch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SelfServiceEnablerSecurityFilterTest {

    private SelfServiceSwitch selfServiceSwitch;
    private SelfServiceEnablerSecurityFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default Tenant", "UTC", null));
        selfServiceSwitch = mock(SelfServiceSwitch.class);
        filter = new SelfServiceEnablerSecurityFilter(selfServiceSwitch);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void switchedOff_refusesEvenLoginWith503AndEndsSessions() throws Exception {
        when(selfServiceSwitch.isOn()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/fineract-provider/api/v1/self/authentication");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("error.msg.selfservice.disabled");
        verify(selfServiceSwitch).refusedWhileOff();
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void switchedOn_letsTheRequestThrough() throws Exception {
        when(selfServiceSwitch.isOn()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fineract-provider/api/v1/self/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(selfServiceSwitch).allowedWhileOn();
    }

    @Test
    void withoutTenant_theSwitchIsNotRead() throws Exception {
        ThreadLocalContextUtil.reset();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fineract-provider/api/v1/self/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(selfServiceSwitch, never()).isOn();
    }
}
