package org.apache.guacamole.auth.ldap;

import org.apache.guacamole.auth.ldap.conf.ConfigurationService;
import org.apache.guacamole.auth.ldap.connection.ConnectionService;
import org.apache.guacamole.auth.ldap.group.UserGroupService;
import org.apache.guacamole.auth.ldap.user.LDAPAuthenticatedUser;
import org.apache.guacamole.auth.ldap.user.LDAPUserContext;
import org.apache.guacamole.auth.ldap.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.ldap", name = "enabled", havingValue = "true")
public class LDAPAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LDAPAuthenticationAutoConfiguration.class);

    @Bean("ldapConfigurationService")
    public ConfigurationService ldapConfigurationService() {
        return new ConfigurationService();
    }

    @Bean("ldapConnectionService")
    public ConnectionService ldapConnectionService() {
        return new ConnectionService();
    }

    @Bean("ldapLDAPConnectionService")
    public LDAPConnectionService ldapLDAPConnectionService() {
        return new LDAPConnectionService();
    }

    @Bean("ldapObjectQueryService")
    public ObjectQueryService ldapObjectQueryService() {
        return new ObjectQueryService();
    }

    @Bean("ldapUserGroupService")
    public UserGroupService ldapUserGroupService() {
        return new UserGroupService();
    }

    @Bean("ldapUserService")
    public UserService ldapUserService() {
        return new UserService();
    }

    @Bean("ldapAuthenticationProviderService")
    public AuthenticationProviderService ldapAuthenticationProviderService() {
        return new AuthenticationProviderService();
    }

    @Bean("ldapAuthenticationProvider")
    public LDAPAuthenticationProvider ldapAuthenticationProvider() {
        logger.info("LDAP authentication extension enabled.");
        return new LDAPAuthenticationProvider();
    }

    @Bean
    @Scope("prototype")
    public LDAPAuthenticatedUser ldapAuthenticatedUser() {
        return new LDAPAuthenticatedUser();
    }

    @Bean
    @Scope("prototype")
    public LDAPUserContext ldapUserContext() {
        return new LDAPUserContext();
    }
}
