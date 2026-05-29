package org.apache.guacamole.config;

import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.environment.LocalEnvironment;
import org.apache.guacamole.SystemEnvironmentGuacamoleProperties;
import org.apache.guacamole.properties.GuacamoleProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EnvironmentConfig {

    @Autowired
    private org.springframework.core.env.Environment springEnv;

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
                // Check under each guacamole sub-namespace
                // e.g., "json-secret-key" → "guacamole.auth.json.json-secret-key"
                // e.g., "mysql-hostname"   → "guacamole.auth.mysql.hostname"
                for (String prefix : new String[]{
                        "guacamole.auth.header", "guacamole.auth.duo",
                        "guacamole.auth.json", "guacamole.auth.ldap",
                        "guacamole.auth.totp", "guacamole.auth.radius",
                        "guacamole.auth.quickconnect", "guacamole.auth.sso-cas",
                        "guacamole.auth.sso-openid", "guacamole.auth.sso-saml",
                        "guacamole.vault.ksm", "guacamole.history"}) {
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
