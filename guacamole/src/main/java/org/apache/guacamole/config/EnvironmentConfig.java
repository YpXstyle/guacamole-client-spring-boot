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

package org.apache.guacamole.config;

import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.environment.LocalEnvironment;
import org.apache.guacamole.SystemEnvironmentGuacamoleProperties;
import org.apache.guacamole.properties.GuacamoleProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import jakarta.annotation.PostConstruct;

@Configuration
public class EnvironmentConfig {

    @Autowired
    private org.springframework.core.env.Environment springEnv;

    @PostConstruct
    public void validateModules() {
        // Validate JDBC modules (only one can be enabled)
        boolean mysql = springEnv.getProperty("guacamole.auth.mysql.enabled", Boolean.class, false);
        boolean postgresql = springEnv.getProperty("guacamole.auth.postgresql.enabled", Boolean.class, false);
        boolean sqlserver = springEnv.getProperty("guacamole.auth.sqlserver.enabled", Boolean.class, false);

        int jdbcCount = 0;
        if (mysql) jdbcCount++;
        if (postgresql) jdbcCount++;
        if (sqlserver) jdbcCount++;

        if (jdbcCount > 1) {
            throw new IllegalStateException(
                "Only one JDBC authentication module can be enabled at a time. "
                + "Currently enabled: "
                + (mysql ? "[MySQL] " : "")
                + (postgresql ? "[PostgreSQL] " : "")
                + (sqlserver ? "[SQLServer] " : "")
                + ". Please disable all but one in application.yml.");
        }

        // Validate SSO modules (only one can be enabled)
        boolean cas = springEnv.getProperty("guacamole.auth.sso-cas.enabled", Boolean.class, false);
        boolean openid = springEnv.getProperty("guacamole.auth.sso-openid.enabled", Boolean.class, false);
        boolean saml = springEnv.getProperty("guacamole.auth.sso-saml.enabled", Boolean.class, false);

        int ssoCount = 0;
        if (cas) ssoCount++;
        if (openid) ssoCount++;
        if (saml) ssoCount++;

        if (ssoCount > 1) {
            throw new IllegalStateException(
                "Only one SSO authentication module can be enabled at a time. "
                + "Currently enabled: "
                + (cas ? "[CAS] " : "")
                + (openid ? "[OpenID] " : "")
                + (saml ? "[SAML] " : "")
                + ". Please disable all but one in application.yml.");
        }
    }

    @Bean
    @Primary
    public Environment guacamoleEnvironment() {
        LocalEnvironment env = LocalEnvironment.getInstance();
        env.addGuacamoleProperties(new GuacamoleProperties() {
            @Override
            public String getProperty(String name) {
                // Check exact name (flat Guacamole property name)
                String value = springEnv.getProperty(name);
                if (value != null)
                    return value;

                // Map legacy guacd property names to their Spring namespace keys
                // e.g., "guacd-hostname" → "guacamole.guacd.hostname"
                if ("guacd-hostname".equals(name))
                    return springEnv.getProperty("guacamole.guacd.hostname");
                if ("guacd-port".equals(name))
                    return springEnv.getProperty("guacamole.guacd.port");
                if ("guacd-ssl".equals(name))
                    return springEnv.getProperty("guacamole.guacd.ssl");

                // Check under each guacamole sub-namespace
                // e.g., "json-secret-key" → "guacamole.auth.json.json-secret-key"
                // e.g., "mysql-hostname"   → "guacamole.auth.mysql.mysql-hostname"
                for (String prefix : new String[]{
                        "guacamole.auth.mysql", "guacamole.auth.postgresql",
                        "guacamole.auth.sqlserver", "guacamole.auth.header",
                        "guacamole.auth.duo", "guacamole.auth.json",
                        "guacamole.auth.ldap", "guacamole.auth.totp",
                        "guacamole.auth.radius", "guacamole.auth.quickconnect",
                        "guacamole.auth.sso-cas", "guacamole.auth.sso-openid",
                        "guacamole.auth.sso-saml", "guacamole.vault.ksm",
                        "guacamole.history",
                        "guacamole.guacd"}) {
                    value = springEnv.getProperty(prefix + "." + name);
                    if (value != null)
                        return value;
                }
                return null;
            }
        });
        env.addGuacamoleProperties(new SystemEnvironmentGuacamoleProperties());
        return env;
    }
}
