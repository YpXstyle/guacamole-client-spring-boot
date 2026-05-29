package org.apache.guacamole.auth.ldap;

import org.apache.guacamole.auth.ldap.LDAPAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.ldap", name = "enabled", havingValue = "true")
public class LDAPAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LDAPAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public LDAPAuthenticationProvider lDAPAuthenticationProvider() {
        logger.info("LDAP authentication extension enabled.");
        return new LDAPAuthenticationProvider();
    }
}
