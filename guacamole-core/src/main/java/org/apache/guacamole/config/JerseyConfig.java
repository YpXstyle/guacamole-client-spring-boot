package org.apache.guacamole.config;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.annotation.Configuration;
import jakarta.ws.rs.ApplicationPath;

@Configuration
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        // Bridge Jersey logging (java.util.logging) to SLF4J
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // Scan for REST resource classes
        packages("org.apache.guacamole.rest");

        // Use Jackson for JSON serialization
        register(JacksonFeature.class);
    }
}
