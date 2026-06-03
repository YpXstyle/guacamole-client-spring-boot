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

package org.apache.guacamole.auth.jdbc.system;

import java.util.Date;

/**
 * Model representing a single uploaded file entry from the
 * guacamole_system_file table.
 */
public class SystemFileModel {

    /**
     * The unique file identifier (UUID).
     */
    private String fileId;

    /**
     * The original filename of the uploaded file.
     */
    private String filename;

    /**
     * The MIME type of the file (e.g., "image/png").
     */
    private String mimeType;

    /**
     * The size of the file in bytes.
     */
    private long fileSize;

    /**
     * The relative path to the file on the filesystem.
     */
    private String filePath;

    /**
     * The category of the file (branding, avatar, recording).
     */
    private String category;

    /**
     * The username of the user who uploaded the file.
     */
    private String uploadedBy;

    /**
     * The timestamp when the file was uploaded.
     */
    private Date uploadedAt;

    /**
     * Creates a new, empty SystemFileModel.
     */
    public SystemFileModel() {
    }

    /**
     * Returns the unique file identifier.
     *
     * @return The file ID (UUID).
     */
    public String getFileId() {
        return fileId;
    }

    /**
     * Sets the unique file identifier.
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
     * @return The MIME type (e.g., "image/png").
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
     * Returns the file size in bytes.
     *
     * @return The file size.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Sets the file size in bytes.
     *
     * @param fileSize The file size to set.
     */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Returns the relative path to the file on the filesystem.
     *
     * @return The file path.
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Sets the relative path to the file on the filesystem.
     *
     * @param filePath The file path to set.
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Returns the category of the file.
     *
     * @return The category (branding, avatar, recording).
     */
    public String getCategory() {
        return category;
    }

    /**
     * Sets the category of the file.
     *
     * @param category The category to set.
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Returns the username of the user who uploaded the file.
     *
     * @return The username, or null if unknown.
     */
    public String getUploadedBy() {
        return uploadedBy;
    }

    /**
     * Sets the username of the user who uploaded the file.
     *
     * @param uploadedBy The username to set.
     */
    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    /**
     * Returns the timestamp when the file was uploaded.
     *
     * @return The upload timestamp.
     */
    public Date getUploadedAt() {
        return uploadedAt;
    }

    /**
     * Sets the timestamp when the file was uploaded.
     *
     * @param uploadedAt The timestamp to set.
     */
    public void setUploadedAt(Date uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

}
