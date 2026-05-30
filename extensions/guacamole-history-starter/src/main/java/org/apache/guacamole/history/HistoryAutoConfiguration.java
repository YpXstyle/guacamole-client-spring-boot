package org.apache.guacamole.history;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import jakarta.annotation.PostConstruct;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.history", name = "enabled", havingValue = "true")
public class HistoryAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(HistoryAutoConfiguration.class);

    @Value("${guacamole.history.recording-search-path:/var/lib/guacamole/recordings}")
    private String recordingSearchPath;

    @PostConstruct
    public void init() {
        File path = new File(recordingSearchPath);
        HistoryAuthenticationProvider.setSpringRecordingSearchPath(path);
        logger.info("History extension recording-search-path set to: {}", path.getAbsolutePath());
    }

    @Bean
    @ConditionalOnMissingBean
    public HistoryAuthenticationProvider historyAuthenticationProvider() {
        logger.info("History recording extension enabled.");
        return new HistoryAuthenticationProvider();
    }
}
