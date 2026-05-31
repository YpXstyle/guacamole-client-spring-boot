/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
