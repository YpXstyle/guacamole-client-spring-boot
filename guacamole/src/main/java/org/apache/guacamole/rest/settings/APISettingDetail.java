/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.guacamole.rest.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;

/**
 * Response DTO for a single configuration entry with metadata.
 * Used by the admin interface (GET /api/settings).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APISettingDetail {

    /**
     * The configuration key.
     */
    private String key;

    /**
     * The configuration value.
     */
    private String value;

    /**
     * The type of the configuration value.
     */
    private String type;

    /**
     * The group this configuration belongs to.
     */
    private String group;

    /**
     * The username of the last user to update this configuration.
     */
    private String updatedBy;

    /**
     * The timestamp of the last update.
     */
    private Date updatedAt;

    /**
     * Creates a new, empty APISettingDetail.
     */
    public APISettingDetail() {
    }

    /**
     * Returns the configuration key.
     *
     * @return The configuration key.
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the configuration key.
     *
     * @param key The configuration key to set.
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Returns the configuration value.
     *
     * @return The configuration value.
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the configuration value.
     *
     * @param value The configuration value to set.
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns the type of the configuration value.
     *
     * @return The configuration type.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of the configuration value.
     *
     * @param type The configuration type to set.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the group this configuration belongs to.
     *
     * @return The configuration group.
     */
    public String getGroup() {
        return group;
    }

    /**
     * Sets the group this configuration belongs to.
     *
     * @param group The configuration group to set.
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * Returns the username of the last user to update this configuration.
     *
     * @return The username.
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * Sets the username of the last user to update this configuration.
     *
     * @param updatedBy The username to set.
     */
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    /**
     * Returns the timestamp of the last update.
     *
     * @return The timestamp.
     */
    public Date getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the timestamp of the last update.
     *
     * @param updatedAt The timestamp to set.
     */
    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

}
