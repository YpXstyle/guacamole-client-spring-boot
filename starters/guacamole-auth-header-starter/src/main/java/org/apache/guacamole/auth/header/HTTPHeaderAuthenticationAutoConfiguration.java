package org.apache.guacamole.auth.header;

import org.apache.guacamole.auth.header.HTTPHeaderAuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.header", name = "enabled", havingValue = "true")
public class HTTPHeaderAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(HTTPHeaderAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public HTTPHeaderAuthenticationProvider hTTPHeaderAuthenticationProvider() {
        logger.info("HTTP Header authentication extension enabled.");
        return new HTTPHeaderAuthenticationProvider();
    }
}
