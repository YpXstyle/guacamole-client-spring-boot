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

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for system configuration entries in the guacamole_system_config table.
 */
@Mapper
public interface SystemConfigMapper {

    /**
     * Returns all system configuration entries.
     *
     * @return A list of all configuration entries.
     */
    List<SystemConfigModel> selectAll();

    /**
     * Returns the configuration entry with the given key.
     *
     * @param configKey
     *     The configuration key to look up.
     *
     * @return
     *     The configuration entry, or null if not found.
     */
    SystemConfigModel selectByKey(@Param("configKey") String configKey);

    /**
     * Returns all configuration entries belonging to the given group.
     *
     * @param configGroup
     *     The configuration group to filter by.
     *
     * @return
     *     A list of configuration entries in the specified group.
     */
    List<SystemConfigModel> selectByGroup(@Param("configGroup") String configGroup);

    /**
     * Updates an existing configuration entry. Only updates the value,
     * updated_by, and updated_at fields. Does NOT create new rows —
     * the config key must already exist in the database (via DDL).
     *
     * @param configKey
     *     The configuration key.
     *
     * @param configValue
     *     The configuration value.
     *
     * @param updatedBy
     *     The username of the user making the update.
     *
     * @return
     *     The number of rows updated (0 if the key doesn't exist).
     */
    int update(@Param("configKey") String configKey,
               @Param("configValue") String configValue,
               @Param("updatedBy") String updatedBy);

}
