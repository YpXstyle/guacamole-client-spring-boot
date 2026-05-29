package org.apache.guacamole.auth.totp;

import org.apache.guacamole.auth.totp.conf.ConfigurationService;
import org.apache.guacamole.auth.totp.form.AuthenticationCodeField;
import org.apache.guacamole.auth.totp.user.CodeUsageTrackingService;
import org.apache.guacamole.auth.totp.user.UserVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.totp", name = "enabled", havingValue = "true")
public class TOTPAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TOTPAuthenticationAutoConfiguration.class);

    @Bean("totpConfigurationService")
    public ConfigurationService totpConfigurationService() {
        return new ConfigurationService();
    }

    @Bean
    public CodeUsageTrackingService totpCodeUsageTrackingService() {
        return new CodeUsageTrackingService();
    }

    @Bean
    @Scope("prototype")
    public AuthenticationCodeField totpAuthenticationCodeField() {
        return new AuthenticationCodeField();
    }

    @Bean
    public UserVerificationService totpUserVerificationService() {
        return new UserVerificationService();
    }

    @Bean("totpAuthenticationProvider")
    public TOTPAuthenticationProvider tOTPAuthenticationProvider(
            UserVerificationService verificationService,
            CodeUsageTrackingService codeUsageTrackingService) {
        logger.info("TOTP authentication extension enabled.");
        return new TOTPAuthenticationProvider(verificationService, codeUsageTrackingService);
    }
}
