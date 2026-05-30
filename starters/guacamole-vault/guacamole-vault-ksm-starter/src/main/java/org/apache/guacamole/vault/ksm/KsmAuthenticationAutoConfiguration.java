package org.apache.guacamole.vault.ksm;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.vault.ksm.conf.KsmConfigurationService;
import org.apache.guacamole.vault.ksm.secret.KsmClient;
import org.apache.guacamole.vault.ksm.secret.KsmRecordService;
import org.apache.guacamole.vault.ksm.secret.KsmSecretService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.vault.ksm", name = "enabled", havingValue = "true")
public class KsmAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(KsmAuthenticationAutoConfiguration.class);

    @Bean
    public KsmConfigurationService ksmConfigurationService() {
        return new KsmConfigurationService();
    }

    @Bean
    public KsmRecordService ksmRecordService() {
        return new KsmRecordService();
    }

    @Bean
    public KsmClient ksmClient(KsmConfigurationService ksmConfigurationService, KsmRecordService ksmRecordService) {
        return new KsmClient(ksmConfigurationService, ksmRecordService);
    }

    @Bean
    public KsmSecretService ksmSecretService(KsmClient ksmClient, KsmRecordService ksmRecordService) {
        return new KsmSecretService(ksmClient, ksmRecordService);
    }

    @Bean
    public KsmAuthenticationProvider ksmAuthenticationProvider(
            Environment environment,
            KsmConfigurationService ksmConfigurationService,
            KsmSecretService ksmSecretService) throws GuacamoleException {
        logger.info("Keeper Secrets Manager vault extension enabled.");
        return new KsmAuthenticationProvider(environment, ksmConfigurationService, ksmSecretService);
    }
}
