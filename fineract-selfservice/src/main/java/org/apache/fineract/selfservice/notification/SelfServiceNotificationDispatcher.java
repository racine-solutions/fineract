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
package org.apache.fineract.selfservice.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SelfServiceNotificationDispatcher {

    private final EmailNotificationSender emailSender;
    private final SmsNotificationSender smsSender;
    private final WhatsAppNotificationSender whatsappSender;
    private final InAppNotificationSender inAppSender;

    public void sendEmail(String to, String subject, String content) {
        log.info("Dispatching Email to {}: {}", to, subject);
        emailSender.send(to, subject, content);
    }

    public void sendSms(String to, String content) {
        log.info("Dispatching SMS to {}: {}", to, content);
        smsSender.send(to, content);
    }

    public void sendWhatsApp(String to, String content) {
        log.info("Dispatching WhatsApp to {}: {}", to, content);
        whatsappSender.send(to, content);
    }

    public void sendInApp(Long userId, String title, String content) {
        log.info("Dispatching In-App notification to user {}: {}", userId, title);
        inAppSender.send(userId, title, content);
    }
}

// ==========================================
// --- Default Implementations ---
// ==========================================

@Slf4j
class DefaultEmailNotificationSender implements EmailNotificationSender {

    @Override
    public void send(String to, String subject, String content) {
        log.info("Default Email Sender - To: {}, Subject: {}, Content: {}", to, subject, content);
    }
}

@Slf4j
class DefaultSmsNotificationSender implements SmsNotificationSender {

    @Override
    public void send(String to, String content) {
        log.info("Default SMS Sender - To: {}, Content: {}", to, content);
    }
}

@Slf4j
class DefaultWhatsAppNotificationSender implements WhatsAppNotificationSender {

    @Override
    public void send(String to, String content) {
        log.info("Default WhatsApp Sender - To: {}, Content: {}", to, content);
    }
}

@Slf4j
class DefaultInAppNotificationSender implements InAppNotificationSender {

    @Override
    public void send(Long userId, String title, String content) {
        log.info("Default In-App Sender - UserId: {}, Title: {}, Content: {}", userId, title, content);
    }
}
