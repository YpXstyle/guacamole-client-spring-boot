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
import java.util.Map;

/**
 * Response DTO for the public configuration endpoint (GET /api/config).
 * Contains branding, theme, features, and announcement configuration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIConfigResponse {

    /**
     * Branding configuration (name, logo, copyright, etc.).
     */
    private Map<String, String> branding;

    /**
     * Theme configuration (colors, mode).
     */
    private Map<String, String> theme;

    /**
     * Announcement configuration.
     */
    private Map<String, String> announcement;

    /**
     * Creates a new, empty APIConfigResponse.
     */
    public APIConfigResponse() {
    }

    /**
     * Returns the branding configuration.
     *
     * @return A map of branding config key to value.
     */
    public Map<String, String> getBranding() {
        return branding;
    }

    /**
     * Sets the branding configuration.
     *
     * @param branding The branding config map to set.
     */
    public void setBranding(Map<String, String> branding) {
        this.branding = branding;
    }

    /**
     * Returns the theme configuration.
     *
     * @return A map of theme config key to value.
     */
    public Map<String, String> getTheme() {
        return theme;
    }

    /**
     * Sets the theme configuration.
     *
     * @param theme The theme config map to set.
     */
    public void setTheme(Map<String, String> theme) {
        this.theme = theme;
    }

    /**
     * Returns the announcement configuration.
     *
     * @return A map of announcement config key to value.
     */
    public Map<String, String> getAnnouncement() {
        return announcement;
    }

    /**
     * Sets the announcement configuration.
     *
     * @param announcement The announcement config map to set.
     */
    public void setAnnouncement(Map<String, String> announcement) {
        this.announcement = announcement;
    }

}
