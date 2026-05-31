package org.apache.guacamole.auth.cas;

import org.apache.guacamole.auth.cas.conf.ConfigurationService;
import org.apache.guacamole.auth.cas.ticket.TicketValidationService;
import org.apache.guacamole.auth.sso.SSOResource;
import org.apache.guacamole.auth.sso.user.SSOAuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.sso-cas", name = "enabled", havingValue = "true")
public class CASAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CASAuthenticationAutoConfiguration.class);

    @Bean("casSsoResource")
    public CASResource casSsoResource() {
        return new CASResource();
    }

    @Bean("casConfigurationService")
    public ConfigurationService casConfigurationService() {
        return new ConfigurationService();
    }

    @Bean("casTicketValidationService")
    public TicketValidationService casTicketValidationService() {
        return new TicketValidationService();
    }

    @Bean("casAuthenticationProviderService")
    public AuthenticationProviderService casAuthenticationProviderService() {
        return new AuthenticationProviderService();
    }

    @Bean
    @Qualifier("cas")
    @Scope("prototype")
    public SSOAuthenticatedUser casAuthenticatedUser() {
        return new SSOAuthenticatedUser();
    }

    @Bean("casAuthenticationProvider")
    public CASAuthenticationProvider casAuthenticationProvider() {
        logger.info("CAS SSO authentication extension enabled.");
        return new CASAuthenticationProvider();
    }
}
