package org.apache.guacamole.auth.openid;

import org.apache.guacamole.auth.openid.OpenIDAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.sso.openid", name = "enabled", havingValue = "true")
public class OpenIDAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(OpenIDAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OpenIDAuthenticationProvider openIDAuthenticationProvider() {
        logger.info("OpenID Connect SSO authentication extension enabled.");
        return new OpenIDAuthenticationProvider();
    }
}
