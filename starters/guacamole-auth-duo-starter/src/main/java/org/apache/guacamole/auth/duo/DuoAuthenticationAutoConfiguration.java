package org.apache.guacamole.auth.duo;

import org.apache.guacamole.auth.duo.DuoAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.duo", name = "enabled", havingValue = "true")
public class DuoAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DuoAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public DuoAuthenticationProvider duoAuthenticationProvider() {
        logger.info("Duo authentication extension enabled.");
        return new DuoAuthenticationProvider();
    }
}
