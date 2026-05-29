package org.apache.guacamole.auth.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.json", name = "enabled", havingValue = "true")
public class JSONAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(JSONAuthenticationAutoConfiguration.class);

    @Bean("jsonAuthenticationProvider")
    @ConditionalOnMissingBean
    public JSONAuthenticationProvider jSONAuthenticationProvider(
            AuthenticationProviderService authProviderService) {
        logger.info("JSON authentication extension enabled.");
        return new JSONAuthenticationProvider(authProviderService);
    }
}
