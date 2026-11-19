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
package org.apache.fineract.racine.campaigns.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.racine.campaigns.service.EmailEventService;
import org.junit.jupiter.api.Test;

class BrevoWebhookApiResourceTest {

    private static final String EVENT = "{\"event\":\"delivered\",\"message-id\":\"<x>\"}";

    private final EmailEventService events = mock(EmailEventService.class);

    @Test
    void theRightTokenAppliesTheEvent() {
        when(events.apply(any())).thenReturn(1);
        assertThat(new BrevoWebhookApiResource(events, "s3cret").receive("s3cret", EVENT).getStatus()).isEqualTo(200);
        verify(events).apply(any());
    }

    @Test
    void aWrongOrMissingTokenIsRefused() {
        final BrevoWebhookApiResource resource = new BrevoWebhookApiResource(events, "s3cret");
        assertThat(resource.receive("guess", EVENT).getStatus()).isEqualTo(403);
        assertThat(resource.receive(null, EVENT).getStatus()).isEqualTo(403);
        verify(events, never()).apply(any());
    }

    @Test
    void everythingIsRefusedWhileNoTokenIsSet() {
        assertThat(new BrevoWebhookApiResource(events, " ").receive("", EVENT).getStatus()).isEqualTo(403);
        verify(events, never()).apply(any());
    }

    @Test
    void aBodyThatIsNotJsonIsABadRequest() {
        assertThat(new BrevoWebhookApiResource(events, "s3cret").receive("s3cret", "{not json").getStatus()).isEqualTo(400);
    }
}
