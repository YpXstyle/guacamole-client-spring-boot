package org.apache.guacamole.auth.json;

import org.apache.guacamole.auth.json.connection.ConnectionService;
import org.apache.guacamole.auth.json.user.AuthenticatedUser;
import org.apache.guacamole.auth.json.user.UserContext;
import org.apache.guacamole.auth.json.user.UserDataConnection;
import org.apache.guacamole.auth.json.user.UserDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.json", name = "enabled", havingValue = "true")
public class JSONAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JSONAuthenticationAutoConfiguration.class);

    @Bean("jsonConfigurationService")
    public ConfigurationService jsonConfigurationService() {
        return new ConfigurationService();
    }

    @Bean
    public CryptoService jsonCryptoService() {
        return new CryptoService();
    }

    @Bean
    public RequestValidationService jsonRequestValidationService(ConfigurationService confService) {
        return new RequestValidationService(confService);
    }

    @Bean("jsonConnectionService")
    public ConnectionService jsonConnectionService() {
        return new ConnectionService();
    }

    @Bean
    public UserDataService jsonUserDataService() {
        return new UserDataService();
    }

    @Bean
    @Scope("prototype")
    public UserDataConnection jsonUserDataConnection() {
        return new UserDataConnection();
    }

    @Bean
    @Scope("prototype")
    public AuthenticatedUser jsonAuthenticatedUser() {
        return new AuthenticatedUser();
    }

    @Bean
    @Scope("prototype")
    public UserContext jsonUserContext() {
        return new UserContext();
    }

    @Bean("jsonAuthenticationProviderService")
    public AuthenticationProviderService jsonAuthenticationProviderService() {
        return new AuthenticationProviderService();
    }

    @Bean("jsonAuthenticationProvider")
    public JSONAuthenticationProvider jSONAuthenticationProvider(
            AuthenticationProviderService authProviderService) {
        logger.info("JSON authentication extension enabled.");
        return new JSONAuthenticationProvider(authProviderService);
    }
}
