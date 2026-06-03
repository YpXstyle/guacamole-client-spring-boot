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

/**
 * Request DTO for updating a configuration entry (PUT /api/settings/{key}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APISettingUpdate {

    /**
     * The new configuration value. A null value is stored as an empty
     * string and does NOT delete the database row. Configuration rows
     * can only be deleted by running a DDL migration to drop them.
     */
    private String value;

    /**
     * Creates a new, empty APISettingUpdate.
     */
    public APISettingUpdate() {
    }

    /**
     * Returns the new configuration value.
     *
     * @return The new value.
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the new configuration value.
     *
     * @param value The new value to set.
     */
    public void setValue(String value) {
        this.value = value;
    }

}
