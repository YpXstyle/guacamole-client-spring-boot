package org.apache.guacamole.config;

import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
public class LogConfig {

    @PostConstruct
    public void init() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    @PreDestroy
    public void destroy() {
        SLF4JBridgeHandler.uninstall();
    }
}
