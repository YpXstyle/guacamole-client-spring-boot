package org.apache.guacamole.auth.mysql;

import org.apache.guacamole.auth.jdbc.system.SystemConfigService;
import org.apache.guacamole.auth.mysql.conf.MySQLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.mysql", name = "enabled", havingValue = "true")
@ComponentScan("org.apache.guacamole.auth.jdbc")
@Import(MyBatisConfig.class)
public class MySQLAuthenticationAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MySQLAuthenticationAutoConfiguration.class);

    @Autowired(required = false)
    private SystemConfigService systemConfigService;

    @Bean
    @ConditionalOnMissingBean
    public MySQLEnvironment mySQLEnvironment() throws Exception {
        return new MySQLEnvironment(systemConfigService);
    }

    @Bean
    @ConditionalOnMissingBean
    public MySQLAuthenticationProvider mySQLAuthenticationProvider() {
        logger.info("MySQL authentication extension enabled.");
        return new MySQLAuthenticationProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public MySQLSharedAuthenticationProvider mySQLSharedAuthenticationProvider() {
        logger.info("MySQL shared authentication provider registered.");
        return new MySQLSharedAuthenticationProvider();
    }
}
