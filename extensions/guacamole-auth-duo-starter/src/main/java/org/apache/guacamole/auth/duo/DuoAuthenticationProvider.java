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

package org.apache.guacamole.auth.duo;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.UserContext;

/**
 * AuthenticationProvider implementation which uses Duo as an additional
 * authentication factor for users which have already been authenticated by
 * some other AuthenticationProvider.
 */
public class DuoAuthenticationProvider extends AbstractAuthenticationProvider {

    /**
     * Service for verifying users against Duo.
     */
    private final UserVerificationService verificationService;

    /**
     * Creates a new DuoAuthenticationProvider with the given verification service.
     *
     * @param verificationService
     *     Service for verifying users against Duo.
     */
    public DuoAuthenticationProvider(UserVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Override
    public String getIdentifier() {
        return "duo";
    }

    @Override
    public UserContext getUserContext(AuthenticatedUser authenticatedUser)
            throws GuacamoleException {

        // Verify user against Duo service
        verificationService.verifyAuthenticatedUser(authenticatedUser);

        // User has been verified, and authentication should be allowed to
        // continue
        return null;

    }

}
