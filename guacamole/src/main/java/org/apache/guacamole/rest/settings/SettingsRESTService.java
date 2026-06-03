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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleSecurityException;
import org.apache.guacamole.GuacamoleServerException;
import org.apache.guacamole.GuacamoleSession;
import org.apache.guacamole.auth.jdbc.system.SystemConfigModel;
import org.apache.guacamole.auth.jdbc.system.SystemConfigService;
import org.apache.guacamole.rest.auth.DecoratedUserContext;
import org.apache.guacamole.net.auth.permission.SystemPermission;
import org.apache.guacamole.net.auth.permission.SystemPermissionSet;
import org.apache.guacamole.rest.TokenParam;
import org.apache.guacamole.rest.auth.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST service for system configuration management (GET/PUT/DELETE /api/settings).
 * Requires SYSTEM_ADMINISTER permission.
 */
@Path("/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsRESTService {

    /**
     * Pattern for validating hex color values (#RRGGBB).
     */
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired(required = false)
    private SystemConfigService configService;

    /**
     * Verifies that the user has SYSTEM_ADMINISTER permission.
     *
     * @param authToken
     *     The authentication token.
     *
     * @throws GuacamoleException
     *     If the token is invalid or the user lacks permission.
     */
    private void verifyAdminPermission(String authToken) throws GuacamoleException {
        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        List<DecoratedUserContext> userContexts = session.getUserContexts();

        if (userContexts.isEmpty()) {
            throw new GuacamoleSecurityException("No user context available");
        }

        DecoratedUserContext userContext = userContexts.get(0);
        SystemPermissionSet permissions = userContext.self().getSystemPermissions();

        if (!permissions.hasPermission(SystemPermission.Type.ADMINISTER)) {
            throw new GuacamoleSecurityException(
                "SYSTEM_ADMINISTER permission required");
        }
    }

    /**
     * Returns all system configuration entries with metadata.
     *
     * @param authToken
     *     The authentication token.
     *
     * @return
     *     A list of all configuration entries.
     *
     * @throws GuacamoleException
     *     If the token is invalid or the user lacks permission.
     */
    @GET
    public List<APISettingDetail> getAllSettings(@TokenParam String authToken)
            throws GuacamoleException {

        verifyAdminPermission(authToken);

        // If no JDBC extension is enabled, return empty list
        if (configService == null) {
            return new ArrayList<>();
        }

        List<SystemConfigModel> entries = configService.getAllEntries();
        List<APISettingDetail> result = new ArrayList<>();

        for (SystemConfigModel entry : entries) {
            APISettingDetail detail = new APISettingDetail();
            detail.setKey(entry.getConfigKey());
            detail.setValue(entry.getConfigValue());
            detail.setType(entry.getConfigType());
            detail.setGroup(entry.getConfigGroup());
            detail.setUpdatedBy(entry.getUpdatedBy());
            detail.setUpdatedAt(entry.getUpdatedAt());
            result.add(detail);
        }

        return result;
    }

    /**
     * Updates a single configuration entry.
     *
     * @param authToken
     *     The authentication token.
     *
     * @param key
     *     The configuration key to update.
     *
     * @param update
     *     The update request containing the new value.
     *
     * @return
     *     The updated configuration entry.
     *
     * @throws GuacamoleException
     *     If the token is invalid, the user lacks permission,
     *     or the value is invalid.
     */
    @PUT
    @Path("/{key}")
    public APISettingDetail updateSetting(@TokenParam String authToken,
            @PathParam("key") String key, APISettingUpdate update)
            throws GuacamoleException {

        verifyAdminPermission(authToken);

        if (configService == null) {
            throw new GuacamoleServerException(
                "System configuration is not available. Enable a JDBC extension first.");
        }

        String value = update.getValue();

        // Validate color values
        if (key.startsWith("theme.") && key.endsWith("_color") && value != null) {
            if (!HEX_COLOR_PATTERN.matcher(value).matches()) {
                throw new GuacamoleClientException(
                    "Invalid color value: " + value + ". Expected format: #RRGGBB");
            }
        }

        // Treat null/empty as empty string (never delete the config row)
        if (value == null) {
            value = "";
        }

        // Get username for audit
        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        String username = session.getAuthenticatedUser().getIdentifier();
        configService.updateValue(key, value, username);

        // Return updated entry
        APISettingDetail detail = new APISettingDetail();
        detail.setKey(key);
        detail.setValue(value);

        return detail;
    }

}
