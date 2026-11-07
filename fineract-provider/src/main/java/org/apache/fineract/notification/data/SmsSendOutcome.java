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
package org.apache.fineract.notification.data;

/**
 * What happened to one SMS handed to the gateway.
 *
 * @param status
 *            the result
 * @param gatewayResponse
 *            the gateway's answer when it was called, else null
 */
public record SmsSendOutcome(Status status, String gatewayResponse) {

    public enum Status {
        /** The gateway accepted it and one credit was used. */
        SENT,
        /** The enable-sms-notifications configuration is switched off. */
        SMS_OFF,
        /** The SMS account is missing or inactive. */
        ACCOUNT_INACTIVE,
        /** No SMS credit left. */
        NO_CREDIT,
        /** The gateway answered but refused the message. */
        GATEWAY_REFUSED,
        /** The gateway could not be reached or answered with an error. */
        ERROR
    }

    public static SmsSendOutcome of(Status status) {
        return new SmsSendOutcome(status, null);
    }

    public boolean isSent() {
        return status == Status.SENT;
    }
}
