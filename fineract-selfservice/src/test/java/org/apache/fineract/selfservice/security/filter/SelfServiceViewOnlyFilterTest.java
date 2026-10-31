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
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.List;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SelfServiceViewOnlyFilterTest {

    private final SelfServiceViewOnlyFilter filter = new SelfServiceViewOnlyFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void viewOnlyUser_writeRequest_isRefusedWith403() throws Exception {
        signIn(true);
        MockHttpServletResponse response = run("POST", "/fineract-provider/api/v1/self/accounttransfers");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(SelfServiceViewOnlyFilter.ERROR_CODE);
    }

    @Test
    void viewOnlyUser_readRequest_isAllowed() throws Exception {
        signIn(true);
        assertThat(run("GET", "/fineract-provider/api/v1/self/savingsaccounts").getStatus()).isEqualTo(200);
    }

    @Test
    void viewOnlyUser_logoutAndSessionRefresh_areAllowed() throws Exception {
        signIn(true);
        assertThat(run("POST", "/fineract-provider/api/v1/self/authentication/logout").getStatus()).isEqualTo(200);
        assertThat(run("POST", "/fineract-provider/api/v1/self/authentication/token").getStatus()).isEqualTo(200);
    }

    @Test
    void viewOnlyUser_otherWrites_areRefused() throws Exception {
        signIn(true);
        for (String path : List.of("/api/v1/self/beneficiaries/tpt", "/api/v1/self/loans", "/api/v1/self/savingsaccounts",
                "/api/v1/self/user")) {
            assertThat(run("POST", path).getStatus()).as(path).isEqualTo(403);
        }
        assertThat(run("PUT", "/api/v1/self/user").getStatus()).isEqualTo(403);
        assertThat(run("DELETE", "/api/v1/self/beneficiaries/tpt/3").getStatus()).isEqualTo(403);
    }

    @Test
    void activeUser_writeRequest_isAllowed() throws Exception {
        signIn(false);
        assertThat(run("POST", "/fineract-provider/api/v1/self/accounttransfers").getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousRequest_isAllowed() throws Exception {
        assertThat(run("POST", "/fineract-provider/api/v1/self/registration").getStatus()).isEqualTo(200);
    }

    private static void signIn(final boolean viewOnly) {
        AppSelfServiceUser user = mock(AppSelfServiceUser.class);
        when(user.isViewOnly()).thenReturn(viewOnly);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private MockHttpServletResponse run(final String method, final String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }
}
