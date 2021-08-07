/*
 * Copyright (C) 2021 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.krypton.updater.model.data;

import com.krypton.updater.model.room.ChangelogEntity;

import java.util.Date;

public final class Changelog {

    private String changelog, sha;
    private Date date;

    public Changelog() {
        // Empty constructor
    }

    public Changelog(ChangelogEntity entity) {
        date = entity.date;
        changelog = entity.changelog;
        sha = entity.sha;
    }

    public Changelog setDate(Date date) {
        this.date = date;
        return this;
    }

    public Changelog setChangelog(String changelog) {
        this.changelog = changelog;
        return this;
    }

    public Changelog setSHA(String sha) {
        this.sha = sha;
        return this;
    }

    public Date getDate() {
        return date;
    }

    public String getChangelog() {
        return changelog;
    }

    public String getSHA() {
        return sha;
    }

    public ChangelogEntity toEntity() {
        final ChangelogEntity entity = new ChangelogEntity();
        entity.date = date;
        entity.changelog = changelog;
        entity.sha = sha;
        return entity;
    }
}
