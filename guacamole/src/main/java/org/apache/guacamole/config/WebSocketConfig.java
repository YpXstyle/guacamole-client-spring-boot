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

package org.apache.guacamole.config;

import org.apache.guacamole.tunnel.TunnelRequestService;
import org.apache.guacamole.tunnel.websocket.GuacamoleWebSocketEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.catalina.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class WebSocketConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    @Autowired
    private TunnelRequestService tunnelRequestService;

    @PostConstruct
    public void init() {
        GuacamoleWebSocketEndpoint.setTunnelRequestService(tunnelRequestService);
        logger.info("TunnelRequestService set on GuacamoleWebSocketEndpoint");
    }

    @Bean
    public TomcatServletWebServerFactory servletContainerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addContextCustomizers(context -> {
            // Register WebSocket endpoint after context starts
            context.addLifecycleListener(event -> {
                if (org.apache.catalina.Lifecycle.AFTER_START_EVENT.equals(event.getType())) {
                    registerWebSocketEndpoint(context);
                }
            });
        });
        return factory;
    }

    private void registerWebSocketEndpoint(Context context) {
        try {
            jakarta.servlet.ServletContext servletContext = context.getServletContext();
            Object containerObj = servletContext.getAttribute(
                    "org.apache.tomcat.websocket.server.WsServerContainer");
            if (containerObj == null) {
                containerObj = servletContext.getAttribute(
                        jakarta.websocket.server.ServerContainer.class.getName());
            }
            if (containerObj instanceof jakarta.websocket.server.ServerContainer) {
                jakarta.websocket.server.ServerContainer container =
                        (jakarta.websocket.server.ServerContainer) containerObj;
                ServerEndpointConfig config = ServerEndpointConfig.Builder
                        .create(GuacamoleWebSocketEndpoint.class, "/websocket-tunnel")
                        .subprotocols(java.util.Collections.singletonList("guacamole"))
                        .build();
                container.addEndpoint(config);
                logger.info("WebSocket tunnel registered at /websocket-tunnel");
            } else {
                logger.error("ServerContainer not found. WebSocket tunnel unavailable.");
            }
        } catch (DeploymentException e) {
            logger.error("Unable to deploy WebSocket tunnel endpoint.", e);
        }
    }
}
