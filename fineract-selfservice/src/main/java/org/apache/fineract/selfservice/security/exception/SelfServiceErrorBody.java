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
package org.apache.fineract.selfservice.security.exception;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Error bodies in the normal Fineract format (httpStatusCode, userMessageGlobalisationCode, defaultUserMessage,
 * developerMessage, errors), for responses written by self-service filters and exception mappers.
 */
public final class SelfServiceErrorBody {

    private static final Gson GSON = new Gson();

    private SelfServiceErrorBody() {}

    public static String json(final int status, final String code, final String message) {
        return json(status, code, message, Map.of());
    }

    /** extra: further top-level fields the client needs (e.g. the tokens for a forced password change). */
    public static String json(final int status, final String code, final String message, final Map<String, Object> extra) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("developerMessage", message);
        body.put("httpStatusCode", String.valueOf(status));
        body.put("defaultUserMessage", message);
        body.put("userMessageGlobalisationCode", code);
        body.put("errors", List.of());
        body.putAll(extra);
        return GSON.toJson(body);
    }
}
