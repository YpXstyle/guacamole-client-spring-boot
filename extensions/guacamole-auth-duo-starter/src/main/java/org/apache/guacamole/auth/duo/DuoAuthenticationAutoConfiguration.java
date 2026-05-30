package org.apache.guacamole.auth.duo;

import org.apache.guacamole.auth.duo.api.DuoService;
import org.apache.guacamole.auth.duo.conf.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.duo", name = "enabled", havingValue = "true")
public class DuoAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DuoAuthenticationAutoConfiguration.class);

    @Bean
    public ConfigurationService duoConfigurationService() {
        return new ConfigurationService();
    }

    @Bean
    public DuoService duoApiService() {
        return new DuoService();
    }

    @Bean
    public UserVerificationService duoUserVerificationService() {
        return new UserVerificationService();
    }

    @Bean
    public DuoAuthenticationProvider duoAuthenticationProvider(
            UserVerificationService verificationService) {
        logger.info("Duo authentication extension enabled.");
        return new DuoAuthenticationProvider(verificationService);
    }
}
