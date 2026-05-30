package org.apache.guacamole.config;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import jakarta.ws.rs.ApplicationPath;

@Configuration
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {

    @Autowired
    public JerseyConfig(Environment springEnv) {
        // Bridge Jersey logging (java.util.logging) to SLF4J
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // Scan for REST resource classes
        packages("org.apache.guacamole.rest");
        packages("org.apache.guacamole.auth.sso");

        // Only scan SAML package if SAML is enabled
        boolean samlEnabled = springEnv.getProperty("guacamole.auth.sso-saml.enabled", Boolean.class, false);
        if (samlEnabled) {
            packages("org.apache.guacamole.auth.saml");
        }

        // Use Jackson for JSON serialization
        register(JacksonFeature.class);
    }
}
