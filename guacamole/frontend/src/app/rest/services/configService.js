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

/**
 * Service for fetching and caching the public configuration from
 * the backend (GET /api/config).
 *
 * This service provides branding, theme, features, and announcement
 * configuration to the frontend. The configuration is cached for the
 * lifetime of the page to avoid unnecessary API calls.
 *
 * Registered as an AngularJS factory for dependency injection.
 */
angular.module('index').factory('configService', ['$http', '$q',
    function configService($http, $q) {

        'use strict';

        /**
         * Cached configuration response. Null until first successful fetch.
         *
         * @type {object|null}
         */
        var cachedConfig = null;

        /**
         * Fetches the public configuration from the backend.
         * Caches the result for subsequent calls.
         *
         * @returns {Promise} A promise that resolves with the configuration object.
         */
        function getConfig() {
            if (cachedConfig) {
                return $q.when(cachedConfig);
            }

            return $http.get('api/config').then(function(response) {
                cachedConfig = response.data;
                return cachedConfig;
            });
        }

        /**
         * Returns the cached configuration without making an API call.
         * Returns null if configuration has not been fetched yet.
         *
         * @returns {object|null} The cached configuration, or null.
         */
        function getCachedConfig() {
            return cachedConfig;
        }

        /**
         * Clears the configuration cache. Useful for forcing a refresh.
         */
        function clearCache() {
            cachedConfig = null;
        }

        // Public API
        return {
            getConfig: getConfig,
            getCachedConfig: getCachedConfig,
            clearCache: clearCache
        };

    }
]);
