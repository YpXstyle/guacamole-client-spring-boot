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

import java.util.HashMap;
import java.util.Map;
import org.apache.guacamole.auth.jdbc.system.SystemConfigService;
import org.apache.guacamole.config.SystemConfigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST service for public configuration (GET /api/config).
 * This endpoint does NOT require authentication and is used by the
 * frontend to load branding, theme, features, and announcement config.
 */
@Path("/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConfigRESTService {

    @Autowired(required = false)
    private SystemConfigService configService;

    @Autowired
    private SystemConfigProperties systemConfigProperties;

    /**
     * Returns the public configuration for the frontend.
     * Includes branding, theme, features, and announcement.
     *
     * @return
     *     The public configuration response.
     */
    @GET
    public APIConfigResponse getConfig() {
        APIConfigResponse response = new APIConfigResponse();

        // Branding - try database first, fallback to yml defaults
        Map<String, String> branding = new HashMap<>();
        branding.put("siteName", getConfigValue("branding.site_name"));
        branding.put("siteNameShort", getConfigValue("branding.site_name_short"));
        branding.put("logo", getConfigValue("branding.logo"));
        branding.put("logoDark", getConfigValue("branding.logo_dark"));
        branding.put("favicon", getConfigValue("branding.favicon"));
        branding.put("loginBackground", getConfigValue("branding.login_background"));
        branding.put("copyright", getConfigValue("branding.copyright"));
        branding.put("supportUrl", getConfigValue("branding.support_url"));
        branding.put("helpUrl", getConfigValue("branding.help_url"));
        response.setBranding(branding);

        // Theme - try database first, fallback to yml defaults
        Map<String, String> theme = new HashMap<>();
        theme.put("primaryColor", getConfigValue("theme.primary_color"));
        theme.put("accentColor", getConfigValue("theme.accent_color"));
        theme.put("successColor", getConfigValue("theme.success_color"));
        theme.put("warningColor", getConfigValue("theme.warning_color"));
        theme.put("dangerColor", getConfigValue("theme.danger_color"));
        theme.put("mode", getConfigValue("theme.mode"));
        response.setTheme(theme);

        // Announcement - try database first, fallback to yml defaults
        Map<String, String> announcement = new HashMap<>();
        announcement.put("message", getConfigValue("announcement.message"));
        announcement.put("level", getConfigValue("announcement.level"));
        announcement.put("enabled", getConfigValue("announcement.enabled"));
        announcement.put("startTime", getConfigValue("announcement.start_time"));
        announcement.put("endTime", getConfigValue("announcement.end_time"));
        announcement.put("closable", getConfigValue("announcement.closable"));
        response.setAnnouncement(announcement);

        return response;
    }

    /**
     * Gets a config value with fallback chain: database -> yml defaults -> null.
     *
     * @param key
     *     The configuration key.
     *
     * @return
     *     The configuration value, or null if not found anywhere.
     */
    private String getConfigValue(String key) {
        // Try database first (if JDBC extension is enabled)
        if (configService != null) {
            String value = configService.getValue(key);
            if (value != null) {
                return value;
            }
        }

        // Fallback to yml defaults
        return systemConfigProperties.getDefaultValue(key);
    }

}
