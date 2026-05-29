package org.apache.guacamole.auth.quickconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.quickconnect", name = "enabled", havingValue = "true")
public class QuickConnectAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QuickConnectAuthenticationAutoConfiguration.class);

    @Bean("quickConnectAuthenticationProvider")
    @ConditionalOnMissingBean
    public QuickConnectAuthenticationProvider quickConnectAuthenticationProvider(
            ObjectProvider<QuickConnectUserContext> userContextProvider) {
        logger.info("Quick Connect authentication extension enabled.");
        return new QuickConnectAuthenticationProvider(userContextProvider);
    }

    @Bean("quickConnectUserContext")
    @Scope("prototype")
    @ConditionalOnMissingBean(name = "quickConnectUserContext")
    public QuickConnectUserContext quickConnectUserContext() {
        return new QuickConnectUserContext();
    }
}
