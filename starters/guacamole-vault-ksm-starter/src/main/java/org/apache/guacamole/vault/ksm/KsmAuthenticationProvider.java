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

package org.apache.guacamole.vault.ksm;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.UserContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AuthenticationProvider implementation which reads secrets from Keeper
 * Secrets Manager
 */
public class KsmAuthenticationProvider extends AbstractAuthenticationProvider {

    @Autowired
    private org.apache.guacamole.vault.ksm.secret.KsmSecretService secretService;

    /**
     * Creates a new KsmKeyVaultAuthenticationProvider which reads secrets
     * from a configured Keeper Secrets Manager.
     */
    public KsmAuthenticationProvider() {
    }

    @Override
    public String getIdentifier() {
        return "keeper-secrets-manager";
    }

    @Override
    public UserContext decorate(UserContext context,
            AuthenticatedUser authenticatedUser, Credentials credentials)
            throws GuacamoleException {
        // Token injection is handled through the connection flow
        // via tokens returned by VaultSecretService
        return context;
    }

}
