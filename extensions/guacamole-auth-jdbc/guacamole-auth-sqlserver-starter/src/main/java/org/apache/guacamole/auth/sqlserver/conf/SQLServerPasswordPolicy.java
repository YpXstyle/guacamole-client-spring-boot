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

package org.apache.guacamole.auth.sqlserver.conf;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.auth.jdbc.JDBCEnvironment;
import org.apache.guacamole.auth.jdbc.security.PasswordPolicy;
import org.apache.guacamole.auth.jdbc.system.SystemConfigService;
import org.apache.guacamole.properties.BooleanGuacamoleProperty;
import org.apache.guacamole.properties.IntegerGuacamoleProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PasswordPolicy implementation which reads the details of the policy from
 * SQLServer-specific properties in guacamole.properties.
 */
public class SQLServerPasswordPolicy implements PasswordPolicy {

    private static final Logger logger = LoggerFactory.getLogger(SQLServerPasswordPolicy.class);

    /**
     * The property which specifies the minimum length required of all user
     * passwords. By default, this will be zero.
     */
    private static final IntegerGuacamoleProperty MIN_LENGTH =
            new IntegerGuacamoleProperty() {

        @Override
        public String getName() { return "sqlserver-user-password-min-length"; }

    };

    /**
     * The property which specifies the minimum number of days which must
     * elapse before a user may reset their password. If set to zero, the
     * default, then this restriction does not apply.
     */
    private static final IntegerGuacamoleProperty MIN_AGE =
            new IntegerGuacamoleProperty() {

        @Override
        public String getName() { return "sqlserver-user-password-min-age"; }

    };

    /**
     * The property which specifies the maximum number of days which may
     * elapse before a user is required to reset their password. If set to zero,
     * the default, then this restriction does not apply.
     */
    private static final IntegerGuacamoleProperty MAX_AGE =
            new IntegerGuacamoleProperty() {

        @Override
        public String getName() { return "sqlserver-user-password-max-age"; }

    };

    /**
     * The property which specifies the number of previous passwords remembered
     * for each user. If set to zero, the default, then this restriction does
     * not apply.
     */
    private static final IntegerGuacamoleProperty HISTORY_SIZE =
            new IntegerGuacamoleProperty() {

        @Override
        public String getName() { return "sqlserver-user-password-history-size"; }

    };

    /**
     * The property which specifies whether all user passwords must have at
     * least one lowercase character and one uppercase character. By default,
     * no such restriction is imposed.
     */
    private static final BooleanGuacamoleProperty REQUIRE_MULTIPLE_CASE =
            new BooleanGuacamoleProperty() {

        @Override
        public String getName() { return "sqlserver-user-password-require-multiple-case"; }

    };

    /**
     * The property which specifies whether all user passwords must have at
     * least one numeric character (digit). By default, no such restriction is
     * imposed.
     */
    private static final BooleanGuacamoleProperty REQUIRE_DIGIT =
            new BooleanGuacamoleProperty() {

        @Override
        public String getName() { return "sqlserver-user-password-require-digit"; }

    };

    /**
     * The property which specifies whether all user passwords must have at
     * least one non-alphanumeric character (symbol). By default, no such
     * restriction is imposed.
     */
    private static final BooleanGuacamoleProperty REQUIRE_SYMBOL =
            new BooleanGuacamoleProperty() {

        @Override
        public String getName() { return "sqlserver-user-password-require-symbol"; }

    };

    /**
     * The property which specifies whether users are prohibited from including
     * their own username in their password. By default, no such restriction is
     * imposed.
     */
    private static final BooleanGuacamoleProperty PROHIBIT_USERNAME =
            new BooleanGuacamoleProperty() {

        @Override
        public String getName() { return "sqlserver-user-password-prohibit-username"; }

    };

    /**
     * The Guacamole server environment.
     */
    private final JDBCEnvironment environment;

    /**
     * Service for reading system configuration from the database.
     * May be null if the system config module is not available.
     */
    private final SystemConfigService systemConfigService;

    /**
     * Creates a new SQLServerPasswordPolicy which reads the details of the
     * policy from the properties exposed by the given environment, with
     * optional overrides from the database-backed system configuration.
     *
     * @param environment
     *     The environment from which password policy properties should be
     *     read as fallback defaults.
     *
     * @param systemConfigService
     *     The SystemConfigService for reading DB config values, or null.
     */
    public SQLServerPasswordPolicy(JDBCEnvironment environment,
            SystemConfigService systemConfigService) {
        this.environment = environment;
        this.systemConfigService = systemConfigService;
    }

    private String getDBValue(String key) {
        if (systemConfigService == null) return null;
        try {
            return systemConfigService.getValue(key);
        } catch (Exception e) {
            logger.warn("Failed to read DB config key '{}', falling back to defaults", key, e);
            return null;
        }
    }

    @Override
    public int getMinimumLength() throws GuacamoleException {
        String dbVal = getDBValue("security.password_min_length");
        if (dbVal != null && !dbVal.isEmpty()) {
            try {
                return Integer.parseInt(dbVal);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer in DB for password_min_length: '{}', using default", dbVal);
            }
        }
        return environment.getProperty(MIN_LENGTH, 0);
    }

    @Override
    public int getMinimumAge() throws GuacamoleException {
        return environment.getProperty(MIN_AGE, 0);
    }

    @Override
    public int getMaximumAge() throws GuacamoleException {
        return environment.getProperty(MAX_AGE, 0);
    }

    @Override
    public int getHistorySize() throws GuacamoleException {
        return environment.getProperty(HISTORY_SIZE, 0);
    }

    @Override
    public boolean isMultipleCaseRequired() throws GuacamoleException {
        String dbVal = getDBValue("security.password_require_uppercase");
        if (dbVal != null && !dbVal.isEmpty()) {
            return Boolean.parseBoolean(dbVal);
        }
        return environment.getProperty(REQUIRE_MULTIPLE_CASE, false);
    }

    @Override
    public boolean isNumericRequired() throws GuacamoleException {
        String dbVal = getDBValue("security.password_require_number");
        if (dbVal != null && !dbVal.isEmpty()) {
            return Boolean.parseBoolean(dbVal);
        }
        return environment.getProperty(REQUIRE_DIGIT, false);
    }

    @Override
    public boolean isNonAlphanumericRequired() throws GuacamoleException {
        String dbVal = getDBValue("security.password_require_special");
        if (dbVal != null && !dbVal.isEmpty()) {
            return Boolean.parseBoolean(dbVal);
        }
        return environment.getProperty(REQUIRE_SYMBOL, false);
    }

    @Override
    public boolean isUsernameProhibited() throws GuacamoleException {
        return environment.getProperty(PROHIBIT_USERNAME, false);
    }

}
