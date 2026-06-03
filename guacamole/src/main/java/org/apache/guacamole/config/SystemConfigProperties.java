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

package org.apache.guacamole.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the system configuration module.
 * Reads values from the "guacamole.system" section of application.yml.
 */
@Component
@ConfigurationProperties(prefix = "guacamole.system")
public class SystemConfigProperties {

    /**
     * Base directory for file storage. Uploaded files (logos, favicons, etc.)
     * will be stored under this directory.
     */
    private String fileStoragePath = "/opt/guacamole/files";

    /**
     * Default configuration values used when the database is not available.
     * This serves as a fallback for deployments without JDBC authentication.
     */
    private Map<String, String> defaults = new HashMap<>();

    /**
     * Returns the base directory for file storage.
     *
     * @return The file storage path.
     */
    public String getFileStoragePath() {
        return fileStoragePath;
    }

    /**
     * Sets the base directory for file storage.
     *
     * @param fileStoragePath The file storage path to set.
     */
    public void setFileStoragePath(String fileStoragePath) {
        this.fileStoragePath = fileStoragePath;
    }

    /**
     * Returns the default configuration values.
     *
     * @return A map of config key to default value.
     */
    public Map<String, String> getDefaults() {
        return defaults;
    }

    /**
     * Sets the default configuration values.
     *
     * @param defaults The defaults map to set.
     */
    public void setDefaults(Map<String, String> defaults) {
        this.defaults = defaults;
    }

    /**
     * Returns the default value for the given config key.
     *
     * @param key The configuration key.
     *
     * @return The default value, or null if not defined.
     */
    public String getDefaultValue(String key) {
        return defaults.get(key);
    }

}
