package org.apache.guacamole.auth.radius;

import org.apache.guacamole.auth.radius.RadiusAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.radius", name = "enabled", havingValue = "true")
public class RadiusAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RadiusAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RadiusAuthenticationProvider radiusAuthenticationProvider() {
        logger.info("RADIUS authentication extension enabled.");
        return new RadiusAuthenticationProvider();
    }
}
