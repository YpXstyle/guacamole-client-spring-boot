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

package org.apache.guacamole;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.TimeZone;

@SpringBootApplication
@ComponentScan(
    basePackages = "org.apache.guacamole",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "org\\.apache\\.guacamole\\.(auth\\..*|vault\\..*|history\\..*)"
    )
)
@Slf4j
public class GuacamoleSpringBootApplication {
    public static void main(String[] args) throws UnknownHostException {
        String tz = System.getProperty("user.timezone");
        if (tz != null && !tz.isEmpty()) {
            TimeZone.setDefault(TimeZone.getTimeZone(tz));
        }

        ConfigurableApplicationContext run = SpringApplication.run(GuacamoleSpringBootApplication.class, args);
        Environment env = run.getEnvironment();
        final String hostAddress = InetAddress.getLocalHost().getHostAddress();
        final String serverPort = env.getProperty("server.port");
        String contextPath = env.getProperty("server.servlet.context-path");
        if (StringUtils.isNotBlank(contextPath)) {
            if (contextPath.endsWith("/")) {
                contextPath = contextPath.replaceAll("/$", "");
            }
        }
        log.info("\n[----------------------------------------------------------]\n\t" +
                        "启动成功！访问地址:\thttp://{}:{}{}\n\t" +
                        "\n[----------------------------------------------------------]",
                hostAddress, serverPort, StringUtils.isBlank(contextPath) ? "" : contextPath);
    }
}
