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
package org.apache.fineract.racine.officerapp.domain;

/** What the app tells the server about the phone when it signs in. */
public record DeviceInfo(String installationId, String manufacturer, String model, String osVersion, String appVersion, String appBuild) {

    /** Model with manufacturer, e.g. "Tecno Spark 20". */
    public String label() {
        return label(manufacturer, model);
    }

    public static String label(final String manufacturer, final String model) {
        final String m = model == null ? "" : model.trim();
        final String make = manufacturer == null ? "" : manufacturer.trim();
        if (make.isEmpty()) {
            return m.isEmpty() ? "Unknown device" : m;
        }
        if (m.isEmpty()) {
            return make;
        }
        return m.toLowerCase(java.util.Locale.ROOT).startsWith(make.toLowerCase(java.util.Locale.ROOT)) ? m : capitalize(make) + " " + m;
    }

    private static String capitalize(final String s) {
        return s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
    }
}
