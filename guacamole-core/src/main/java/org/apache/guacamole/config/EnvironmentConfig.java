package org.apache.guacamole.config;

import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.environment.LocalEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EnvironmentConfig {

    @Bean
    @Primary
    public Environment guacamoleEnvironment() {
        return LocalEnvironment.getInstance();
    }
}
