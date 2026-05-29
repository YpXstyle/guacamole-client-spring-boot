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

package org.apache.guacamole.vault.conf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleServerException;
import org.apache.guacamole.environment.Environment;
import org.apache.guacamole.properties.FileGuacamoleProperties;
import org.apache.guacamole.properties.GuacamoleProperties;
import org.apache.guacamole.properties.PropertiesGuacamoleProperties;
import org.apache.guacamole.vault.secret.VaultSecretService;

/**
 * Base class for services which retrieve key vault configuration information.
 * A concrete implementation of this class must be defined and bound for key
 * vault support to work.
 */
public abstract class VaultConfigurationService {

    /**
     * The Guacamole server environment.
     */
    @Autowired
    private Environment environment;

    @Autowired
    private VaultSecretService secretService;

    /**
     * ObjectMapper for deserializing YAML.
     */
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /**
     * The name of the file containing a YAML mapping of Guacamole parameter
     * token to vault secret name.
     */
    private final String tokenMappingFilename;

    /**
     * The name of the properties file containing Guacamole configuration
     * properties.
     */
    private final String propertiesFilename;

    /**
     * Creates a new VaultConfigurationService which retrieves the token/secret
     * mappings and Guacamole configuration properties from the files with the
     * given names.
     *
     * @param tokenMappingFilename
     *     The name of the YAML file containing the token/secret mapping.
     *
     * @param propertiesFilename
     *     The name of the properties file containing Guacamole configuration
     *     properties whose values are the names of corresponding secrets.
     */
    protected VaultConfigurationService(String tokenMappingFilename,
            String propertiesFilename) {
        this.tokenMappingFilename = tokenMappingFilename;
        this.propertiesFilename = propertiesFilename;
    }

    /**
     * Returns a mapping dictating the name of the secret which maps to each
     * parameter token.
     *
     * @return
     *     A mapping dictating the name of the secret which maps to each
     *     parameter token.
     *
     * @throws GuacamoleException
     *     If the YAML file defining the token/secret mapping cannot be read.
     */
    public Map<String, String> getTokenMapping() throws GuacamoleException {

        // Get configuration file from GUACAMOLE_HOME
        File confFile = new File(environment.getGuacamoleHome(), tokenMappingFilename);
        if (!confFile.exists())
            return Collections.emptyMap();

        // Deserialize token mapping from YAML
        try {

            Map<String, String> mapping = mapper.readValue(confFile, new TypeReference<Map<String, String>>() {});
            if (mapping == null)
                return Collections.emptyMap();

            return mapping;

        }

        // Fail if YAML is invalid/unreadable
        catch (IOException e) {
            throw new GuacamoleServerException("Unable to read token mapping "
                    + "configuration file \"" + tokenMappingFilename + "\".", e);
        }

    }

    /**
     * Returns a GuacamoleProperties instance which automatically reads the
     * values of requested properties from the vault.
     *
     * @return
     *     A GuacamoleProperties instance which automatically reads property
     *     values from the vault.
     *
     * @throws GuacamoleException
     *     If the properties file containing the property/secret mappings
     *     exists but cannot be read.
     */
    public GuacamoleProperties getProperties() throws GuacamoleException {

        // Use empty properties if file cannot be found
        File propFile = new File(environment.getGuacamoleHome(), propertiesFilename);
        if (!propFile.exists())
            return new PropertiesGuacamoleProperties(new Properties());

        // Automatically pull properties from vault
        return new FileGuacamoleProperties(propFile) {

            @Override
            public String getProperty(String name) throws GuacamoleException {
                try {

                    String secretName = super.getProperty(name);
                    if (secretName == null)
                        return null;

                    return secretService.getValue(secretName).get();

                }
                catch (InterruptedException | ExecutionException e) {

                    if (e.getCause() instanceof GuacamoleException)
                        throw (GuacamoleException) e;

                    throw new GuacamoleServerException(String.format("Property "
                            + "\"%s\" could not be retrieved from the vault.", name), e);
                }
            }

        };

    }

}
