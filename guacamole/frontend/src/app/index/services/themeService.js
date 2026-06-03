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
 * Theme application service for the Guacamole theme system.
 *
 * This service is the SINGLE ENTRY POINT for all color changes in the
 * application. It reads theme configuration from the backend, calculates
 * CSS variables using colorEngine, and applies them to the DOM.
 *
 * IMPORTANT: All theme changes MUST go through this service's
 * setThemeVariable() function. No other code path should modify
 * CSS variables directly.
 *
 * Registered as an AngularJS factory for dependency injection.
 */
angular.module('index').factory('themeService', ['$injector',
    function themeService($injector) {

        'use strict';

        var configService = $injector.get('configService');
        var colorEngine = $injector.get('colorEngine');

        /**
         * The single entry point for setting CSS variables.
         * ALL color changes in the application MUST use this function.
         *
         * @param {string} name - The CSS variable name (e.g., '--gc-color-primary')
         * @param {string} value - The CSS variable value (e.g., '#1a73e8')
         */
        function setThemeVariable(name, value) {
            document.documentElement.style.setProperty(name, value);
        }

        /**
         * Applies the theme from the backend configuration.
         * This is the main function that should be called on page load.
         *
         * Uses $q promise (not async/await) for AngularJS compatibility.
         */
        function applyTheme() {
            configService.getConfig().then(function(config) {
                var themeConfig = config.theme || {};
                var mode = themeConfig.mode || 'light';

                // Determine effective mode
                var effectiveMode = mode;
                if (mode === 'auto') {
                    effectiveMode = window.matchMedia('(prefers-color-scheme: dark)').matches
                        ? 'dark' : 'light';
                }

                // Calculate all CSS variables
                var colors = colorEngine.calculate(themeConfig, effectiveMode);

                // Apply each variable (THE ONLY PATH for color changes)
                Object.keys(colors).forEach(function(name) {
                    setThemeVariable(name, colors[name]);
                });

                // Set data-theme attribute for CSS selectors
                document.documentElement.setAttribute('data-theme', effectiveMode);

            })['catch'](function(error) {
                // If theme loading fails, CSS :root defaults are used
                // This is the graceful degradation path
                console.error('Theme apply failed, using CSS defaults:', error);
            });
        }

        /**
         * Listens for OS theme changes when in "auto" mode.
         * Re-applies theme when the user switches between light/dark
         * at the operating system level.
         */
        var mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        mediaQuery.addEventListener('change', function() {
            var cachedConfig = configService.getCachedConfig();
            if (cachedConfig && cachedConfig.theme && cachedConfig.theme.mode === 'auto') {
                applyTheme();
            }
        });

        /**
         * Returns the current effective theme mode.
         *
         * @returns {string} "light" or "dark"
         */
        function getCurrentMode() {
            return document.documentElement.getAttribute('data-theme') || 'light';
        }

        // Public API
        return {
            applyTheme: applyTheme,
            setThemeVariable: setThemeVariable,
            getCurrentMode: getCurrentMode
        };

    }
]);
