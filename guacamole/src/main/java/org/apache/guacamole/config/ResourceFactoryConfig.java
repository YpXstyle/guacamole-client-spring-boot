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

import org.apache.guacamole.GuacamoleSession;
import org.apache.guacamole.net.auth.ActiveConnection;
import org.apache.guacamole.net.auth.Connection;
import org.apache.guacamole.net.auth.ConnectionGroup;
import org.apache.guacamole.net.auth.Directory;
import org.apache.guacamole.net.auth.SharingProfile;
import org.apache.guacamole.net.auth.User;
import org.apache.guacamole.net.auth.UserContext;
import org.apache.guacamole.net.auth.UserGroup;
import org.apache.guacamole.rest.activeconnection.APIActiveConnection;
import org.apache.guacamole.rest.activeconnection.ActiveConnectionDirectoryResource;
import org.apache.guacamole.rest.activeconnection.ActiveConnectionObjectTranslator;
import org.apache.guacamole.rest.activeconnection.ActiveConnectionResource;
import org.apache.guacamole.rest.connection.APIConnection;
import org.apache.guacamole.rest.connection.ConnectionDirectoryResource;
import org.apache.guacamole.rest.connection.ConnectionObjectTranslator;
import org.apache.guacamole.rest.connection.ConnectionResource;
import org.apache.guacamole.rest.connectiongroup.APIConnectionGroup;
import org.apache.guacamole.rest.connectiongroup.ConnectionGroupDirectoryResource;
import org.apache.guacamole.rest.connectiongroup.ConnectionGroupObjectTranslator;
import org.apache.guacamole.rest.connectiongroup.ConnectionGroupResource;
import org.apache.guacamole.rest.directory.DirectoryObjectResourceFactory;
import org.apache.guacamole.rest.directory.DirectoryObjectTranslator;
import org.apache.guacamole.rest.directory.DirectoryResourceFactory;
import org.apache.guacamole.rest.session.SessionResource;
import org.apache.guacamole.rest.session.SessionResourceFactory;
import org.apache.guacamole.rest.session.UserContextResource;
import org.apache.guacamole.rest.session.UserContextResourceFactory;
import org.apache.guacamole.rest.sharingprofile.APISharingProfile;
import org.apache.guacamole.rest.sharingprofile.SharingProfileDirectoryResource;
import org.apache.guacamole.rest.sharingprofile.SharingProfileObjectTranslator;
import org.apache.guacamole.rest.sharingprofile.SharingProfileResource;
import org.apache.guacamole.rest.tunnel.TunnelCollectionResource;
import org.apache.guacamole.rest.tunnel.TunnelCollectionResourceFactory;
import org.apache.guacamole.rest.tunnel.TunnelResource;
import org.apache.guacamole.rest.tunnel.TunnelResourceFactory;
import org.apache.guacamole.rest.user.APIUser;
import org.apache.guacamole.rest.user.UserDirectoryResource;
import org.apache.guacamole.rest.user.UserObjectTranslator;
import org.apache.guacamole.rest.user.UserResource;
import org.apache.guacamole.rest.usergroup.APIUserGroup;
import org.apache.guacamole.rest.usergroup.UserGroupDirectoryResource;
import org.apache.guacamole.rest.usergroup.UserGroupObjectTranslator;
import org.apache.guacamole.rest.usergroup.UserGroupResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual factory implementations replacing Guice's FactoryModuleBuilder.
 * Uses AutowireCapableBeanFactory to inject @Autowired fields after creation.
 */
@Configuration
public class ResourceFactoryConfig {

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    // --- Simple factories ---
    @Bean
    public SessionResourceFactory sessionResourceFactory() {
        return (token, session) -> {
            SessionResource r = new SessionResource(token, session);
            beanFactory.autowireBean(r);
            return r;
        };
    }

    @Bean
    public UserContextResourceFactory userContextResourceFactory() {
        return userContext -> {
            UserContextResource r = new UserContextResource(userContext);
            beanFactory.autowireBean(r);
            return r;
        };
    }

    @Bean
    public TunnelCollectionResourceFactory tunnelCollectionResourceFactory() {
        return session -> {
            TunnelCollectionResource r = new TunnelCollectionResource(session);
            beanFactory.autowireBean(r);
            return r;
        };
    }

    @Bean
    public TunnelResourceFactory tunnelResourceFactory() {
        return tunnel -> {
            TunnelResource r = new TunnelResource(tunnel);
            beanFactory.autowireBean(r);
            return r;
        };
    }

    // --- Translators ---
    @Bean public ActiveConnectionObjectTranslator activeConnectionObjectTranslator() { return new ActiveConnectionObjectTranslator(); }
    @Bean public ConnectionObjectTranslator connectionObjectTranslator() { return new ConnectionObjectTranslator(); }
    @Bean public ConnectionGroupObjectTranslator connectionGroupObjectTranslator() { return new ConnectionGroupObjectTranslator(); }
    @Bean public SharingProfileObjectTranslator sharingProfileObjectTranslator() { return new SharingProfileObjectTranslator(); }
    @Bean public UserObjectTranslator userObjectTranslator() { return new UserObjectTranslator(); }
    @Bean public UserGroupObjectTranslator userGroupObjectTranslator() { return new UserGroupObjectTranslator(); }

