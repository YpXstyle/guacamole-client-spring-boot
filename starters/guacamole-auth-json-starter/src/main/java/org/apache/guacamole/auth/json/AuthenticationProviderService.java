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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.auth.json.user.AuthenticatedUser;
import org.apache.guacamole.auth.json.user.UserContext;
import org.apache.guacamole.auth.json.user.UserData;
import org.apache.guacamole.auth.json.user.UserDataService;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.credentials.CredentialsInfo;
import org.apache.guacamole.net.auth.credentials.GuacamoleInvalidCredentialsException;

/**
 * Service providing convenience functions for the JSONAuthenticationProvider.
 */
public class AuthenticationProviderService {

    /**
     * Service for deriving Guacamole extension API data from UserData objects.
     */
    @Autowired
    private UserDataService userDataService;

    /**
     * Provider for AuthenticatedUser objects.
     */
    @Autowired
    private ObjectProvider<AuthenticatedUser> authenticatedUserProvider;

    /**
     * Provider for UserContext objects.
     */
    @Autowired
    private ObjectProvider<UserContext> userContextProvider;

    /**
     * Returns an AuthenticatedUser representing the user authenticated by the
     * given credentials.
     *
     * @param credentials
     *     The credentials to use for authentication.
     *
     * @return
     *     An AuthenticatedUser representing the user authenticated by the
     *     given credentials.
     *
     * @throws GuacamoleException
     *     If an error occurs while authenticating the user, or if access is
     *     denied.
     */
    public AuthenticatedUser authenticateUser(Credentials credentials)
            throws GuacamoleException {

        // If the request does not contain the "data" parameter, this is not
        // a JSON auth attempt - return null to let other providers handle it
        HttpServletRequest request = credentials.getRequest();
        if (request == null || request.getParameter(UserDataService.ENCRYPTED_DATA_PARAMETER) == null)
            return null;

        // Pull UserData from credentials, if possible
        UserData userData = userDataService.fromCredentials(credentials);
        if (userData == null)
            throw new GuacamoleInvalidCredentialsException("Permission denied.", CredentialsInfo.EMPTY);

        // Produce AuthenticatedUser associated with derived UserData
        AuthenticatedUser authenticatedUser = authenticatedUserProvider.getObject();
        authenticatedUser.init(credentials, userData);
        return authenticatedUser;

    }

    /**
     * Returns a UserContext object initialized with data accessible to the
     * given AuthenticatedUser.
     *
     * @param authenticatedUser
     *     The AuthenticatedUser to retrieve data for.
     *
     * @return
     *     A UserContext object initialized with data accessible to the given
     *     AuthenticatedUser.
     *
     * @throws GuacamoleException
     *     If the UserContext cannot be created due to an error.
     */
    public UserContext getUserContext(org.apache.guacamole.net.auth.AuthenticatedUser authenticatedUser)
            throws GuacamoleException {

        // The JSONAuthenticationProvider only provides data for users it has
        // authenticated itself
        if (!(authenticatedUser instanceof AuthenticatedUser))
            return null;

        // Return UserContext containing data from the authenticated user's
        // associated UserData object
        UserContext userContext = userContextProvider.getObject();
        userContext.init(((AuthenticatedUser) authenticatedUser).getUserData());
        return userContext;

    }

}
