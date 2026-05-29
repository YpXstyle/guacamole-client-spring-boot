package org.apache.guacamole.vault.ksm;

import org.apache.guacamole.vault.ksm.KsmAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.vault.ksm", name = "enabled", havingValue = "true")
public class KsmAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(KsmAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public KsmAuthenticationProvider ksmAuthenticationProvider() {
        logger.info("Keeper Secrets Manager vault extension enabled.");
        return new KsmAuthenticationProvider();
    }
}