    // --- ActiveConnection Directory factories ---
    private <T> T autowire(T bean) { beanFactory.autowireBean(bean); return bean; }

    @Bean
    public DirectoryResourceFactory<ActiveConnection, APIActiveConnection>
            activeConnectionDirectoryResourceFactory(
                    ActiveConnectionObjectTranslator translator,
                    DirectoryObjectResourceFactory<ActiveConnection, APIActiveConnection> objectFactory) {
        return (userContext, directory) ->
            autowire(new ActiveConnectionDirectoryResource(userContext, directory, translator, objectFactory));
    }

    @Bean
    public DirectoryObjectResourceFactory<ActiveConnection, APIActiveConnection>
            activeConnectionResourceFactory(ActiveConnectionObjectTranslator translator) {
        return (parent, userContext, directory) ->
            autowire(new ActiveConnectionResource(parent, userContext, directory, translator));
    }

    // --- Connection Directory factories ---
    @Bean
    public DirectoryResourceFactory<Connection, APIConnection>
            connectionDirectoryResourceFactory(
                    ConnectionObjectTranslator translator,
                    DirectoryObjectResourceFactory<Connection, APIConnection> objectFactory) {
        return (userContext, directory) ->
            autowire(new ConnectionDirectoryResource(userContext, directory, translator, objectFactory));
    }

    @Bean
    public DirectoryObjectResourceFactory<Connection, APIConnection>
            connectionResourceFactory(ConnectionObjectTranslator translator) {
        return (parent, userContext, directory) ->
            autowire(new ConnectionResource(parent, userContext, directory, translator));
    }

    // --- ConnectionGroup Directory factories ---
    @Bean
    public DirectoryResourceFactory<ConnectionGroup, APIConnectionGroup>
            connectionGroupDirectoryResourceFactory(
                    ConnectionGroupObjectTranslator translator,
                    DirectoryObjectResourceFactory<ConnectionGroup, APIConnectionGroup> objectFactory) {
        return (userContext, directory) ->
            autowire(new ConnectionGroupDirectoryResource(userContext, directory, translator, objectFactory));
    }

    @Bean
    public DirectoryObjectResourceFactory<ConnectionGroup, APIConnectionGroup>
            connectionGroupResourceFactory(ConnectionGroupObjectTranslator translator) {
        return (parent, userContext, directory) ->
            autowire(new ConnectionGroupResource(parent, userContext, directory, translator));
    }

    // --- SharingProfile Directory factories ---
    @Bean
    public DirectoryResourceFactory<SharingProfile, APISharingProfile>
            sharingProfileDirectoryResourceFactory(
                    SharingProfileObjectTranslator translator,
                    DirectoryObjectResourceFactory<SharingProfile, APISharingProfile> objectFactory) {
        return (userContext, directory) ->
            autowire(new SharingProfileDirectoryResource(userContext, directory, translator, objectFactory));
    }

    @Bean
    public DirectoryObjectResourceFactory<SharingProfile, APISharingProfile>
            sharingProfileResourceFactory(SharingProfileObjectTranslator translator) {
        return (parent, userContext, directory) ->
            autowire(new SharingProfileResource(parent, userContext, directory, translator));
    }

    // --- User Directory factories ---
    @Bean
    public DirectoryResourceFactory<User, APIUser>
            userDirectoryResourceFactory(
                    UserObjectTranslator translator,
                    DirectoryObjectResourceFactory<User, APIUser> objectFactory) {
        return (userContext, directory) ->
            autowire(new UserDirectoryResource(userContext, directory, translator, objectFactory));
    }

    @Bean
    public DirectoryObjectResourceFactory<User, APIUser>
            userResourceFactory(UserObjectTranslator translator) {
        return (parent, userContext, directory) ->
            autowire(new UserResource(parent, userContext, directory, translator));
    }

    // --- UserGroup Directory factories ---
    @Bean
    public DirectoryResourceFactory<UserGroup, APIUserGroup>
            userGroupDirectoryResourceFactory(
                    UserGroupObjectTranslator translator,
                    DirectoryObjectResourceFactory<UserGroup, APIUserGroup> objectFactory) {
        return (userContext, directory) ->
            autowire(new UserGroupDirectoryResource(userContext, directory, translator, objectFactory));
    }

    @Bean
    public DirectoryObjectResourceFactory<UserGroup, APIUserGroup>
            userGroupResourceFactory(UserGroupObjectTranslator translator) {
        return (parent, userContext, directory) ->
            autowire(new UserGroupResource(parent, userContext, directory, translator));
    }
}
