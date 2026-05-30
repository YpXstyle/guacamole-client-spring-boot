package org.apache.guacamole.auth.sqlserver;

import org.apache.guacamole.auth.sqlserver.conf.SQLServerEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.sqlserver", name = "enabled", havingValue = "true")
@ComponentScan("org.apache.guacamole.auth.jdbc")
@Import(MyBatisConfig.class)
public class SQLServerAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SQLServerAuthenticationAutoConfiguration.class);

    @Bean
    public SQLServerEnvironment sQLServerEnvironment() throws Exception {
        return new SQLServerEnvironment();
    }

    @Bean
    public SQLServerAuthenticationProvider sQLServerAuthenticationProvider() {
        logger.info("SQL Server authentication extension enabled.");
        return new SQLServerAuthenticationProvider();
    }
}
