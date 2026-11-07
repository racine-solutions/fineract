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
package org.apache.fineract.racine.campaigns.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.fineract.notification.service.SmsText;
import org.apache.fineract.racine.campaigns.data.MessageCheckData;
import org.apache.fineract.racine.campaigns.data.PlaceholderData;

/** The values a campaign message (SMS or email) can include, and the one-SMS check. */
public final class CommPlaceholders {

    /** Characters of the standard SMS alphabet; anything else makes the whole SMS Unicode (70 characters). */
    private static final String GSM_7 = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿"
            + "abcdefghijklmnopqrstuvwxyzäöñüà^{}\\[~]|€";
    public static final int UNICODE_LIMIT = 70;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*\\}\\}");

    public static final List<PlaceholderData> ALL = List.of(
            new PlaceholderData("client.name", "Client name", "Client", "Mwesigye", SmsText.MAX_NAME_LENGTH),
            new PlaceholderData("client.firstName", "First name", "Client", "John", 20),
            new PlaceholderData("client.lastName", "Last name", "Client", "Mwesigye", 20),
            new PlaceholderData("client.fullName", "Full name", "Client", "John Mwesigye", 30),
            new PlaceholderData("client.phone", "Phone", "Client", "256785154434", 15),
            new PlaceholderData("client.email", "Email", "Client", "john@example.com", 40),
            new PlaceholderData("client.accountNo", "Account no (masked)", "Client", "0****13", 7),
            new PlaceholderData("client.office", "Office", "Client", "Ntinda Branch", 30),
            new PlaceholderData("client.loanOfficer", "Loan officer", "Client", "Sarah Namuli", 30),
            new PlaceholderData("client.memberSince", "Member since", "Client", "3 Feb 2021", 11),
            new PlaceholderData("loans.totalOutstanding", "Loan balance", "Loans", "UGX 1,250,000", 17),
            new PlaceholderData("loans.nextDueDate", "Next due date", "Loans", "25 Nov 2026", 11),
            new PlaceholderData("loans.nextDueAmount", "Next due amount", "Loans", "UGX 85,000", 17),
            new PlaceholderData("loans.daysInArrears", "Days in arrears", "Loans", "12", 5),
            new PlaceholderData("loans.arrearsAmount", "Arrears amount", "Loans", "UGX 170,000", 17),
            new PlaceholderData("loans.count", "Active loans", "Loans", "1", 2),
            new PlaceholderData("loans.product", "Loan product", "Loans", "Business Loan", 30),
            new PlaceholderData("loans.accountNo", "Loan account (masked)", "Loans", "0****42", 7),
            new PlaceholderData("loans.lastPaymentAmount", "Last payment", "Loans", "UGX 85,000", 17),
            new PlaceholderData("loans.lastPaymentDate", "Last payment date", "Loans", "25 Oct 2026", 11),
            new PlaceholderData("loans.endDate", "Loan end date", "Loans", "25 Jun 2027", 11),
            new PlaceholderData("savings.totalBalance", "Savings balance", "Savings", "UGX 640,500", 17),
            new PlaceholderData("savings.count", "Savings accounts", "Savings", "1", 2),
            new PlaceholderData("savings.accountNo", "Savings account (masked)", "Savings", "0****57", 7),
            new PlaceholderData("savings.lastDepositAmount", "Last deposit", "Savings", "UGX 50,000", 17),
            new PlaceholderData("savings.lastDepositDate", "Last deposit date", "Savings", "30 Oct 2026", 11),
            new PlaceholderData("fixedDeposit.nextMaturityDate", "Maturity date", "Deposits", "2 Dec 2026", 11),
            new PlaceholderData("fixedDeposit.maturityAmount", "Maturity amount", "Deposits", "UGX 5,600,000", 17),
            new PlaceholderData("fixedDeposit.totalBalance", "Fixed deposit balance", "Deposits", "UGX 5,000,000", 17),
            new PlaceholderData("recurringDeposit.totalBalance", "Recurring deposit balance", "Deposits", "UGX 360,000", 17),
            new PlaceholderData("shares.count", "Shares", "Shares", "120", 10),
            new PlaceholderData("shares.value", "Shares value", "Shares", "UGX 1,200,000", 17),
            new PlaceholderData("staff.name", "Staff name", "Staff", "Sarah Namuli", 30),
            new PlaceholderData("staff.firstName", "Staff first name", "Staff", "Sarah", 20),
            new PlaceholderData("staff.office", "Staff office", "Staff", "Ntinda Branch", 30),
            new PlaceholderData("staff.clients", "Their clients", "Staff", "148", 5),
            new PlaceholderData("staff.activeLoans", "Their active loans", "Staff", "96", 5),
            new PlaceholderData("staff.loansInArrears", "Their loans in arrears", "Staff", "7", 5),
            new PlaceholderData("tenant.name", "Institution", "General", "Showroom", SmsText.MAX_NAME_LENGTH),
            new PlaceholderData("date.today", "Today's date", "General", "7 Nov 2026", 11));

    private static final Map<String, PlaceholderData> BY_KEY = ALL.stream()
            .collect(Collectors.toMap(PlaceholderData::key, Function.identity()));

    private CommPlaceholders() {}

    /** Length of the text with every known placeholder at its longest value; unknown ones count as typed. */
    public static int longestLength(final String text) {
        if (text == null) {
            return 0;
        }
        final Matcher matcher = PLACEHOLDER.matcher(text);
        final StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            final PlaceholderData placeholder = BY_KEY.get(matcher.group(1));
            matcher.appendReplacement(out,
                    placeholder == null ? Matcher.quoteReplacement(matcher.group()) : "x".repeat(placeholder.maxLength()));
        }
        matcher.appendTail(out);
        return out.length();
    }

    /** Groups only clients have: a message to staff can't use them, and staff tags don't fit a message to clients. */
    private static final List<String> CLIENT_GROUPS = List.of("client", "loans", "savings", "fixedDeposit", "recurringDeposit", "shares");

    /**
     * The tags in the text that don't suit who the message goes to, e.g. {{loans.nextDueDate}} in a message to staff.
     */
    public static List<String> unsuitable(final String text, final boolean toStaff) {
        final Matcher matcher = PLACEHOLDER.matcher(text == null ? "" : text);
        return matcher.results().map(result -> result.group(1)).filter(key -> {
            final String group = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
            return toStaff ? CLIENT_GROUPS.contains(group) : "staff".equals(group);
        }).distinct().toList();
    }

    /** Placeholders used in the text that the catalogue does not know. */
    public static List<String> unknown(final String text) {
        final Matcher matcher = PLACEHOLDER.matcher(text == null ? "" : text);
        return matcher.results().map(result -> result.group(1)).filter(key -> !BY_KEY.containsKey(key)).distinct().toList();
    }

    public static boolean needsUnicode(final String text) {
        return text != null && text.chars().anyMatch(ch -> GSM_7.indexOf(ch) < 0);
    }

    /** An email as one client would get it: no length limit, so it always fits. */
    public static MessageCheckData checkEmail(final String template, final String rendered, final String subject) {
        final String shown = rendered == null ? template : rendered;
        return new MessageCheckData(shown, shown == null ? 0 : shown.length(), longestLength(template), null, false, true, subject);
    }

    /** The one-SMS check for a template and, when given, the message one client would get. */
    public static MessageCheckData check(final String template, final String rendered) {
        final String shown = rendered == null ? template : rendered;
        final boolean unicode = needsUnicode(template) || needsUnicode(shown);
        final int limit = unicode ? UNICODE_LIMIT : SmsText.MAX_LENGTH;
        final int longest = longestLength(template);
        return new MessageCheckData(shown, shown == null ? 0 : shown.length(), longest, limit, unicode, longest <= limit, null);
    }
}
