package org.apache.guacamole.auth.postgresql;

import org.apache.guacamole.auth.postgresql.conf.PostgreSQLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.postgresql", name = "enabled", havingValue = "true")
@ComponentScan("org.apache.guacamole.auth.jdbc")
@Import(MyBatisConfig.class)
public class PostgreSQLAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PostgreSQLEnvironment postgreSQLEnvironment() throws Exception {
        return new PostgreSQLEnvironment();
    }

    @Bean
    @ConditionalOnMissingBean
    public PostgreSQLAuthenticationProvider postgreSQLAuthenticationProvider() {
        logger.info("PostgreSQL authentication extension enabled.");
        return new PostgreSQLAuthenticationProvider();
    }
}
