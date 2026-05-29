package org.apache.guacamole.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.guacamole.auth.file.FileAuthenticationProvider;
import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.net.auth.AuthenticationProvider;
import org.apache.guacamole.net.event.listener.Listener;
import org.apache.guacamole.rest.auth.AuthTokenGenerator;
import org.apache.guacamole.rest.auth.HashTokenSessionMap;
import org.apache.guacamole.rest.auth.SecureRandomAuthTokenGenerator;
import org.apache.guacamole.rest.auth.TokenSessionMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreServicesConfig {

    @Bean
    public TokenSessionMap tokenSessionMap(Environment environment) {
        return new HashTokenSessionMap(environment);
    }

    @Bean
    public AuthTokenGenerator authTokenGenerator() {
        return new SecureRandomAuthTokenGenerator();
    }

    @Bean
    public FileAuthenticationProvider fileAuthenticationProvider() {
        return new FileAuthenticationProvider();
    }

    // List<AuthenticationProvider> and List<Listener> are auto-collected by Spring
    // from all AuthenticationProvider/Listener beans in the application context.
    // Do NOT define explicit @Bean for these lists - it would override auto-collection.

    @Bean
    public List<File> temporaryFiles() {
        return new ArrayList<>();
    }
}
