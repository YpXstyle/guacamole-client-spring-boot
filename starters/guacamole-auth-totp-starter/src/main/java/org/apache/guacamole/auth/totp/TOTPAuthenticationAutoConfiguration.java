package org.apache.guacamole.auth.totp;

import org.apache.guacamole.auth.totp.user.UserVerificationService;
import org.apache.guacamole.auth.totp.user.CodeUsageTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.totp", name = "enabled", havingValue = "true")
public class TOTPAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TOTPAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TOTPAuthenticationProvider tOTPAuthenticationProvider(
            UserVerificationService verificationService,
            CodeUsageTrackingService codeUsageTrackingService) {
        logger.info("TOTP authentication extension enabled.");
        return new TOTPAuthenticationProvider(verificationService, codeUsageTrackingService);
    }
}
