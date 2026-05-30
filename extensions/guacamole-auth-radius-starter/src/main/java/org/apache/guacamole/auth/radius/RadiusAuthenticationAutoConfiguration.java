package org.apache.guacamole.auth.radius;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.auth.radius.conf.ConfigurationService;
import org.apache.guacamole.auth.radius.conf.RadiusAuthenticationProtocol;
import org.apache.guacamole.auth.radius.conf.RadiusGuacamoleProperties;
import org.apache.guacamole.auth.radius.user.AuthenticatedUser;
import org.apache.guacamole.environment.Environment;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.radius", name = "enabled", havingValue = "true")
public class RadiusAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RadiusAuthenticationAutoConfiguration.class);

    @Autowired
    private Environment environment;

    @Bean("radiusConfigurationService")
    public ConfigurationService radiusConfigurationService() throws GuacamoleException {
        // Check for MD4 requirement (MSCHAP protocols need BouncyCastle)
        RadiusAuthenticationProtocol authProtocol = environment.getProperty(RadiusGuacamoleProperties.RADIUS_AUTH_PROTOCOL);
        RadiusAuthenticationProtocol innerProtocol = environment.getProperty(RadiusGuacamoleProperties.RADIUS_EAP_TTLS_INNER_PROTOCOL);
        if (authProtocol == RadiusAuthenticationProtocol.MSCHAP_V1
                    || authProtocol == RadiusAuthenticationProtocol.MSCHAP_V2
                    || innerProtocol == RadiusAuthenticationProtocol.MSCHAP_V1
                    || innerProtocol == RadiusAuthenticationProtocol.MSCHAP_V2) {
            try {
                MessageDigest.getInstance("MD4");
            }
            catch (NoSuchAlgorithmException e) {
                Security.addProvider(new BouncyCastleProvider());
                logger.info("Added BouncyCastle provider for MD4 support (MSCHAP protocol).");
            }
        }
        return new ConfigurationService();
    }

    @Bean("radiusConnectionService")
    public RadiusConnectionService radiusConnectionService() {
        return new RadiusConnectionService();
    }

    @Bean("radiusAuthenticationProviderService")
    public AuthenticationProviderService radiusAuthenticationProviderService() {
        return new AuthenticationProviderService();
    }

    @Bean("radiusAuthenticationProvider")
    public RadiusAuthenticationProvider radiusAuthenticationProvider() {
        logger.info("RADIUS authentication extension enabled.");
        return new RadiusAuthenticationProvider();
    }

    @Bean
    @Scope("prototype")
    public AuthenticatedUser radiusAuthenticatedUser() {
        return new AuthenticatedUser();
    }
}
