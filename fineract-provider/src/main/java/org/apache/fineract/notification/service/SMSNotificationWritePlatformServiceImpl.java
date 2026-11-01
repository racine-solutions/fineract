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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.external.repository.SmsEventConfigurationRepository;
import org.apache.fineract.infrastructure.event.external.repository.domain.SmsEventConfiguration;
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
    private final SmsEventConfigurationRepository repository;

    private static final Map<SmsTypeEnum, String> SMS_TYPE_TO_EVENT_MAP = new EnumMap<>(SmsTypeEnum.class);

    static {
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.LOAN_SUBMISSION, "LoanAccountCreationEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.LOAN_APPROVAL, "LoanAccountApprovalEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.LOAN_DISBURSEMENT, "LoanAccountDisbursementEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.LOAN_REJECTED, "LoanAccountRejectedEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.LOAN_REPAYMENT, "LoanAccountRepaymentEvent");
        // Savings
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SAVINGS_DEPOSIT, "SavingsAccountDepositEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SAVINGS_WITHDRAW, "SavingsAccountWithdrawEvent");
    }

    @Override
    public void sendSms(SmsMessageData messageData, SmsTypeEnum smsType) {
        deliver(messageData, smsType, false, true);
    }

    @Override
    public boolean sendSmsNow(SmsMessageData messageData, SmsTypeEnum smsType) {
        return deliver(messageData, smsType, true, false);
    }

    /**
     * Sends one SMS through the gateway.
     *
     * @param sensitive
     *            the text carries a secret (one-time code), so it is never written to the log or the message history
     * @param failOnNoCredit
     *            keep the old behaviour of rejecting the call when the SMS account has no credit
     * @return true only when the gateway accepted the message
     */
    private boolean deliver(SmsMessageData messageData, SmsTypeEnum smsType, boolean sensitive, boolean failOnNoCredit) {

        final GlobalConfigurationProperty property = this.configurationRepositoryWrapper
                .findOneByNameWithNotFoundDetection(GlobalConfigurationConstants.ENABLE_SMS_NOTIFICATIONS);

        Optional<SmsNotificationAccount> smsAccount = smsNotificationAccountRepository.findById(1L);

        if (smsAccount.isPresent() && smsAccount.get().getIsActive()) {
            log.info("SMS Account found with total balance: {}", smsAccount.get().getSmsTotalBalance());
        } else {
            log.info("SMS Account not found or is not active for this tenant :- " + ThreadLocalContextUtil.getTenant().getName());
            return false;
        }

        final SmsNotificationAccount account = smsAccount.get();

        if (account.getSmsTotalBalance() == null || account.getSmsTotalBalance() <= 0) {
            log.warn("Insufficient SMS credit balance ({}) for tenant :- {}. Message not sent.", account.getSmsTotalBalance(),
                    ThreadLocalContextUtil.getTenant().getName());
            if (!failOnNoCredit) {
                return false;
            }
            throw new PlatformApiDataValidationException("error.msg.sms.insufficient.balance",
                    "Insufficient SMS credit balance. Please top up your SMS account before sending messages.", "smsTotalBalance");
        }

        if (!property.isEnabled()) {
            log.info("** SMS Notification is disabled for this Tenant :-> " + ThreadLocalContextUtil.getTenant().getName());
            return false;
        }

        Gson gson = new GsonBuilder().create();

        SmsUserData userData = new SmsUserData(getConfigProperty("sms.username"), getConfigProperty("sms.password"));
        messageData.setSenderid(getConfigProperty("sms.sender.id"));
        List<SmsMessageData> msgdata = new ArrayList<>();
        msgdata.add(messageData);
        SmsNotificationData smsNotificationData = new SmsNotificationData();

        smsNotificationData.setMethod(getConfigProperty("sms.method"));
        smsNotificationData.setUserdata(userData);
        smsNotificationData.setMsgdata(msgdata);

        // The payload holds the gateway credentials and the message text: never log it.
        String notificationObj = gson.toJson(smsNotificationData);
        log.info("Sending {} SMS to {}", smsType, maskNumber(messageData.getNumber()));

        HttpUrl.Builder urlBuilder = HttpUrl.parse(getConfigProperty("sms.url")).newBuilder();
        String url = urlBuilder.build().toString();

        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = RequestBody.create(MediaType.parse(FORM_URL_CONTENT_TYPE), notificationObj);

        Request request = new Request.Builder().url(url).post(formBody).build();

        try (Response response = client.newCall(request).execute()) {
            // Read the body exactly once – ResponseBody can only be consumed once.
            String resObject = response.body() != null ? response.body().string() : "";
            boolean accepted = response.isSuccessful() && isGatewayAccepted(resObject);

            if (accepted) {
                log.info("Sms Message Response :=>" + resObject);
                debitSmsAccount(account);
            } else {
                log.error("Failed to deliver sms message notification (HTTP {}): {}", response.code(), resObject);
            }

            cacheSmsNotification(messageData, smsType, accepted, resObject, sensitive);
            return accepted;
        } catch (Exception e) {
            // SMS delivery failures must never propagate to callers (e.g. loan repayment).
            log.error("Posting sms notification has failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * egosms answers HTTP 200 even when it refuses a message; the JSON "Status" field says whether it was accepted. A
     * body without that field is treated as accepted so other gateways keep working.
     */
    static boolean isGatewayAccepted(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                return true;
            }
            JsonObject json = parsed.getAsJsonObject();
            for (String key : json.keySet()) {
                if ("status".equalsIgnoreCase(key) && json.get(key).isJsonPrimitive()) {
                    return "ok".equalsIgnoreCase(json.get(key).getAsString().trim());
                }
            }
            return true;
        } catch (RuntimeException e) {
            return true;
        }
    }

    static String maskNumber(String number) {
        if (number == null || number.length() < 4) {
            return "***";
        }
        return "***" + number.substring(number.length() - 3);
    }

    private void debitSmsAccount(SmsNotificationAccount account) {
        int newBalance = account.getSmsTotalBalance() - 1;
        account.setSmsTotalBalance(newBalance);
        smsNotificationAccountRepository.saveAndFlush(account);
        log.info("SMS credit debited. Remaining balance: {}", newBalance);
    }

    private void cacheSmsNotification(SmsMessageData messageData, SmsTypeEnum smsType, Boolean hasPassed, String response,
            boolean sensitive) {
        SmsNotificationAccountMessage message = new SmsNotificationAccountMessage();

        message.setMessage(sensitive ? "[one-time code withheld]" : messageData.getMessage());
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

    @Override
    public void processLoanSmsNotification(Loan loan, SmsTypeEnum smsType, LoanTransaction transaction) {

        if (!isSmsNotificationEnabled(smsType)) {
            log.info("Loan Sms Event Type " + smsType + " Is Not Activate");
            return;
        }

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

        if (!isSmsNotificationEnabled(smsType)) {
            log.info("SavingsAccount Sms Event Type " + smsType + " Is Not Activate");
            return;
        }

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

    private boolean isSmsNotificationEnabled(SmsTypeEnum smsType) {
        String eventType = SMS_TYPE_TO_EVENT_MAP.get(smsType);

        if (eventType == null) {
            log.info("No Event type found to process a notification for SMS type: {}", smsType);
            return false;
        }

        SmsEventConfiguration configuration = repository.findSmsEventConfigurationByTypeWithNotFoundDetection(eventType);
        return configuration.isEnabled();
    }

}
