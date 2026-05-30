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

package org.apache.guacamole.auth.sso;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.auth.sso.user.SSOAuthenticatedUser;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.TokenInjectingUserContext;
import org.apache.guacamole.net.auth.UserContext;

/**
 * An AuthenticationProvider which authenticates users against an arbitrary
 * SSO system. Core authentication functions are provided by the injected
 * SSOAuthenticationProviderService implementation.
 */
public abstract class SSOAuthenticationProvider extends AbstractAuthenticationProvider {

    /**
     * Returns the SSOAuthenticationProviderService implementation that should
     * be used for core authentication functions. Each SSO module must provide
     * its own implementation.
     *
     * @return
     *     The SSOAuthenticationProviderService implementation for this module.
     */
    protected abstract SSOAuthenticationProviderService getAuthService();

    /**
     * Returns the SSOResource that should be used for SSO-related REST
     * endpoints. Each SSO module must provide its own implementation.
     *
     * @return
     *     The SSOResource implementation for this module.
     */
    protected abstract SSOResource getSsoResource();

    @Override
    public AuthenticatedUser authenticateUser(Credentials credentials)
            throws GuacamoleException {
        return getAuthService().authenticateUser(credentials);
    }

    @Override
    public UserContext decorate(UserContext context,
            AuthenticatedUser authenticatedUser, Credentials credentials)
            throws GuacamoleException {

        // Only inject tokens for users authenticated by this extension
        if (authenticatedUser.getAuthenticationProvider() != this)
            return context;

        return new TokenInjectingUserContext(context,
                ((SSOAuthenticatedUser) authenticatedUser).getTokens());
    }

    @Override
    public SSOResource getResource() {
        return getSsoResource();
    }

    @Override
    public void shutdown() {
        getAuthService().shutdown();
    }

}
