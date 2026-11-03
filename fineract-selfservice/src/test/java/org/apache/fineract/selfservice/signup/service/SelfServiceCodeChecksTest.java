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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRegistration;
import org.apache.fineract.selfservice.registration.domain.SelfServiceRequestType;
import org.apache.fineract.selfservice.signup.exception.SelfServiceCodeException;
import org.junit.jupiter.api.Test;

class SelfServiceCodeChecksTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 0);

    static SelfServiceRegistration request(final String code, final LocalDateTime sentAt) {
        final SelfServiceRegistration request = SelfServiceRegistration.instance(mock(Client.class), "000000001", "Jane", null, "Doe",
                "0785154434", "jane@example.com", null, "ref-1", "jane@example.com", "x", SelfServiceRequestType.SIGNUP, NOW.plusDays(1));
        if (code != null) {
            request.issueCode(SelfServiceVerificationCode.hash(code), sentAt, SelfServiceCodePolicy.CODE_VALIDITY,
                    SelfServiceCodePolicy.SEND_WINDOW);
        }
        return request;
    }

    @Test
    void aSecondCodeWithinAMinuteIsRefused() {
        assertThatThrownBy(() -> SelfServiceCodeChecks.checkCanSend(List.of(request("111111", NOW.minusSeconds(20))), NOW))
                .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(429);
                    assertThat(e.getCode()).isEqualTo("error.msg.code.resend.too.soon");
                    assertThat(e.getExtra()).containsEntry("retryAfterSeconds", 40L);
                });
        SelfServiceCodeChecks.checkCanSend(List.of(request("111111", NOW.minusSeconds(61))), NOW);
    }

    @Test
    void moreThanFiveSendsAnHourAreRefused() {
        final SelfServiceRegistration request = request("111111", NOW.minusMinutes(50));
        for (int i = 4; i >= 1; i--) {
            request.issueCode(SelfServiceVerificationCode.hash("111111"), NOW.minusMinutes(i * 10L), SelfServiceCodePolicy.CODE_VALIDITY,
                    SelfServiceCodePolicy.SEND_WINDOW);
        }
        assertThat(request.getSendCount()).isEqualTo(5);
        assertThatThrownBy(() -> SelfServiceCodeChecks.checkCanSend(List.of(request), NOW)).isInstanceOfSatisfying(
                SelfServiceCodeException.class, e -> assertThat(e.getCode()).isEqualTo("error.msg.code.send.limit"));
        // An hour after the first send the window starts again
        SelfServiceCodeChecks.checkCanSend(List.of(request), NOW.plusMinutes(11));
    }

    @Test
    void wrongCodesUseUpTries_andTheFifthThrowsTheCodeAway() {
        final SelfServiceRegistration request = request("482913", NOW.minusMinutes(1));
        for (int left = 4; left >= 1; left--) {
            final int expected = left;
            assertThatThrownBy(() -> SelfServiceCodeChecks.checkCode(request, "000000", NOW, true))
                    .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(400);
                        assertThat(e.getExtra()).containsEntry("attemptsLeft", expected);
                    });
        }
        assertThatThrownBy(() -> SelfServiceCodeChecks.checkCode(request, "000000", NOW, true))
                .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> assertThat(e.getStatus()).isEqualTo(410));
        // Even the right code no longer works
        assertThatThrownBy(() -> SelfServiceCodeChecks.checkCode(request, "482913", NOW, true))
                .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> assertThat(e.getStatus()).isEqualTo(410));
    }

    @Test
    void anExpiredCodeIsRefused_andANewCodeReplacesTheOldOne() {
        final SelfServiceRegistration request = request("482913", NOW.minusMinutes(11));
        assertThatThrownBy(() -> SelfServiceCodeChecks.checkCode(request, "482913", NOW, true))
                .isInstanceOfSatisfying(SelfServiceCodeException.class, e -> assertThat(e.getStatus()).isEqualTo(410));

        request.issueCode(SelfServiceVerificationCode.hash("551072"), NOW, SelfServiceCodePolicy.CODE_VALIDITY,
                SelfServiceCodePolicy.SEND_WINDOW);
        assertThatThrownBy(() -> SelfServiceCodeChecks.checkCode(request, "482913", NOW, true))
                .isInstanceOf(SelfServiceCodeException.class);
        SelfServiceCodeChecks.checkCode(request, "551072", NOW, true);
    }
}
