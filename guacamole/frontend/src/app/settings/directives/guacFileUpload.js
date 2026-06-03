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
 * Directive for file upload with preview and server-side deletion.
 *
 * Usage:
 *   <guac-file-upload category="branding"
 *                     current-url="configs['branding.logo'].value"
 *                     on-upload="onFileUploaded('branding.logo', url)">
 *   </guac-file-upload>
 */
angular.module('settings').directive('guacFileUpload', [function guacFileUpload() {

    return {
        restrict: 'E',
        replace: true,
        templateUrl: 'app/settings/templates/guacFileUpload.html',
        scope: {
            category: '@',
            currentUrl: '=',
            onUpload: '&'
        },
        controller: ['$scope', '$http', '$injector',
            function guacFileUploadController($scope, $http, $injector) {

            var $q = $injector.get('$q');
            var authenticationService = $injector.get('authenticationService');
            var guacNotification = $injector.get('guacNotification');
            var requestService = $injector.get('requestService');

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
             * Whether a file is currently being uploaded.
             *
             * @type {boolean}
             */
            $scope.uploading = false;

            /**
             * Whether a file is currently being removed from the server.
             *
             * @type {boolean}
             */
            $scope.removing = false;

            /**
             * Extracts the file ID from a file URL.
             * URL format: /api/settings/files/{fileId}
             *
             * @param {string} url - The file URL.
             * @returns {string|null} The file ID, or null if not parseable.
             */
            var extractFileId = function extractFileId(url) {
                if (!url) return null;
                var parts = url.split('/');
                return parts[parts.length - 1] || null;
            };

            /**
             * Handles file selection from the file input.
             *
             * @param {FileList} files - The selected files.
             */
            $scope.onFileSelect = function(files) {
                if (!files || files.length === 0) return;

                var file = files[0];

                // Validate file size (2MB)
                if (file.size > 2 * 1024 * 1024) {
                    guacNotification.showStatus({
                        className  : 'error',
                        title      : 'APP.DIALOG_HEADER_ERROR',
                        text       : { key : 'SETTINGS.ERROR_FILE_TOO_LARGE' },
                        actions    : [ ACKNOWLEDGE_ACTION ]
                    });
                    return;
                }

                // Validate file type
                var allowedTypes = ['image/svg+xml', 'image/png', 'image/jpeg', 'image/x-icon', 'image/gif'];
                if (allowedTypes.indexOf(file.type) === -1) {
                    guacNotification.showStatus({
                        className  : 'error',
                        title      : 'APP.DIALOG_HEADER_ERROR',
                        text       : { key : 'SETTINGS.ERROR_INVALID_FILE_TYPE' },
                        actions    : [ ACKNOWLEDGE_ACTION ]
                    });
                    return;
                }

                $scope.uploading = true;

                var token = authenticationService.getCurrentToken();
                var formData = new FormData();
                formData.append('file', file);
                formData.append('category', $scope.category || 'branding');

                // Delete old file from server first (if any)
                var deletePromise = $q.when();
                var oldFileId = extractFileId($scope.currentUrl);
                if (oldFileId) {
                    deletePromise = $http.delete('api/settings/files/' + oldFileId, {
                        headers: { 'Guacamole-Token': token }
                    })['catch'](function() {
                        // Ignore delete failures (file may already be gone)
                    });
                }

                deletePromise.then(function() {
                    return $http.post('api/settings/files', formData, {
                        headers: {
                            'Content-Type': undefined,
                            'Guacamole-Token': token
                        },
                        transformRequest: angular.identity
                    });
                }).then(function(response) {
                    $scope.uploading = false;
                    var url = response.data.url;

                    // Update preview
                    $scope.currentUrl = url;

                    // Notify parent
                    if ($scope.onUpload) {
                        $scope.onUpload({ url: url });
                    }

                    guacNotification.showStatus({
                        text    : { key : 'SETTINGS.ACTION_UPLOAD_SUCCESS' },
                        actions : [ ACKNOWLEDGE_ACTION ]
                    });
                })['catch'](requestService.createErrorCallback(function(error) {
                    $scope.uploading = false;
                    guacNotification.showStatus({
                        className  : 'error',
                        title      : 'APP.DIALOG_HEADER_ERROR',
                        text       : { key : 'SETTINGS.ERROR_UPLOAD_FAILED' },
                        actions    : [ ACKNOWLEDGE_ACTION ]
                    });
                }));
            };

            /**
             * Removes the current file — both from the server and from the view.
             */
            $scope.removeFile = function() {
                var fileId = extractFileId($scope.currentUrl);
                if (!fileId) {
                    $scope.currentUrl = '';
                    if ($scope.onUpload) {
                        $scope.onUpload({ url: '' });
                    }
                    return;
                }

                $scope.removing = true;

                var token = authenticationService.getCurrentToken();
                $http.delete('api/settings/files/' + fileId, {
                    headers: { 'Guacamole-Token': token }
                }).then(function() {
                    $scope.removing = false;
                    $scope.currentUrl = '';
                    if ($scope.onUpload) {
                        $scope.onUpload({ url: '' });
                    }
                })['catch'](requestService.createErrorCallback(function(error) {
                    $scope.removing = false;
                    // Clear locally even if server delete fails
                    $scope.currentUrl = '';
                    if ($scope.onUpload) {
                        $scope.onUpload({ url: '' });
                    }
                }));
            };

        }],
        link: function(scope, element) {
            // Bind file input change event
            var fileInput = element.find('input[type="file"]');
            fileInput.on('change', function() {
                scope.$apply(function() {
                    scope.onFileSelect(this.files);
                }.bind(this));
            });

            // Clear the file input value after each use so re-selecting
            // the same file triggers change again
            element.on('click', 'input[type="file"]', function() {
                this.value = null;
            });
        }
    };

}]);
