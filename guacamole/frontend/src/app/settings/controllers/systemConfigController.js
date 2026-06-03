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
 * Controller for the system configuration management page.
 */
angular.module('settings').controller('systemConfigController', ['$scope', '$injector',
    function systemConfigController($scope, $injector) {

    'use strict';

    // Required services
    var $q                     = $injector.get('$q');
    var authenticationService  = $injector.get('authenticationService');
    var guacNotification       = $injector.get('guacNotification');
    var colorEngine            = $injector.get('colorEngine');
    var themeService           = $injector.get('themeService');
    var configService          = $injector.get('configService');
    var requestService         = $injector.get('requestService');

    /**
     * An action to be provided along with the object sent to showStatus which
     * closes the currently-shown status dialog.
     */
    var ACKNOWLEDGE_ACTION = {
        name     : 'APP.ACTION_ACKNOWLEDGE',
        callback : function acknowledgeCallback() {
            guacNotification.showStatus(false);
        }
    };

    /**
     * The currently active tab.
     *
     * @type {string}
     */
    $scope.activeTab = 'branding';

    /**
     * All configuration entries, keyed by config_key.
     * Each entry has { value, type, group, updatedBy, updatedAt }.
     *
     * @type {Object}
     */
    $scope.configs = {};

    /**
     * Set of dirty (modified) config keys.
     *
     * @type {Object}
     */
    var dirtyKeys = {};

    /**
     * Loads all configuration from the backend.
     */
    function loadConfigs() {

        authenticationService.request({
            method: 'GET',
            url: 'api/settings'
        }).then(function(entries) {
            // Clear existing keys in-place to preserve ng-model object references
            for (var existingKey in $scope.configs) {
                if ($scope.configs.hasOwnProperty(existingKey)) {
                    delete $scope.configs[existingKey];
                }
            }

            entries.forEach(function(entry) {
                $scope.configs[entry.key] = {
                    value: entry.value,
                    type: entry.type,
                    group: entry.group,
                    updatedBy: entry.updatedBy,
                    updatedAt: entry.updatedAt
                };

                // Convert type-specific strings to actual types
                if (entry.type === 'boolean') {
                    $scope.configs[entry.key].value = (entry.value === 'true');
                } else if (entry.type === 'integer') {
                    $scope.configs[entry.key].value = parseInt(entry.value, 10) || 0;
                } else if (entry.type === 'datetime' && entry.value) {
                    // AngularJS datetime-local ng-model REQUIRES a Date object
                    $scope.configs[entry.key].value = new Date(entry.value);
                }
            });

            // Fill any missing keys from factory defaults (all groups)
            for (var defaultKey in FACTORY_DEFAULTS) {
                if (FACTORY_DEFAULTS.hasOwnProperty(defaultKey) && !$scope.configs[defaultKey]) {
                    var group = defaultKey.split('.')[0];
                    var rawValue = FACTORY_DEFAULTS[defaultKey];
                    var typedValue = rawValue;
                    // Infer type from value for booleans and integers
                    if (rawValue === 'true' || rawValue === 'false') {
                        typedValue = (rawValue === 'true');
                    }
                    $scope.configs[defaultKey] = {
                        value: typedValue,
                        type: (rawValue === 'true' || rawValue === 'false') ? 'boolean' : 'string',
                        group: group
                    };
                }
            }

            dirtyKeys = {};
        }, requestService.createErrorCallback(function(error) {
            guacNotification.showStatus({
                className  : 'error',
                title      : 'APP.DIALOG_HEADER_ERROR',
                text       : { key : 'SETTINGS.ERROR_LOAD_FAILED' },
                actions    : [ ACKNOWLEDGE_ACTION ]
            });
        }));
    }

    /**
     * Marks a config key as dirty (modified).
     *
     * @param {string} key - The config key that was modified.
     */
    $scope.markDirty = function(key) {
        dirtyKeys[key] = true;
    };

    /**
     * Returns whether any config has been modified.
     *
     * @returns {boolean} true if any config is dirty.
     */
    $scope.hasDirty = function() {
        return Object.keys(dirtyKeys).length > 0;
    };

    /**
     * Saves all dirty configuration entries to the backend.
     */
    $scope.saveAll = function() {
        var savePromises = [];

        Object.keys(dirtyKeys).forEach(function(key) {
            var config = $scope.configs[key];
            if (!config) return;

            var value = config.value;

            // Null-guard: never send null — backend treats null as "delete this key"
            if (value === null || value === undefined) {
                value = '';
            }

            // Convert typed values back to strings for the API
            if (typeof value === 'boolean') {
                value = value ? 'true' : 'false';
            } else if (typeof value === 'number') {
                value = String(value);
            } else if (value instanceof Date && !isNaN(value.getTime())) {
                // Convert Date (from datetime-local input) to ISO string
                value = value.toISOString();
            }

            var promise = authenticationService.request({
                method: 'PUT',
                url: 'api/settings/' + key,
                data: { value: value }
            });

            savePromises.push(promise);
        });

        $q.all(savePromises).then(function() {
            guacNotification.showStatus({
                text    : { key : 'SETTINGS.ACTION_SAVE_SUCCESS' },
                actions : [ ACKNOWLEDGE_ACTION ]
            });

            // Check which groups were changed BEFORE clearing dirty keys
            var themeChanged = Object.keys(dirtyKeys).some(function(k) {
                return k.startsWith('theme.');
            });
            var brandingChanged = Object.keys(dirtyKeys).some(function(k) {
                return k.startsWith('branding.');
            });
            var announcementChanged = Object.keys(dirtyKeys).some(function(k) {
                return k.startsWith('announcement.');
            });

            dirtyKeys = {};

            // Clear config cache BEFORE reload so loadConfigs gets fresh defaults
            configService.clearCache();
            loadConfigs();

            // Sync branding changes in real-time (title, copyright, favicon)
            if (brandingChanged) {
                configService.getConfig().then(function(config) {
                    if (config && config.branding) {
                        $scope.$emit('guacBrandingChanged', config.branding);
                    }
                });
            }

            // Notify announcement banner of config changes
            if (announcementChanged) {
                $scope.$emit('guacConfigChanged');
            }

            // Re-apply theme if changed
            if (themeChanged) {
                themeService.applyTheme();
            }
        }, requestService.createErrorCallback(function(error) {
            guacNotification.showStatus({
                className  : 'error',
                title      : 'APP.DIALOG_HEADER_ERROR',
                text       : { key : 'SETTINGS.ERROR_SAVE_FAILED' },
                actions    : [ ACKNOWLEDGE_ACTION ]
            });
        }));
    };

    /**
     * Factory default values for all config keys.
     * Used by resetAll() to restore defaults and by loadConfigs() as fallback.
     * Mirrors the guacamole.system.defaults section in application.yml.
     */
    var FACTORY_DEFAULTS = {
        // Branding — matches application.yml defaults
        'branding.site_name': 'Apache Guacamole',
        'branding.site_name_short': '',
        'branding.logo': '',
        'branding.logo_dark': '',
        'branding.favicon': '',
        'branding.login_background': '',
        'branding.copyright': '',
        'branding.support_url': '',
        'branding.help_url': '',

        // Theme — matches application.yml
        'theme.primary_color': '#1a56db',
        'theme.accent_color': '#0694a2',
        'theme.success_color': '#057a55',
        'theme.warning_color': '#f59e0b',
        'theme.danger_color': '#e02424',
        'theme.mode': 'light',

        // Security (password complexity)
        'security.password_min_length': '8',
        'security.password_require_uppercase': 'true',
        'security.password_require_number': 'true',
        'security.password_require_special': 'false',

        // Announcement
        'announcement.message': '',
        'announcement.level': 'info',
        'announcement.enabled': 'false',
        'announcement.start_time': '',
        'announcement.end_time': '',
        'announcement.closable': 'true'
    };

    /**
     * Returns the factory default value for a config key.
     *
     * @param {string} key - The config key.
     * @returns {string} The default value, or '' if unknown.
     */
    var getDefaultValue = function getDefaultValue(key) {
        return FACTORY_DEFAULTS[key] || '';
    };

    /**
     * Resets all configuration in the given group to defaults.
     * Sends PUT with factory default values (not DELETE) to preserve database rows.
     *
     * @param {string} group - The config group to reset.
     */
    $scope.resetAll = function(group) {

        /**
         * Executes the actual reset operation after user confirmation.
         */
        var resetImmediately = function() {
            var resetPromises = [];

            Object.keys($scope.configs).forEach(function(key) {
                if (!key.startsWith(group + '.')) return;

                var defaultValue = getDefaultValue(key);
                resetPromises.push(authenticationService.request({
                    method: 'PUT',
                    url: 'api/settings/' + key,
                    data: { value: defaultValue }
                }));
            });

            $q.all(resetPromises).then(function() {
                guacNotification.showStatus({
                    text    : { key : 'SETTINGS.ACTION_RESET_SUCCESS' },
                    actions : [ ACKNOWLEDGE_ACTION ]
                });
                configService.clearCache();
                loadConfigs();
                themeService.applyTheme();

                if (group === 'branding') {
                    configService.getConfig().then(function(config) {
                        if (config && config.branding) {
                            $scope.$emit('guacBrandingChanged', config.branding);
                        }
                    });
                }

                if (group === 'announcement') {
                    $scope.$emit('guacConfigChanged');
                }
            }, requestService.createErrorCallback(function(error) {
                guacNotification.showStatus({
                    className  : 'error',
                    title      : 'APP.DIALOG_HEADER_ERROR',
                    text       : { key : 'SETTINGS.ERROR_RESET_FAILED' },
                    actions    : [ ACKNOWLEDGE_ACTION ]
                });
            }));
        }; // end resetImmediately

        // Show confirmation dialog
        var RESET_ACTION = {
            name      : 'SETTINGS.ACTION_RESET',
            className : 'danger',
            callback  : function resetCallback() {
                guacNotification.showStatus(false);
                resetImmediately();
            }
        };

        var CANCEL_ACTION = {
            name     : 'APP.ACTION_CANCEL',
            callback : function cancelCallback() {
                guacNotification.showStatus(false);
            }
        };

        guacNotification.showStatus({
            title   : 'SETTINGS.DIALOG_HEADER_CONFIRM_RESET',
            text    : { key : 'SETTINGS.TEXT_CONFIRM_RESET' },
            actions : [ RESET_ACTION, CANCEL_ACTION ]
        });
    };

    /**
     * Callback when a file is uploaded via the file upload component.
     *
     * @param {string} key - The config key to update.
     * @param {string} url - The uploaded file URL.
     */
    $scope.onFileUploaded = function(key, url) {
        if ($scope.configs[key]) {
            $scope.configs[key].value = url;
        } else {
            $scope.configs[key] = { value: url };
        }
        dirtyKeys[key] = true;
    };

    /**
     * Preview theme changes in real-time (for color pickers).
     */
    $scope.previewTheme = function() {
        var themeConfig = {
            primaryColor: $scope.configs['theme.primary_color'] ? $scope.configs['theme.primary_color'].value : '#3C3C3C',
            accentColor: $scope.configs['theme.accent_color'] ? $scope.configs['theme.accent_color'].value : '#0095ff',
            successColor: $scope.configs['theme.success_color'] ? $scope.configs['theme.success_color'].value : '#A3D655',
            warningColor: $scope.configs['theme.warning_color'] ? $scope.configs['theme.warning_color'].value : '#f4b400',
            dangerColor: $scope.configs['theme.danger_color'] ? $scope.configs['theme.danger_color'].value : '#A43'
        };

        var mode = $scope.configs['theme.mode'] ? $scope.configs['theme.mode'].value : 'light';
        var colors = colorEngine.calculate(themeConfig, mode);

        Object.keys(colors).forEach(function(name) {
            themeService.setThemeVariable(name, colors[name]);
        });
    };

    // Initialize: load configs
    loadConfigs();

}]);
