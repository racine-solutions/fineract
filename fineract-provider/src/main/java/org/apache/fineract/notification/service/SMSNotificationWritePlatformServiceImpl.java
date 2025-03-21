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
package org.apache.fineract.notification.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationProperty;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.notification.data.SmsMessageData;
import org.apache.fineract.notification.data.SmsNotificationData;
import org.apache.fineract.notification.data.SmsTypeEnum;
import org.apache.fineract.notification.data.SmsUserData;
import org.apache.fineract.notification.domain.SmsNotificationAccount;
import org.apache.fineract.notification.domain.SmsNotificationAccountMessage;
import org.apache.fineract.notification.domain.SmsNotificationAccountRepository;
import org.apache.fineract.notification.domain.SmsNotificationMessageRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

@RequiredArgsConstructor
@Slf4j
public class SMSNotificationWritePlatformServiceImpl implements SmsNotificationWritePlatformService {

    @Autowired
    private Environment env;
    @Autowired
    private final GlobalConfigurationRepositoryWrapper configurationRepositoryWrapper;
    private final SmsNotificationAccountRepository smsNotificationAccountRepository;
    private final SmsNotificationMessageRepository smsNotificationMessageRepository;
    public static final String FORM_URL_CONTENT_TYPE = "application/json";

    @Override
    public void sendSms(SmsMessageData messageData, SmsTypeEnum smsType) {

        final GlobalConfigurationProperty property = this.configurationRepositoryWrapper
                .findOneByNameWithNotFoundDetection(GlobalConfigurationConstants.ENABLE_SMS_NOTIFICATIONS);

        Optional<SmsNotificationAccount> smsAccount = smsNotificationAccountRepository.findById(1L);

        if (smsAccount.isPresent() && smsAccount.get().getIsActive()) {
            log.info("SMS Account found with total balance: {}", smsAccount.get().getSmsTotalBalance());
        } else {
            log.info("SMS Account not found or is not active for this tenant :- " + ThreadLocalContextUtil.getTenant().getName());
            return;
        }

        if (property.isEnabled()) {
            Gson gson = new GsonBuilder().create();

            SmsUserData userData = new SmsUserData(getConfigProperty("sms.username"), getConfigProperty("sms.password"));
            messageData.setSenderid(getConfigProperty("sms.sender.id"));
            List<SmsMessageData> msgdata = new ArrayList<>();
            msgdata.add(messageData);
            SmsNotificationData smsNotificationData = new SmsNotificationData();

            smsNotificationData.setMethod(getConfigProperty("sms.method"));
            smsNotificationData.setUserdata(userData);
            smsNotificationData.setMsgdata(msgdata);

            String notificationObj = gson.toJson(smsNotificationData);
            log.info("SMS Message is object :=> " + notificationObj);

            HttpUrl.Builder urlBuilder = HttpUrl.parse(getConfigProperty("sms.url")).newBuilder();
            String url = urlBuilder.build().toString();

            log.info("SMS URL :=>" + url);
            OkHttpClient client = new OkHttpClient();
            Response response = null;

            RequestBody formBody = RequestBody.create(MediaType.parse(FORM_URL_CONTENT_TYPE), notificationObj);

            Request request = new Request.Builder().url(url).post(formBody).build();

            List<Throwable> exceptions = new ArrayList<>();
            String resObject = null;
            try {
                response = client.newCall(request).execute();
                resObject = response.body().string();

                if (response.isSuccessful()) {

                    log.info("Sms Message Response :=>" + resObject);

                } else {
                    log.error("Failed to deliver sms message notification :" + resObject);

                    handleAPIIntegrityIssues(resObject);

                }
            } catch (Exception e) {
                log.error("Posting sms notification has failed " + e);
                exceptions.add(e);
            }
            assert response != null;
            cacheSmsNotification(messageData, smsType, response.isSuccessful(), resObject);

        } else {
            log.info("** SMS Notification is disabled for this Tenant :-> " + ThreadLocalContextUtil.getTenant().getName());
        }
    }

    private void cacheSmsNotification(SmsMessageData messageData, SmsTypeEnum smsType, Boolean hasPassed, String response) {
        SmsNotificationAccountMessage message = new SmsNotificationAccountMessage();

        message.setMessage(messageData.getMessage());
        message.setNumber(messageData.getNumber());
        message.setSenderid(messageData.getSenderid());
        message.setSmsResponse(response);
        message.setHasPassed(hasPassed);
        message.setSmsTypeEnum(smsType);
        smsNotificationMessageRepository.saveAndFlush(message);
    }

