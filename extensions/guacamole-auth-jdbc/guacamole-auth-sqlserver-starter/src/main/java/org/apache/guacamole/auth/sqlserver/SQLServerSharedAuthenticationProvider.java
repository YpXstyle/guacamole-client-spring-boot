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

package org.apache.guacamole.auth.sqlserver;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.auth.jdbc.sharing.SharedAuthenticationProviderService;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.UserContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Provides an AuthenticationProvider implementation which interacts with the
 * SQLServer AuthenticationProvider, accepting share keys as credentials and
 * providing access to the shared connections.
 */
public class SQLServerSharedAuthenticationProvider extends AbstractAuthenticationProvider {

    @Autowired
    private SharedAuthenticationProviderService authProviderService;

    @Override
    public String getIdentifier() {
        return "sqlserver-shared";
    }

    @Override
    public AuthenticatedUser authenticateUser(Credentials credentials)
            throws GuacamoleException {
        return authProviderService.authenticateUser(this, credentials);
    }

    @Override
    public UserContext getUserContext(AuthenticatedUser authenticatedUser)
            throws GuacamoleException {
        return authProviderService.getUserContext(this, authenticatedUser);
    }

    @Override
    public UserContext updateUserContext(UserContext context,
            AuthenticatedUser authenticatedUser, Credentials credentials)
            throws GuacamoleException {
        return authProviderService.updateUserContext(this, context,
                authenticatedUser, credentials);
    }

}
