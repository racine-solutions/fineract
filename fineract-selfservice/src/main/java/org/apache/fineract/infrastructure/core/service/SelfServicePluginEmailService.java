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
package org.apache.fineract.infrastructure.core.service;

import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.data.SMTPCredentialsData;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Service responsible for sending emails in the self-service plugin. Overrides the default non-functional Fineract core
 * implementation.
 *
 * <p>
 * SMTP credentials are resolved directly from the Fineract core DB table ({@code c_external_service_properties}) via
 * {@link ExternalServicesPropertiesReadPlatformService}.
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "mifos.self.service.plugin.email.enabled", havingValue = "true", matchIfMissing = true)
public class SelfServicePluginEmailService implements PlatformEmailService {

    private final ExternalServicesPropertiesReadPlatformService externalServicesReadPlatformService;

    @Autowired
    public SelfServicePluginEmailService(final ExternalServicesPropertiesReadPlatformService externalServicesReadPlatformService) {
        this.externalServicesReadPlatformService = externalServicesReadPlatformService;
    }

    /**
     * Sends an email to a newly created user account with their initial credentials.
     *
     * @param organisationName
     *            the name of the organization
     * @param contactName
     *            the recipient's name
     * @param address
     *            the recipient's email address
     * @param username
     *            the allocated username
     * @param unencodedPassword
     *            the initial unencoded password
     */
    @Override
    public void sendToUserAccount(String organisationName, String contactName, String address, String username, String unencodedPassword) {

        final String subject = "Welcome " + contactName + " to " + organisationName;
        final String body = "You are receiving this email as your email account: " + address
                + " has being used to create a user account for an organisation named [" + organisationName + "] on Mifos.\n"
                + "You can login using the following credentials:\nusername: " + username + "\n" + "password: " + unencodedPassword + "\n"
                + "You must change this password upon first log in using Uppercase, Lowercase, number and character.\n"
                + "Thank you and welcome to the organisation.";

        final EmailDetail emailDetail = new EmailDetail(subject, body, address, contactName);
        sendDefinedEmail(emailDetail);
    }

    @Override
    public void sendForgotPasswordEmail(String organisationName, String contactName, String address, String username,
            String temporaryPassword) {
        final String subject = "Password Reset Request - " + organisationName;
        final String body = "Dear " + contactName + ",\n\n" + "You have requested to reset your password for your account on "
                + organisationName + ".\n\n" + "Your temporary password is: " + temporaryPassword + "\n\n"
                + "This temporary password will expire in 1 hour.\n\n" + "Please login with your username: " + username
                + " and this temporary password.\n" + "You will be required to change your password immediately after logging in.\n\n"
                + "If you did not request this password reset, please contact your system administrator.\n\n" + "Thank you.";

        final EmailDetail emailDetail = new EmailDetail(subject, body, address, contactName);
        sendDefinedEmail(emailDetail);
    }

    /**
     * Sends an HTML formatted email.
     *
     * @param emailDetails
     *            the email details containing recipient, subject, and HTML body
     * @throws PlatformEmailSendException
     *             if the email fails to send
     */
    public void sendFormattedEmail(EmailDetail emailDetails) {
        final SMTPCredentialsData smtpCredentialsData = this.externalServicesReadPlatformService.getSMTPCredentials();
        final JavaMailSenderImpl mailSender = configureMailSender(smtpCredentialsData);

        try {
            final MimeMessage mimeMessage = mailSender.createMimeMessage();
            final MimeMessageHelper message = new MimeMessageHelper(mimeMessage, "UTF-8");
            message.setFrom(smtpCredentialsData.getFromEmail());
            message.setTo(emailDetails.getAddress());
            message.setSubject(emailDetails.getSubject());
            message.setText(emailDetails.getBody(), true);
            log.info("Self Service Email :- {}", emailDetails.getBody());
            mailSender.send(mimeMessage);

        } catch (Exception e) {
            throw new PlatformEmailSendException(e);
        }
    }

    /**
     * Sends a plain text email.
     *
     * @param emailDetails
     *            the email details containing recipient, subject, and plain text body
     * @throws PlatformEmailSendException
     *             if the email fails to send
     */
    @Override
    public void sendDefinedEmail(EmailDetail emailDetails) {
        final SMTPCredentialsData smtpCredentialsData = this.externalServicesReadPlatformService.getSMTPCredentials();
        final JavaMailSenderImpl mailSender = configureMailSender(smtpCredentialsData);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(smtpCredentialsData.getFromEmail());
            message.setTo(emailDetails.getAddress());
            message.setSubject(emailDetails.getSubject());
            message.setText(emailDetails.getBody());
            log.info("Email details :- {} ", message.toString());
            mailSender.send(message);

        } catch (Exception e) {
            log.error("Error sending email details {}", e.getMessage());
            throw new PlatformEmailSendException(e);
        }
    }

    private JavaMailSenderImpl configureMailSender(SMTPCredentialsData smtpCredentialsData) {
        final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtpCredentialsData.getHost());

        String portStr = smtpCredentialsData.getPort();
        int port = 587;
        if (portStr != null && !portStr.isEmpty()) {
            try {
                int parsed = Integer.parseInt(portStr);
                if (parsed >= 1 && parsed <= 65535) {
                    port = parsed;
                } else {
                    log.warn("SMTP port '{}' out of valid range (1-65535), using default 587", portStr);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid SMTP port '{}', using default 587", portStr);
            }
        }
        mailSender.setPort(port);

        mailSender.setUsername(smtpCredentialsData.getUsername());
        mailSender.setPassword(smtpCredentialsData.getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.debug", "true");

        props.put("mail.smtp.starttls.enable", "true");

        props.put("mail.smtp.socketFactory.port", port);
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.socketFactory.fallback", "true");

        return mailSender;
    }
}
