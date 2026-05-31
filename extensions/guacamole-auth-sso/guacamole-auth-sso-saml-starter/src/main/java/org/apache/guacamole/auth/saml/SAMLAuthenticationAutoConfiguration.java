package org.apache.guacamole.auth.saml;

import org.apache.guacamole.auth.saml.acs.AssertionConsumerServiceResource;
import org.apache.guacamole.auth.saml.acs.AuthenticationSessionManager;
import org.apache.guacamole.auth.saml.acs.IdentifierGenerator;
import org.apache.guacamole.auth.saml.acs.SAMLService;
import org.apache.guacamole.auth.saml.conf.ConfigurationService;
import org.apache.guacamole.auth.saml.user.SAMLAuthenticatedUser;
import org.apache.guacamole.auth.sso.SSOResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.sso-saml", name = "enabled", havingValue = "true")
public class SAMLAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SAMLAuthenticationAutoConfiguration.class);

    @Bean("samlSsoResource")
    public SAMLResource samlSsoResource() {
        return new SAMLResource();
    }

    @Bean("samlConfigurationService")
    public ConfigurationService samlConfigurationService() {
        return new ConfigurationService();
    }

    @Bean("samlIdentifierGenerator")
    public IdentifierGenerator samlIdentifierGenerator() {
        return new IdentifierGenerator();
    }

    @Bean("samlAuthenticationSessionManager")
    public AuthenticationSessionManager samlAuthenticationSessionManager() {
        return new AuthenticationSessionManager();
    }

    @Bean("samlService")
    public SAMLService samlService() {
        return new SAMLService();
    }

    @Bean("samlAuthenticationProviderService")
    public AuthenticationProviderService samlAuthenticationProviderService() {
        return new AuthenticationProviderService();
    }

    @Bean("samlAssertionConsumerServiceResource")
    public AssertionConsumerServiceResource samlAssertionConsumerServiceResource() {
        return new AssertionConsumerServiceResource();
    }

    @Bean
    @Qualifier("saml")
    @Scope("prototype")
    public SAMLAuthenticatedUser samlAuthenticatedUser() {
        return new SAMLAuthenticatedUser();
    }

    @Bean("samlAuthenticationProvider")
    public SAMLAuthenticationProvider samlAuthenticationProvider() {
        logger.info("SAML SSO authentication extension enabled.");
        return new SAMLAuthenticationProvider();
    }
}
