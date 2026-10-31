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
package org.apache.fineract.selfservice.registration.domain;

/**
 * Identifies the type of self-service request stored in {@code request_audit_table}.
 *
 * <p>
 * The value determines which workflow may consume the stored token and ensures that a token issued for one workflow
 * cannot be reused in another.
 */
public enum SelfServiceRequestType {
    /**
     * Request created for self-service registration confirmation.
     *
     * <p>
     * Use this for tokens that activate or finalize creation of a self-service user.
     */
    REGISTRATION,

    /**
     * Request created for self-enrollment confirmation.
     *
     * <p>
     * Use this for tokens that activate a disabled user created during one-shot self-enrollment.
     */
    ENROLLMENT,

    /**
     * Request created for self-service password reset.
     *
     * <p>
     * Use this for tokens that authorize renewal of an existing self-service password.
     */
    PASSWORD_RESET,

    /**
     * Request created for SINPE Móvil phone number enrollment.
     *
     * <p>
     * Use this for tokens (OTPs) that verify and authorize the linking of a mobile number for SINPE Móvil transactions.
     */
    SINPE_ENROLLMENT,

    ACCOUNT_TRANSFER
}
