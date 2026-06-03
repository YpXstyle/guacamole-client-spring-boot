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

package org.apache.guacamole.auth.jdbc.system;

import java.util.Date;

/**
 * Model representing a single system configuration entry from the
 * guacamole_system_config table.
 */
public class SystemConfigModel {

    /**
     * The unique configuration key (e.g., "branding.site_name").
     */
    private String configKey;

    /**
     * The configuration value, or null if not set.
     */
    private String configValue;

    /**
     * The type of the configuration value (string, integer, boolean, file,
     * text, url, enum, datetime).
     */
    private String configType;

    /**
     * The group this configuration belongs to (branding, theme, site,
     * security, feature, announcement).
     */
    private String configGroup;

    /**
     * The username of the last user to update this configuration.
     */
    private String updatedBy;

    /**
     * The timestamp of the last update.
     */
    private Date updatedAt;

    /**
     * Creates a new, empty SystemConfigModel.
     */
    public SystemConfigModel() {
    }

    /**
     * Returns the unique configuration key.
     *
     * @return The configuration key.
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Sets the unique configuration key.
     *
     * @param configKey The configuration key to set.
     */
    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    /**
     * Returns the configuration value.
     *
     * @return The configuration value, or null if not set.
     */
    public String getConfigValue() {
        return configValue;
    }

    /**
     * Sets the configuration value.
     *
     * @param configValue The configuration value to set.
     */
    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    /**
     * Returns the type of the configuration value.
     *
     * @return The configuration type (string, integer, boolean, file, text,
     *         url, enum, datetime).
     */
    public String getConfigType() {
        return configType;
    }

    /**
     * Sets the type of the configuration value.
     *
     * @param configType The configuration type to set.
     */
    public void setConfigType(String configType) {
        this.configType = configType;
    }

    /**
     * Returns the group this configuration belongs to.
     *
     * @return The configuration group (branding, theme, site, security,
     *         feature, announcement).
     */
    public String getConfigGroup() {
        return configGroup;
    }

    /**
     * Sets the group this configuration belongs to.
     *
     * @param configGroup The configuration group to set.
     */
    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    /**
     * Returns the username of the last user to update this configuration.
     *
     * @return The username, or null if never updated.
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
