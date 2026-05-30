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

package org.apache.guacamole.vault;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.UserContext;
import org.apache.guacamole.vault.conf.VaultConfigurationService;
import org.apache.guacamole.vault.secret.VaultSecretService;
import org.apache.guacamole.vault.user.VaultUserContext;

/**
 * AuthenticationProvider implementation which automatically injects tokens
 * containing the values of secrets retrieved from a vault.
 */
public abstract class VaultAuthenticationProvider
        extends AbstractAuthenticationProvider {

    /**
     * The Guacamole server environment.
     */
    private final Environment environment;

    /**
     * Service for retrieving vault configuration.
     */
    private final VaultConfigurationService confService;

    /**
     * Service for retrieving secret values from the vault.
     */
    private final VaultSecretService secretService;

    /**
     * Creates a new VaultAuthenticationProvider which uses the given services
     * for vault integration.
     *
     * @param environment
     *     The Guacamole server environment.
     *
     * @param confService
     *     The VaultConfigurationService to use for retrieving configuration.
     *
     * @param secretService
     *     The VaultSecretService to use for retrieving secret values.
     *
     * @throws GuacamoleException
     *     If the properties file containing vault-mapped Guacamole
     *     configuration properties exists but cannot be read.
     */
    protected VaultAuthenticationProvider(Environment environment,
            VaultConfigurationService confService,
            VaultSecretService secretService) throws GuacamoleException {
        this.environment = environment;
        this.confService = confService;
        this.secretService = secretService;

        // Automatically pull properties from vault
        environment.addGuacamoleProperties(confService.getProperties());
    }

    @Override
    public UserContext decorate(UserContext context,
            AuthenticatedUser authenticatedUser, Credentials credentials)
            throws GuacamoleException {
        return new VaultUserContext(context, confService, secretService);
    }

}