    private String getConfigProperty(String propertyName) {
        return this.env.getProperty(propertyName);
    }

    private void handleAPIIntegrityIssues(String httpResponse) {
        throw new PlatformDataIntegrityException(httpResponse, httpResponse);
    }

    @Override
    public void processLoanSmsNotification(Loan loan, SmsTypeEnum smsType, LoanTransaction transaction) {
        String clientName = loan.client().getDisplayName().replace(" ", "");
        String mobileNo = loan.client().getMobileNo();
        String message = null;
        String messageId = null;
        switch (smsType) {
            case LOAN_SUBMISSION:
                message = String.format(
                        "Dear %s, your loan application is under review Ref:%s. We'll notify you once it's done. Thank you!", clientName,
                        loan.getAccountNumber());
                messageId = String.format("LOAN-SUBMISSION-%s", loan.getId());
            break;
            case LOAN_APPROVAL:
                message = String.format("Dear %s, great news! Your loan Ref:%s of %s %s has been approved successfully", clientName,
                        loan.getAccountNumber(), loan.getCurrencyCode(), loan.getApprovedPrincipal().setScale(2, RoundingMode.HALF_UP));
                messageId = String.format("LOAN-APPROVAL-%s", loan.getId());
            break;
            case LOAN_DISBURSEMENT:
                message = String.format("Dear %s loan #%s application for %s %s has been granted successfully Date: %s ", clientName,
                        loan.getAccountNumber(), loan.getCurrencyCode(), loan.getProposedPrincipal().setScale(2, RoundingMode.HALF_UP),
                        DateUtils.getBusinessLocalDate());
                messageId = String.format("LOAN-DISBURSEMENT-%s", loan.getId());
            break;
            case LOAN_REJECTED:
                message = String.format("Dear %s, unfortunately, your loan application #%s for %s %s was not approved.", clientName,
                        loan.getAccountNumber(), loan.getCurrencyCode(), loan.getProposedPrincipal().setScale(2, RoundingMode.HALF_UP));
                messageId = String.format("LOAN-REJECTED-%s", loan.getId());
            break;
            case LOAN_REPAYMENT:
                message = String.format("A Loan Repayment of %s %s was made to your #%s Ref:%s Date:%s ", loan.getCurrencyCode(),
                        transaction.getAmount().setScale(2, RoundingMode.HALF_UP), loan.getAccountNumber(), transaction.getId(),
                        transaction.getTransactionDate());
                messageId = String.format("LOAN-REPAYMENT-%s", transaction.getId());
            break;
            default:
                log.info("No sms type found to process a notification");
                return;

        }

        if (mobileNo != null) {
            sendSms(new SmsMessageData(mobileNo, message, messageId), smsType);
        } else {
            log.info("No mobile number found for client :-> %s    --->  %s", clientName, ThreadLocalContextUtil.getTenant().getName());
        }
    }

    @Override
    public void processSavingsSmsNotification(SavingsAccount savingsAccount, SmsTypeEnum smsType, SavingsAccountTransaction transaction) {
        String clientName = savingsAccount.getClient().getDisplayName().replace(" ", "");
        String mobileNo = savingsAccount.getClient().getMobileNo();
        String message = null;
        String messageId = null;
        switch (smsType) {
            case SAVINGS_DEPOSIT:
                message = String.format("A deposit of %s %s was made to your account #%s Ref:%s Date:%s ",
                        savingsAccount.getCurrency().getCode(), transaction.getAmount().setScale(2, RoundingMode.HALF_UP),
                        savingsAccount.getAccountNumber(), transaction.getId(), transaction.getTransactionDate());
                messageId = String.format("SAVINGS-DEPOSIT-%s", transaction.getId());
            break;
            case SAVINGS_WITHDRAW:
                message = String.format("A withdraw of %s %s was made on your account #%s Ref:%s Date:%s ",
                        savingsAccount.getCurrency().getCode(), transaction.getAmount().setScale(2, RoundingMode.HALF_UP),
                        savingsAccount.getAccountNumber(), transaction.getId(), transaction.getTransactionDate());
                messageId = String.format("SAVINGS-DEPOSIT-%s", transaction.getId());
            break;
            default:
                log.info("No sms type found to process a notification");
                return;
        }

        if (mobileNo != null) {
            sendSms(new SmsMessageData(mobileNo, message, messageId), smsType);
        } else {
            log.info("No mobile number found for client :-> %s    --->  %s", clientName, ThreadLocalContextUtil.getTenant().getName());
        }
    }

}
