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

package org.apache.guacamole.rest.settings;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for file upload (POST /api/settings/files).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIFileUploadResponse {

    /**
     * The unique file identifier (UUID).
     */
    private String fileId;

    /**
     * The original filename.
     */
    private String filename;

    /**
     * The MIME type of the file.
     */
    private String mimeType;

    /**
     * The size of the file in bytes.
     */
    private long size;

    /**
     * The URL to access the file.
     */
    private String url;

    /**
     * Creates a new, empty APIFileUploadResponse.
     */
    public APIFileUploadResponse() {
    }

    /**
     * Returns the file ID.
     *
     * @return The file ID (UUID).
     */
    public String getFileId() {
        return fileId;
    }

    /**
     * Sets the file ID.
     *
     * @param fileId The file ID to set.
     */
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    /**
     * Returns the original filename.
     *
     * @return The filename.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the original filename.
     *
     * @param filename The filename to set.
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * Returns the MIME type.
     *
     * @return The MIME type.
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Sets the MIME type.
     *
     * @param mimeType The MIME type to set.
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Returns the file size.
     *
     * @return The file size in bytes.
     */
    public long getSize() {
        return size;
    }

    /**
     * Sets the file size.
     *
     * @param size The file size to set.
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * Returns the URL to access the file.
     *
     * @return The file URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the URL to access the file.
     *
     * @param url The file URL to set.
     */
    public void setUrl(String url) {
        this.url = url;
    }

}
