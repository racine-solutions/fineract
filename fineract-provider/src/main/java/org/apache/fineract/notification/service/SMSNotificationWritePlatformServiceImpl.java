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
import java.math.BigDecimal;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationProperty;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.external.repository.SmsEventConfigurationRepository;
import org.apache.fineract.infrastructure.event.external.repository.domain.SmsEventConfiguration;
import org.apache.fineract.notification.data.SmsMessageData;
import org.apache.fineract.notification.data.SmsNotificationData;
import org.apache.fineract.notification.data.SmsSendOutcome;
import org.apache.fineract.notification.data.SmsTypeEnum;
import org.apache.fineract.notification.data.SmsUserData;
import org.apache.fineract.notification.domain.SmsNotificationAccount;
import org.apache.fineract.notification.domain.SmsNotificationAccountMessage;
import org.apache.fineract.notification.domain.SmsNotificationAccountRepository;
import org.apache.fineract.notification.domain.SmsNotificationMessageRepository;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.domain.FixedDepositAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccount;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccountTransaction;
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
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.LOAN_CLEARANCE, "LoanAccountClearanceEvent");
        // Savings
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SAVINGS_SUBMISSION, "SavingsAccountCreationEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SAVINGS_APPROVAL, "SavingsAccountApprovalEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SAVINGS_ACTIVATION, "SavingsAccountActivationEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SAVINGS_DEPOSIT, "SavingsAccountDepositEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SAVINGS_WITHDRAW, "SavingsAccountWithdrawEvent");
        // Fixed deposits: the deposit is made on activation, the payout on closure
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.FIXED_DEPOSIT_SUBMISSION, "FixedDepositAccountCreationEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.FIXED_DEPOSIT_APPROVAL, "FixedDepositAccountApprovalEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.FIXED_DEPOSIT_ACTIVATION, "FixedDepositAccountActivationEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.FIXED_DEPOSIT_WITHDRAW, "FixedDepositAccountWithdrawEvent");
        // Recurring deposits
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.RECURRING_DEPOSIT_SUBMISSION, "RecurringDepositAccountCreationEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.RECURRING_DEPOSIT_APPROVAL, "RecurringDepositAccountApprovalEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.RECURRING_DEPOSIT_ACTIVATION, "RecurringDepositAccountActivationEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.RECURRING_DEPOSIT_DEPOSIT, "RecurringDepositAccountDepositEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.RECURRING_DEPOSIT_WITHDRAW, "RecurringDepositAccountWithdrawEvent");
        // Shares
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SHARE_SUBMISSION, "ShareAccountCreationEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SHARE_APPROVAL, "ShareAccountApprovalEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SHARE_ACTIVATION, "ShareAccountActivationEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SHARE_PURCHASE, "ShareAccountPurchaseEvent");
        SMS_TYPE_TO_EVENT_MAP.put(SmsTypeEnum.SHARE_REDEEM, "ShareAccountRedeemEvent");
    }

    @Override
    public void sendSms(SmsMessageData messageData, SmsTypeEnum smsType) {
        deliver(messageData, smsType, false, null);
    }

    @Override
    public boolean sendSmsNow(SmsMessageData messageData, SmsTypeEnum smsType) {
        return deliver(messageData, smsType, true, null).isSent();
    }

    @Override
    public SmsSendOutcome sendCampaignSms(SmsMessageData messageData, String senderId) {
        return deliver(messageData, SmsTypeEnum.CAMPAIGN, false, senderId);
    }

    /**
     * Sends one SMS through the gateway.
     *
     * @param sensitive
     *            the text carries a secret (one-time code), so it is never written to the log or the message history
     * @return what happened; with no credit left the message is skipped, never an error, so the deposit, repayment or
     *         approval that triggered it still goes through
     */
    private SmsSendOutcome deliver(SmsMessageData messageData, SmsTypeEnum smsType, boolean sensitive, String senderId) {

        final GlobalConfigurationProperty property = this.configurationRepositoryWrapper
                .findOneByNameWithNotFoundDetection(GlobalConfigurationConstants.ENABLE_SMS_NOTIFICATIONS);

        Optional<SmsNotificationAccount> smsAccount = smsNotificationAccountRepository.findById(1L);

        if (smsAccount.isPresent() && smsAccount.get().getIsActive()) {
            log.info("SMS Account found with total balance: {}", smsAccount.get().getSmsTotalBalance());
        } else {
            log.info("SMS Account not found or is not active for this tenant :- " + ThreadLocalContextUtil.getTenant().getName());
            return SmsSendOutcome.of(SmsSendOutcome.Status.ACCOUNT_INACTIVE);
        }

        final SmsNotificationAccount account = smsAccount.get();

        if (account.getSmsTotalBalance() == null || account.getSmsTotalBalance() <= 0) {
            log.warn("Insufficient SMS credit balance ({}) for tenant :- {}. Message not sent.", account.getSmsTotalBalance(),
                    ThreadLocalContextUtil.getTenant().getName());
            return SmsSendOutcome.of(SmsSendOutcome.Status.NO_CREDIT);
        }

        if (!property.isEnabled()) {
            log.info("** SMS Notification is disabled for this Tenant :-> " + ThreadLocalContextUtil.getTenant().getName());
            return SmsSendOutcome.of(SmsSendOutcome.Status.SMS_OFF);
        }

        // Every message, core or self-service, passes here: hold it to one SMS and sign it with the tenant if it fits
        messageData.setMessage(SmsText.fit(messageData.getMessage()));

        Gson gson = new GsonBuilder().create();

        SmsUserData userData = new SmsUserData(getConfigProperty("sms.username"), getConfigProperty("sms.password"));
        messageData.setSenderid(senderId != null && !senderId.isBlank() ? senderId.trim() : getConfigProperty("sms.sender.id"));
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
            if (accepted) {
                return new SmsSendOutcome(SmsSendOutcome.Status.SENT, resObject);
            }
            return new SmsSendOutcome(response.isSuccessful() ? SmsSendOutcome.Status.GATEWAY_REFUSED : SmsSendOutcome.Status.ERROR,
                    resObject);
        } catch (Exception e) {
            // SMS delivery failures must never propagate to callers (e.g. loan repayment).
            log.error("Posting sms notification has failed: {}", e.getMessage(), e);
            return new SmsSendOutcome(SmsSendOutcome.Status.ERROR, e.getMessage());
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
        try {
            notifyLoanSmsNotification(loan, smsType, transaction);
        } catch (RuntimeException e) {
            // An SMS is a courtesy: it must never undo the transaction it reports on
            log.error("{} SMS failed and was skipped: {}", smsType, e.getMessage(), e);
        }
    }

    private void notifyLoanSmsNotification(Loan loan, SmsTypeEnum smsType, LoanTransaction transaction) {

        if (!isSmsNotificationEnabled(smsType)) {
            log.info("Loan Sms Event Type " + smsType + " Is Not Activate");
            return;
        }

        if (loan.client() == null) {
            log.info("No client on loan {}: group loans get no SMS", loan.getId());
            return;
        }
        String clientName = recipientName(loan.client());
        String message = null;
        String messageId = null;
        switch (smsType) {
            case LOAN_SUBMISSION:
                message = String.format("Hi %s, your loan application #%s is under review. We will update you soon.", clientName,
                        SmsText.maskAccountNumber(loan.getAccountNumber()));
                messageId = String.format("LOAN-SUBMISSION-%s", loan.getId());
            break;
            case LOAN_APPROVAL:
                message = String.format("Hi %s, your loan #%s of %s %s is approved.", clientName,
                        SmsText.maskAccountNumber(loan.getAccountNumber()), loan.getCurrencyCode(),
                        loan.getApprovedPrincipal().setScale(2, RoundingMode.HALF_UP));
                messageId = String.format("LOAN-APPROVAL-%s", loan.getId());
            break;
            case LOAN_DISBURSEMENT:
                message = String.format("Hi %s, your loan #%s of %s %s was disbursed on %s.", clientName,
                        SmsText.maskAccountNumber(loan.getAccountNumber()), loan.getCurrencyCode(),
                        loan.getProposedPrincipal().setScale(2, RoundingMode.HALF_UP), DateUtils.getBusinessLocalDate());
                messageId = String.format("LOAN-DISBURSEMENT-%s", loan.getId());
            break;
            case LOAN_REJECTED:
                message = String.format("Hi %s, your loan application #%s for %s %s was not approved.", clientName,
                        SmsText.maskAccountNumber(loan.getAccountNumber()), loan.getCurrencyCode(),
                        loan.getProposedPrincipal().setScale(2, RoundingMode.HALF_UP));
                messageId = String.format("LOAN-REJECTED-%s", loan.getId());
            break;
            case LOAN_REPAYMENT:
                message = String.format("Hi %s, repayment of %s %s received on loan #%s #%s Date:%s", clientName, loan.getCurrencyCode(),
                        transaction.getAmount().setScale(2, RoundingMode.HALF_UP), SmsText.maskAccountNumber(loan.getAccountNumber()),
                        transaction.getId(), transaction.getTransactionDate());
                messageId = String.format("LOAN-REPAYMENT-%s", transaction.getId());
            break;
            case LOAN_CLEARANCE:
                message = String.format("Hi %s, your loan #%s is fully paid and cleared. Date:%s. Thank you!", clientName,
                        SmsText.maskAccountNumber(loan.getAccountNumber()), transaction.getTransactionDate());
                if (loan.getStatus().isOverpaid()) {
                    message += String.format(" Overpaid: %s %s", loan.getCurrencyCode(),
                            loan.getTotalOverpaidAsMoney().getAmount().setScale(2, RoundingMode.HALF_UP));
                }
                messageId = String.format("LOAN-CLEARANCE-%s", loan.getId());
            break;
            default:
                log.info("No sms type found to process a notification");
                return;

        }

        send(loan.client(), message, messageId, smsType);
    }

    @Override
    public void processSavingsSmsNotification(SavingsAccount savingsAccount, SmsTypeEnum smsType, SavingsAccountTransaction transaction) {
        try {
            notifySavingsSmsNotification(savingsAccount, smsType, transaction);
        } catch (RuntimeException e) {
            // An SMS is a courtesy: it must never undo the transaction it reports on
            log.error("{} SMS failed and was skipped: {}", smsType, e.getMessage(), e);
        }
    }

    private void notifySavingsSmsNotification(SavingsAccount savingsAccount, SmsTypeEnum smsType, SavingsAccountTransaction transaction) {

        if (!isSmsNotificationEnabled(smsType)) {
            log.info("SavingsAccount Sms Event Type " + smsType + " Is Not Activate");
            return;
        }
        if (savingsAccount.getClient() == null) {
            log.info("No client on savings account {}: group accounts get no SMS", savingsAccount.getId());
            return;
        }

        String message = savingsMessage(savingsAccount, smsType, transaction);
        if (message == null) {
            log.info("No sms type found to process a notification");
            return;
        }
        Long reference = transaction != null ? transaction.getId() : savingsAccount.getId();
        send(savingsAccount.getClient(), message, smsType.name().replace('_', '-') + "-" + reference, smsType);
    }

    /**
     * One wording for savings, fixed deposit and recurring deposit accounts, so every account reads the same: "Hi Doe,
     * your fixed deposit #0****42 is approved."
     */
    static String savingsMessage(SavingsAccount account, SmsTypeEnum smsType, SavingsAccountTransaction transaction) {
        String name = recipientName(account.getClient());
        String product = productName(account.depositAccountType());
        String number = SmsText.maskAccountNumber(account.getAccountNumber());
        String currency = account.getCurrency().getCode();
        switch (smsType) {
            case SAVINGS_SUBMISSION:
            case FIXED_DEPOSIT_SUBMISSION:
            case RECURRING_DEPOSIT_SUBMISSION:
                return String.format("Hi %s, your %s application #%s is under review. We will update you soon.", name, product, number);
            case SAVINGS_APPROVAL:
            case FIXED_DEPOSIT_APPROVAL:
            case RECURRING_DEPOSIT_APPROVAL:
                return String.format("Hi %s, your %s #%s is approved.", name, product, number);
            case FIXED_DEPOSIT_ACTIVATION:
                if (account instanceof FixedDepositAccount fixedDeposit && fixedDeposit.maturityDate() != null
                        && fixedDeposit.maturityAmount() != null) {
                    return String.format("Hi %s, your fixed deposit #%s of %s %s is active. Matures on %s with %s %s.", name, number,
                            currency, amount(fixedDeposit.getDepositAmount()), fixedDeposit.maturityDate(), currency,
                            amount(fixedDeposit.maturityAmount()));
                }
                return String.format("Hi %s, your %s #%s is now active.", name, product, number);
            case SAVINGS_ACTIVATION:
            case RECURRING_DEPOSIT_ACTIVATION:
                return String.format("Hi %s, your %s #%s is now active.", name, product, number);
            case SAVINGS_DEPOSIT:
            case RECURRING_DEPOSIT_DEPOSIT:
                return String.format("Hi %s, deposit of %s %s made to %s #%s #%s Date:%s", name, currency, amount(transaction.getAmount()),
                        product, number, transaction.getId(), transaction.getTransactionDate());
            case SAVINGS_WITHDRAW:
            case FIXED_DEPOSIT_WITHDRAW:
            case RECURRING_DEPOSIT_WITHDRAW:
                if (account.isClosed()) {
                    // A fixed or recurring deposit pays out when it is closed, at or before maturity
                    if (transaction == null) {
                        return String.format("Hi %s, your %s #%s is closed. Date:%s", name, product, number,
                                DateUtils.getBusinessLocalDate());
                    }
                    return String.format("Hi %s, your %s #%s is closed and %s %s paid out. #%s Date:%s", name, product, number, currency,
                            amount(transaction.getAmount()), transaction.getId(), transaction.getTransactionDate());
                }
                return String.format("Hi %s, withdrawal of %s %s made from %s #%s #%s Date:%s", name, currency,
                        amount(transaction.getAmount()), product, number, transaction.getId(), transaction.getTransactionDate());
            default:
                return null;
        }
    }

    @Override
    public void processShareSmsNotification(ShareAccount shareAccount, SmsTypeEnum smsType, ShareAccountTransaction transaction) {
        try {
            notifyShareSmsNotification(shareAccount, smsType, transaction);
        } catch (RuntimeException e) {
            // An SMS is a courtesy: it must never undo the transaction it reports on
            log.error("{} SMS failed and was skipped: {}", smsType, e.getMessage(), e);
        }
    }

    private void notifyShareSmsNotification(ShareAccount shareAccount, SmsTypeEnum smsType, ShareAccountTransaction transaction) {

        if (!isSmsNotificationEnabled(smsType)) {
            log.info("ShareAccount Sms Event Type " + smsType + " Is Not Activate");
            return;
        }
        if (shareAccount.getClient() == null) {
            log.info("No client on share account {}: no SMS sent", shareAccount.getId());
            return;
        }

        String message = shareMessage(shareAccount, smsType, transaction);
        if (message == null) {
            log.info("No sms type found to process a notification");
            return;
        }
        Long reference = transaction != null ? transaction.getId() : shareAccount.getId();
        send(shareAccount.getClient(), message, smsType.name().replace('_', '-') + "-" + reference, smsType);
    }

    static String shareMessage(ShareAccount account, SmsTypeEnum smsType, ShareAccountTransaction transaction) {
        String name = recipientName(account.getClient());
        String number = SmsText.maskAccountNumber(account.getAccountNumber());
        String currency = account.getCurrency().getCode();
        switch (smsType) {
            case SHARE_SUBMISSION:
                return String.format("Hi %s, your share account application #%s is under review. We will update you soon.", name, number);
            case SHARE_APPROVAL:
                return String.format("Hi %s, your share account #%s is approved.", name, number);
            case SHARE_ACTIVATION:
                return String.format("Hi %s, your share account #%s is now active with %s shares.", name, number,
                        account.getTotalApprovedShares());
            case SHARE_PURCHASE:
                return String.format("Hi %s, purchase of %s shares for %s %s on share account #%s #%s Date:%s", name,
                        transaction.getTotalShares(), currency, amount(transaction.amount()), number, transaction.getId(),
                        transaction.getPurchasedDate());
            case SHARE_REDEEM:
                return String.format("Hi %s, %s shares redeemed for %s %s from share account #%s #%s Date:%s", name,
                        transaction.getTotalShares(), currency, amount(transaction.amount()), number, transaction.getId(),
                        transaction.getPurchasedDate());
            default:
                return null;
        }
    }

    private static String productName(DepositAccountType type) {
        if (type == null) {
            return "savings account";
        }
        return switch (type) {
            case FIXED_DEPOSIT -> "fixed deposit";
            case RECURRING_DEPOSIT -> "recurring deposit";
            default -> "savings account";
        };
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** The one way every account SMS goes out: to the client's mobile number, when there is one. */
    private void send(Client client, String message, String messageId, SmsTypeEnum smsType) {
        String mobileNo = client.getMobileNo();
        if (StringUtils.isBlank(mobileNo)) {
            log.info("No mobile number for client {}, so no {} SMS", client.getId(), smsType);
            return;
        }
        sendSms(new SmsMessageData(mobileNo, message, messageId), smsType);
    }

    /** Last name, else first name; entity clients have neither, so they fall back to their display name. */
    static String recipientName(Client client) {
        String name = SmsText.recipientName(client.getLastname(), client.getFirstname());
        if (name == null) {
            name = SmsText.shorten(StringUtils.defaultIfBlank(client.getDisplayName(), "Customer"));
        }
        return name;
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
