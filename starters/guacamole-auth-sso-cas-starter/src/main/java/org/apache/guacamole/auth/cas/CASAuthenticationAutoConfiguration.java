package org.apache.guacamole.auth.cas;

import org.apache.guacamole.auth.cas.CASAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.sso.cas", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = {"org.apache.guacamole.auth.cas", "org.apache.guacamole.auth.sso"})
public class CASAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CASAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CASAuthenticationProvider cASAuthenticationProvider() {
        logger.info("CAS SSO authentication extension enabled.");
        return new CASAuthenticationProvider();
    }
}
