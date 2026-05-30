package org.apache.guacamole.auth.openid;

import org.apache.guacamole.auth.openid.conf.ConfigurationService;
import org.apache.guacamole.auth.openid.token.NonceService;
import org.apache.guacamole.auth.openid.token.TokenValidationService;
import org.apache.guacamole.auth.sso.SSOResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.sso-openid", name = "enabled", havingValue = "true")
public class OpenIDAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(OpenIDAuthenticationAutoConfiguration.class);

    @Bean("openIdSsoResource")
    public OpenIDResource openIdSsoResource() {
        return new OpenIDResource();
    }

    @Bean("openIdConfigurationService")
    public ConfigurationService openIdConfigurationService() {
        return new ConfigurationService();
    }

    @Bean("openIdNonceService")
    public NonceService openIdNonceService() {
        return new NonceService();
    }

    @Bean("openIdTokenValidationService")
    public TokenValidationService openIdTokenValidationService() {
        return new TokenValidationService();
    }

    @Bean("openIdAuthenticationProviderService")
    public AuthenticationProviderService openIdAuthenticationProviderService() {
        return new AuthenticationProviderService();
    }

    @Bean("openIdAuthenticationProvider")
    public OpenIDAuthenticationProvider openIdAuthenticationProvider() {
        logger.info("OpenID Connect SSO authentication extension enabled.");
        return new OpenIDAuthenticationProvider();
    }
}
