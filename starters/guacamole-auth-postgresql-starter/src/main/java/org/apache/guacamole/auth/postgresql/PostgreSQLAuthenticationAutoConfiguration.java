package org.apache.guacamole.auth.postgresql;

import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.apache.guacamole.auth.postgresql.PostgreSQLAuthenticationProvider;

@Configuration
@AutoConfigureAfter(MybatisAutoConfiguration.class)
@ConditionalOnProperty(prefix = "guacamole.auth.postgresql", name = "enabled", havingValue = "true")
@Import(MyBatisConfig.class)
public class PostgreSQLAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PostgreSQLAuthenticationProvider postgreSQLAuthenticationProvider() {
        logger.info("PostgreSQL authentication extension enabled.");
        return new PostgreSQLAuthenticationProvider();
    }
}
