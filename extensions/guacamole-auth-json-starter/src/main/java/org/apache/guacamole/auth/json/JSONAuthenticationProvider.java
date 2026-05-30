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

package org.apache.guacamole.auth.json;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.UserContext;

/**
 * Allows users to be authenticated using encrypted blobs of JSON data. The
 * username of the user, all available connections, and the parameters
 * associated with those connections are all determined by the contents of the
 * provided JSON. The JSON itself is authorized by virtue of being properly
 * encrypted with a shared key.
 */
public class JSONAuthenticationProvider extends AbstractAuthenticationProvider {

    /**
     * Service for authenticating users via encrypted JSON blobs.
     */
    private final AuthenticationProviderService authProviderService;

    /**
     * Creates a new JSONAuthenticationProvider with the given service.
     *
     * @param authProviderService
     *     Service for authenticating users via encrypted JSON blobs.
     */
    public JSONAuthenticationProvider(AuthenticationProviderService authProviderService) {
        this.authProviderService = authProviderService;
    }

    @Override
    public String getIdentifier() {
        return "json";
    }

    @Override
    public AuthenticatedUser authenticateUser(Credentials credentials) throws GuacamoleException {

        return authProviderService.authenticateUser(credentials);

    }

    @Override
    public UserContext getUserContext(AuthenticatedUser authenticatedUser)
            throws GuacamoleException {

        return authProviderService.getUserContext(authenticatedUser);

    }

}
