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

package org.apache.guacamole.vault.secret;

import java.util.Map;
import java.util.concurrent.Future;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.apache.guacamole.token.TokenFilter;

/**
 * Generic service for retrieving the value of a secret stored in a vault.
 */
public interface VaultSecretService {

    /**
     * Translates an arbitrary string, which may contain characters not allowed
     * by the vault implementation, into a string which is valid within a
     * secret name.
     *
     * @param nameComponent
     *     An arbitrary string intended for use within a secret name.
     *
     * @return
     *     A string transformed deterministically such that it is acceptable
     *     as a component of a secret name by the vault provider.
     */
    String canonicalize(String nameComponent);

    /**
     * Returns a Future which eventually completes with the value of the secret
     * having the given name. If no such secret exists, the Future will be
     * completed with null.
     *
     * @param name
     *     The name of the secret to retrieve.
     *
     * @return
     *     A Future which completes with value of the secret having the given
     *     name.
     *
     * @throws GuacamoleException
     *     If the secret cannot be retrieved due to an error.
     */
    Future<String> getValue(String name) throws GuacamoleException;

    /**
     * Returns a map of token names to corresponding Futures which eventually
     * complete with the value of that token, where each token is dynamically
     * defined based on connection parameters.
     *
     * @param config
     *     The configuration of the Guacamole connection for which tokens are
     *     being generated.
     *
     * @param filter
     *     A TokenFilter instance that applies any tokens already available.
     *
     * @return
     *     A map of token names to their corresponding future values.
     *
     * @throws GuacamoleException
     *     If an error occurs producing the tokens and values.
     */
    Map<String, Future<String>> getTokens(GuacamoleConfiguration config,
            TokenFilter filter) throws GuacamoleException;

}
