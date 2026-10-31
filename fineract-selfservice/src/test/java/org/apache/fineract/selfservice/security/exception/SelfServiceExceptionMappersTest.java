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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.apache.fineract.selfservice.security.data.SelfServiceAuthenticatedUserData;
import org.apache.fineract.selfservice.support.SelfServiceSupportContacts;
import org.apache.fineract.selfservice.useradministration.domain.AppSelfServiceUser;
import org.apache.fineract.selfservice.useradministration.domain.SelfServiceUserStatus;
import org.junit.jupiter.api.Test;

class SelfServiceExceptionMappersTest {

    private static SelfServiceSupportContacts supportContacts() {
        SelfServiceSupportContacts contacts = mock(SelfServiceSupportContacts.class);
        when(contacts.callInstruction()).thenReturn("call +256 774 067 967");
        when(contacts.phoneNumbers()).thenReturn(List.of("+256 774 067 967"));
        return contacts;
    }

    @Test
    void refusedLogin_saysWhyPerAccountState() {
        assertThat(disabledBody(SelfServiceUserStatus.SUSPENDED)).contains("error.msg.selfservice.user.suspended")
                .contains("For help, call +256 774 067 967.").contains("supportPhoneNumbers");
        assertThat(disabledBody(SelfServiceUserStatus.CLOSED)).contains("error.msg.selfservice.user.closed");
        assertThat(disabledBody(SelfServiceUserStatus.PENDING)).contains("error.msg.selfservice.user.pending");
    }

    @Test
    void lockedLogin_is401WithLockedCode() {
        Response response = new SelfServiceLockedExceptionMapper(supportContacts())
                .toResponse(new SelfServiceLockedException("locked", null));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat((String) response.getEntity()).contains("error.msg.selfservice.user.locked")
                .contains("To unlock it, call +256 774 067 967.").contains("\"supportPhoneNumbers\":[\"+256 774 067 967\"]");
    }

    @Test
    void forcedPasswordChange_carriesTheFlagAndSession() {
        SelfServiceAuthenticatedUserData data = new SelfServiceAuthenticatedUserData().setUsername("jdoe").setUserId(7L)
                .setBase64EncodedAuthenticationKey("access").setRefreshToken("refresh").setShouldRenewPassword(true)
                .setSessionTimeoutMinutes(15);

        Response response = new SelfServicePasswordResetRequiredExceptionMapper()
                .toResponse(new SelfServicePasswordResetRequiredException(data));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat((String) response.getEntity()).contains("error.msg.selfservice.password.reset.required")
                .contains("\"shouldRenewPassword\":true").contains("\"base64EncodedAuthenticationKey\":\"access\"")
                .contains("\"sessionTimeoutMinutes\":15");
    }

    @Test
    void catchAll_keepsRealStatusesAndHidesInternals() {
        ThrowableMapper mapper = new ThrowableMapper();

        assertThat(mapper.toResponse(new NotFoundException()).getStatus()).isEqualTo(404);
        assertThat(mapper.toResponse(new IllegalArgumentException("Refresh token is missing.")).getStatus()).isEqualTo(400);
        Response unexpected = mapper.toResponse(new NullPointerException("secret internal detail"));
        assertThat(unexpected.getStatus()).isEqualTo(500);
        assertThat((String) unexpected.getEntity()).doesNotContain("secret internal detail");
    }

    private static String disabledBody(final SelfServiceUserStatus status) {
        AppSelfServiceUser user = mock(AppSelfServiceUser.class);
        when(user.getStatus()).thenReturn(status);
        Response response = new SelfServiceDisabledExceptionMapper(supportContacts())
                .toResponse(new SelfServiceDisabledException("disabled", user));
        assertThat(response.getStatus()).isEqualTo(401);
        return (String) response.getEntity();
    }
}
