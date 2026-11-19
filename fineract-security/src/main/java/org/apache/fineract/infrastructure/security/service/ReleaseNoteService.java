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
package org.apache.fineract.infrastructure.security.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks whether each staff user has read the current release notes.
 * <p>
 * The current version is the {@code release-note-version} configuration. A user has read the notes when
 * {@code m_appuser.viewed_release_note} is set and {@code viewed_release_note_version} matches that version, so a new
 * version shows the notes again to everyone without touching user rows. While the configuration is disabled or empty,
 * every user counts as having read the notes, so nobody is shown them.
 */
@Service
@RequiredArgsConstructor
public class ReleaseNoteService {

    public static final String CONFIG_NAME = "release-note-version";

    private final JdbcTemplate jdbcTemplate;

    /** The release-note version staff should read, or null while the configuration is disabled or empty. */
    public String currentVersion() {
        final List<Map<String, Object>> rows = this.jdbcTemplate
                .queryForList("select enabled, string_value from c_configuration where name = ?", CONFIG_NAME);
        if (rows.isEmpty() || !isTrue(rows.get(0).get("enabled"))) {
            return null;
        }
        final Object value = rows.get(0).get("string_value");
        return value == null ? null : StringUtils.trimToNull(value.toString());
    }

    /** True when the user has read the current version of the release notes, or when none is set. */
    public boolean hasViewed(final Long userId) {
        final String current = currentVersion();
        if (current == null || userId == null) {
            return true;
        }
        final List<Map<String, Object>> rows = this.jdbcTemplate
                .queryForList("select viewed_release_note, viewed_release_note_version from m_appuser where id = ?", userId);
        if (rows.isEmpty()) {
            return true;
        }
        final Map<String, Object> row = rows.get(0);
        final Object version = row.get("viewed_release_note_version");
        return isTrue(row.get("viewed_release_note")) && version != null && current.equals(version.toString());
    }

    /** When the user last marked the release notes as read, or null if never. */
    public LocalDateTime viewedOn(final Long userId) {
        final List<LocalDateTime> rows = this.jdbcTemplate.queryForList("select viewed_release_note_on from m_appuser where id = ?",
                LocalDateTime.class, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Records that the user has read the given version. Returns the time saved. */
    @Transactional
    public LocalDateTime markViewed(final Long userId, final String version) {
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        this.jdbcTemplate.update(
                "update m_appuser set viewed_release_note = ?, viewed_release_note_version = ?, viewed_release_note_on = ? where id = ?",
                true, version, now, userId);
        return now;
    }

    private static boolean isTrue(final Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
