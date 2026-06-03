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
 * Directive for displaying system announcements.
 * Shows a compact single-line banner at the top of the page.
 *
 * Features:
 * - Time-based scheduled visibility (start_time / end_time)
 * - Closable toggle (admin-configurable per announcement)
 * - Dismiss state persisted to localStorage — survives page refresh
 * - Auto-reappears when admin publishes a different announcement
 *
 * Usage:
 *   <guac-announcement></guac-announcement>
 */
angular.module('index').directive('guacAnnouncement', [function guacAnnouncement() {

    var STORAGE_KEY = 'guac.announcement.dismissed';

    return {
        restrict: 'E',
        replace: true,
        templateUrl: 'app/index/templates/guacAnnouncement.html',
        controller: ['$scope', '$injector',
            function guacAnnouncementController($scope, $injector) {

            var configService = $injector.get('configService');

            /**
             * The announcement configuration.
             *
             * @type {object}
             */
            $scope.announcement = {
                enabled: false,
                message: '',
                level: 'info',
                startTime: null,
                endTime: null,
                closable: true
            };

            /**
             * Returns a fingerprint string for the current announcement.
             * When the admin changes the announcement content, the fingerprint
             * changes, which resets the dismiss state automatically.
             *
             * @returns {string}
             *     A hash-like fingerprint of the announcement content.
             */
            var getFingerprint = function getFingerprint() {
                return $scope.announcement.message + '|' + $scope.announcement.level;
            };

            /**
             * Returns the fingerprint of the last dismissed announcement,
             * or null if none has been dismissed.
             *
             * @returns {string|null}
             */
            var getDismissedFingerprint = function getDismissedFingerprint() {
                try {
                    return localStorage.getItem(STORAGE_KEY);
                } catch (e) {
                    return null;
                }
            };

            /**
             * Persists the current announcement fingerprint as dismissed.
             */
            var setDismissed = function setDismissed() {
                try {
                    localStorage.setItem(STORAGE_KEY, getFingerprint());
                } catch (e) {
                    // Storage full or unavailable — dismiss only for this session
                }
            };

            /**
             * Clears the persisted dismiss state (e.g. when config changes).
             */
            var clearDismissed = function clearDismissed() {
                try {
                    localStorage.removeItem(STORAGE_KEY);
                } catch (e) {
                    // Ignore
                }
            };

            /**
             * Returns whether the current time falls within the configured
             * time range.
             *
             * @param {string} startTime
             *     ISO datetime string for the start time, or null.
             *
             * @param {string} endTime
             *     ISO datetime string for the end time, or null.
             *
             * @returns {boolean}
             *     true if the current time is within range (or range is not set).
             */
            var isWithinTimeRange = function isWithinTimeRange(startTime, endTime) {
                if (!startTime && !endTime) {
                    return true;
                }

                var now = new Date();

                if (startTime) {
                    if (now < new Date(startTime)) {
                        return false;
                    }
                }

                if (endTime) {
                    if (now > new Date(endTime)) {
                        return false;
                    }
                }

                return true;
            };

            /**
             * Loads announcement configuration from the backend.
             */
            var loadAnnouncement = function loadAnnouncement() {
                configService.getConfig().then(function(config) {
                    if (config && config.announcement) {
                        $scope.announcement.enabled = config.announcement.enabled === 'true' ||
                                                       config.announcement.enabled === true;
                        $scope.announcement.message = config.announcement.message || '';
                        $scope.announcement.level = config.announcement.level || 'info';
                        $scope.announcement.startTime = config.announcement.startTime || null;
                        $scope.announcement.endTime = config.announcement.endTime || null;

                        var closable = config.announcement.closable;
                        $scope.announcement.closable = !(closable === 'false' || closable === false);
                    }
                })['catch'](function() {
                    // If loading fails, no announcement
                });
            };

            /**
             * Dismisses the announcement. Persists the dismiss state so the
             * same announcement won't reappear after page refresh.
             */
            $scope.dismiss = function() {
                if ($scope.announcement.closable) {
                    setDismissed();
                }
            };

            /**
             * Returns whether the announcement should be visible.
             *
             * @returns {boolean} true if announcement should be shown.
             */
            $scope.isVisible = function() {
                if (!$scope.announcement.enabled || !$scope.announcement.message) {
                    return false;
                }

                // Check if this specific announcement was previously dismissed
                if ($scope.announcement.closable) {
                    var dismissedFp = getDismissedFingerprint();
                    if (dismissedFp && dismissedFp === getFingerprint()) {
                        return false;
                    }
                }

                // Check time range
                if (!isWithinTimeRange($scope.announcement.startTime, $scope.announcement.endTime)) {
                    return false;
                }

                return true;
            };

            // Load announcement on initialization
            loadAnnouncement();

            // Reload announcement when system config changes (real-time sync)
            $scope.$on('guacConfigChanged', function() {
                clearDismissed(); // New config = reset dismiss state
                loadAnnouncement();
            });

        }]
    };

}]);
