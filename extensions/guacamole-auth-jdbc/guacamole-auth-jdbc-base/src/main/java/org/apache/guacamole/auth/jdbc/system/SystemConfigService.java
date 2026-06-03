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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing system configuration entries. Provides caching
 * for configuration values read from the database.
 */
@Service
public class SystemConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigService.class);

    /**
     * Cache for configuration values. Entries expire after 60 seconds
     * to balance freshness with database load.
     */
    private final Cache<String, String> configCache = CacheBuilder.newBuilder()
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .maximumSize(200)
        .build();

    @Autowired(required = false)
    private SystemConfigMapper configMapper;

    /**
     * Returns the configuration value for the given key. Uses caching.
     * Returns null if the key is not found or the database is not available.
     *
     * @param key
     *     The configuration key to look up.
     *
     * @return
     *     The configuration value, or null if not found.
     */
    public String getValue(String key) {
        // Try cache first
        String cached = configCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // Try database
        if (configMapper != null) {
            try {
                SystemConfigModel model = configMapper.selectByKey(key);
                if (model != null && model.getConfigValue() != null) {
                    configCache.put(key, model.getConfigValue());
                    return model.getConfigValue();
                }
            } catch (Exception e) {
                logger.warn("Failed to read config from database: {}", key, e);
            }
        }

        return null;
    }

    /**
     * Returns all configuration values as a map.
     *
     * @return
     *     A map of config key to value.
     */
    public Map<String, String> getAllValues() {
        Map<String, String> result = new HashMap<>();

        if (configMapper != null) {
            try {
                List<SystemConfigModel> models = configMapper.selectAll();
                for (SystemConfigModel model : models) {
                    if (model.getConfigValue() != null) {
                        result.put(model.getConfigKey(), model.getConfigValue());
                        configCache.put(model.getConfigKey(), model.getConfigValue());
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to read all configs from database", e);
            }
        }

        return result;
    }

    /**
     * Returns all configuration values for the given group.
     *
     * @param group
     *     The configuration group to filter by.
     *
     * @return
     *     A map of config key to value.
     */
    public Map<String, String> getValuesByGroup(String group) {
        Map<String, String> result = new HashMap<>();

        if (configMapper != null) {
            try {
                List<SystemConfigModel> models = configMapper.selectByGroup(group);
                for (SystemConfigModel model : models) {
                    if (model.getConfigValue() != null) {
                        result.put(model.getConfigKey(), model.getConfigValue());
                        configCache.put(model.getConfigKey(), model.getConfigValue());
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to read configs for group: {}", group, e);
            }
        }

        return result;
    }

    /**
     * Returns all configuration entries with metadata (for admin interface).
     *
     * @return
     *     A list of all configuration entries.
     */
    public List<SystemConfigModel> getAllEntries() {
        if (configMapper != null) {
            try {
                return configMapper.selectAll();
            } catch (Exception e) {
                logger.warn("Failed to read all config entries from database", e);
            }
        }
        return List.of();
    }

    /**
     * Updates a configuration value. Immediately invalidates the cache.
     * Only updates existing rows — the config key must already exist in
     * the database (via DDL migration). Throws an exception if the key
     * is not found.
     *
     * @param key
     *     The configuration key to update.
     *
     * @param value
     *     The new value.
     *
     * @param updatedBy
     *     The username of the user making the update.
     *
     * @throws IllegalStateException
     *     If the database is not available or the config key doesn't exist.
     */
    public void updateValue(String key, String value, String updatedBy) {
        if (configMapper == null) {
            throw new IllegalStateException("Database not available for configuration updates");
        }
        int rows = configMapper.update(key, value, updatedBy);
        if (rows == 0) {
            throw new IllegalStateException(
                "Configuration key not found: " + key
                + ". Run the DDL migration (003-create-system-config.sql) first.");
        }
        configCache.invalidate(key);
        logger.info("Configuration updated: {} = {} by {}", key, value, updatedBy);
    }

    /**
     * Clears the entire configuration cache.
     */
    public void clearCache() {
        configCache.invalidateAll();
        logger.info("Configuration cache cleared");
    }

}
