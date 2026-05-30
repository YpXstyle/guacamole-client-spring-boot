package org.apache.guacamole.auth.header;

import org.apache.guacamole.auth.header.user.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.header", name = "enabled", havingValue = "true")
public class HTTPHeaderAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(HTTPHeaderAuthenticationAutoConfiguration.class);

    @Bean("headerConfigurationService")
    public ConfigurationService headerConfigurationService() {
        return new ConfigurationService();
    }

    @Bean("headerAuthenticationProviderService")
    public AuthenticationProviderService headerAuthenticationProviderService() {
        return new AuthenticationProviderService();
    }

    @Bean
    @Scope("prototype")
    public AuthenticatedUser headerAuthenticatedUser() {
        return new AuthenticatedUser();
    }

    @Bean("headerAuthenticationProvider")
    public HTTPHeaderAuthenticationProvider headerAuthenticationProvider(
            AuthenticationProviderService authProviderService) {
        logger.info("HTTP Header authentication extension enabled.");
        return new HTTPHeaderAuthenticationProvider(authProviderService);
    }
}
