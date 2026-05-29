package org.apache.guacamole.auth.saml;

import org.apache.guacamole.auth.saml.SAMLAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.sso.saml", name = "enabled", havingValue = "true")
public class SAMLAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SAMLAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SAMLAuthenticationProvider sAMLAuthenticationProvider() {
        logger.info("SAML SSO authentication extension enabled.");
        return new SAMLAuthenticationProvider();
    }
}
