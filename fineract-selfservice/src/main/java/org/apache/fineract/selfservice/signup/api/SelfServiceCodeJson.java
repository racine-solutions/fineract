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
package org.apache.fineract.selfservice.signup.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/** Reads request bodies and writes answers for the sign-up and forgot-password endpoints. */
final class SelfServiceCodeJson {

    static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private SelfServiceCodeJson() {}

    static <T> T read(final String body, final Class<T> type) {
        try {
            final T value = GSON.fromJson(body, type);
            if (value == null) {
                throw new IllegalArgumentException("A JSON body is required");
            }
            return value;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("The request body is not valid JSON");
        }
    }

    static String write(final Object value) {
        return GSON.toJson(value);
    }
}
