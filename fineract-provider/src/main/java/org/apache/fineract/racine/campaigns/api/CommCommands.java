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
package org.apache.fineract.racine.campaigns.api;

import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;

/** Commands for campaigns, templates and marketing consent, so permissions and maker-checker apply. */
final class CommCommands {

    private CommCommands() {}

    static CommandWrapper of(final String action, final String entity, final Long entityId, final Long subEntityId, final Long clientId,
            final String href, final String json) {
        return new CommandWrapper(null, null, clientId, null, null, action, entity, entityId, subEntityId, href,
                StringUtils.isBlank(json) ? "{}" : json, null, null, null, null, null, null, null, null, null);
    }
}
