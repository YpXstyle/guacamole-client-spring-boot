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
import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.vault.VaultAuthenticationProvider;
import org.apache.guacamole.vault.ksm.conf.KsmConfigurationService;
import org.apache.guacamole.vault.ksm.secret.KsmSecretService;

/**
 * VaultAuthenticationProvider implementation which reads secrets from Keeper
 * Secrets Manager.
 */
public class KsmAuthenticationProvider extends VaultAuthenticationProvider {

    /**
     * Creates a new KsmAuthenticationProvider which reads secrets
     * from a configured Keeper Secrets Manager.
     *
     * @param environment
     *     The Guacamole server environment.
     *
     * @param confService
     *     The KsmConfigurationService to use for retrieving configuration.
     *
     * @param secretService
     *     The KsmSecretService to use for retrieving secret values.
     *
     * @throws GuacamoleException
     *     If the properties file containing vault-mapped Guacamole
     *     configuration properties exists but cannot be read.
     */
    public KsmAuthenticationProvider(Environment environment,
            KsmConfigurationService confService,
            KsmSecretService secretService) throws GuacamoleException {
        super(environment, confService, secretService);
    }

    @Override
    public String getIdentifier() {
        return "keeper-secrets-manager";
    }

}
