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
package org.apache.fineract.selfservice.signup.exception;

import java.util.Map;
import lombok.Getter;

/**
 * A sign-up or forgot-password step that cannot go ahead. Turned into a normal Fineract error body with the given HTTP
 * status and any extra fields the app needs (tries left, seconds to wait, support numbers).
 */
@Getter
public class SelfServiceCodeException extends RuntimeException {

    private final int status;
    private final String code;
    private final transient Map<String, Object> extra;

    public SelfServiceCodeException(final int status, final String code, final String message) {
        this(status, code, message, Map.of());
    }

    public SelfServiceCodeException(final int status, final String code, final String message, final Map<String, Object> extra) {
        super(message);
        this.status = status;
        this.code = code;
        this.extra = extra;
    }
}
