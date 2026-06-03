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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for managing file uploads (logos, favicons, backgrounds, etc.).
 * Files are stored on the filesystem with metadata in the database.
 */
@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    /**
     * Maximum file size: 2MB.
     */
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

    /**
     * Allowed MIME types for uploads.
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/svg+xml",
        "image/png",
        "image/jpeg",
        "image/x-icon",
        "image/gif"
    );

    /**
     * Allowed file categories. Any other category will be rejected.
     */
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
        "branding"
    );

    /**
     * Pattern for safe filenames — alphanumeric, dot, dash, underscore, and
     * Unicode letters/characters. Rejects path separators and parent refs.
     */
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile(
        "^[\\w\\-.\\u4e00-\\u9fa5 ]+$");

    /**
     * Validates that a filename contains no path traversal characters.
     *
     * @param filename
     *     The filename to validate.
     *
     * @throws GuacamoleClientException
     *     If the filename is unsafe.
     */
    private void validateFilename(String filename) throws GuacamoleClientException {
        if (filename == null || filename.isEmpty()) {
            throw new GuacamoleClientException("Invalid filename");
        }
        // Reject any path separator or parent reference
        if (filename.contains("/") || filename.contains("\\") ||
                filename.contains("..") || filename.startsWith(".")) {
            throw new GuacamoleClientException("Invalid filename: " + filename);
        }
        // Apply whitelist character check (allow unicode letters for i18n)
        if (!SAFE_FILENAME_PATTERN.matcher(filename).matches()) {
            throw new GuacamoleClientException("Invalid filename characters: " + filename);
        }
    }

    /**
     * Validates that a category is in the allowed set.
     *
     * @param category
     *     The category to validate.
     *
     * @throws GuacamoleClientException
     *     If the category is not allowed.
     */
    private void validateCategory(String category) throws GuacamoleClientException {
        if (category == null || !ALLOWED_CATEGORIES.contains(category)) {
            throw new GuacamoleClientException("Invalid category: " + category);
        }
    }

    /**
     * Base directory for file storage. Configured via
     * guacamole.system.file-storage-path in application.yml.
     */
    @Value("${guacamole.system.file-storage-path:/opt/guacamole/files}")
    private String fileStoragePath;

    @Autowired(required = false)
    private SystemFileMapper fileMapper;

    /**
     * Validates the uploaded file.
     *
     * @param mimeType
     *     The MIME type of the file.
     *
     * @param fileSize
     *     The size of the file in bytes.
     *
     * @throws GuacamoleException
     *     If the file is invalid.
     */
    private void validateUpload(String mimeType, long fileSize) throws GuacamoleException {
        if (fileSize > MAX_FILE_SIZE) {
            throw new GuacamoleClientException(
                "File size exceeds maximum limit of 2MB");
        }
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new GuacamoleClientException(
                "Unsupported file type: " + mimeType);
        }
    }

    /**
     * Uploads a file to the filesystem and records metadata in the database.
     *
     * @param filename
     *     The original filename.
     *
     * @param mimeType
     *     The MIME type of the file.
     *
     * @param fileSize
     *     The size of the file in bytes.
     *
     * @param inputStream
     *     The input stream containing the file data.
     *
     * @param category
     *     The file category (e.g., "branding").
     *
     * @param uploadedBy
     *     The username of the user uploading the file.
     *
     * @return
     *     The file ID (UUID) of the uploaded file.
     *
     * @throws GuacamoleException
     *     If the upload fails.
     */
    public String upload(String filename, String mimeType, long fileSize,
            InputStream inputStream, String category, String uploadedBy)
            throws GuacamoleException {

        validateUpload(mimeType, fileSize);
        validateCategory(category);
        validateFilename(filename);

        if (fileMapper == null) {
            throw new GuacamoleServerException("Database not available for file uploads");
        }

        String fileId = UUID.randomUUID().toString();

        // Create directory structure: {base}/{category}/{fileId}/
        Path categoryDir = Paths.get(fileStoragePath, category, fileId);
        try {
            Files.createDirectories(categoryDir);
        } catch (IOException e) {
            throw new GuacamoleServerException("Failed to create file directory", e);
        }

        // Write file to filesystem
        Path filePath = categoryDir.resolve(filename);
        try {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new GuacamoleServerException("Failed to write file", e);
        }

        // Record metadata in database
        SystemFileModel fileModel = new SystemFileModel();
        fileModel.setFileId(fileId);
        fileModel.setFilename(filename);
        fileModel.setMimeType(mimeType);
        fileModel.setFileSize(fileSize);
        fileModel.setFilePath(category + "/" + fileId + "/" + filename);
        fileModel.setCategory(category);
        fileModel.setUploadedBy(uploadedBy);

        fileMapper.insert(fileModel);
        logger.info("File uploaded: {} ({} bytes) by {}", filename, fileSize, uploadedBy);

        return fileId;
    }

    /**
     * Returns the file metadata for the given file ID.
     *
     * @param fileId
     *     The file ID to look up.
     *
     * @return
     *     The file metadata, or null if not found.
     */
    public SystemFileModel getFile(String fileId) {
        if (fileMapper == null) {
            return null;
        }
        return fileMapper.selectById(fileId);
    }

    /**
     * Returns the physical path to the file on the filesystem.
     *
     * @param fileId
     *     The file ID.
     *
     * @return
     *     The absolute path to the file, or null if not found.
     */
    public Path getFilePath(String fileId) {
        SystemFileModel file = getFile(fileId);
        if (file == null) {
            return null;
        }
        return Paths.get(fileStoragePath, file.getFilePath());
    }

    /**
     * Deletes a file from the filesystem and the database.
     *
     * @param fileId
     *     The file ID to delete.
     *
     * @throws GuacamoleException
     *     If the deletion fails.
     */
    public void deleteFile(String fileId) throws GuacamoleException {
        if (fileMapper == null) {
            throw new GuacamoleServerException("Database not available for file operations");
        }

        SystemFileModel file = fileMapper.selectById(fileId);
        if (file == null) {
            return;
        }

        // Delete from filesystem
        Path filePath = Paths.get(fileStoragePath, file.getFilePath());
        try {
            Files.deleteIfExists(filePath);
            // Also try to delete the parent directory (fileId directory)
            Path parentDir = filePath.getParent();
            if (parentDir != null && Files.isDirectory(parentDir)) {
                Files.deleteIfExists(parentDir);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete file from filesystem: {}", filePath, e);
        }

        // Delete from database
        fileMapper.delete(fileId);
        logger.info("File deleted: {} ({})", file.getFilename(), fileId);
    }

    /**
     * Returns all files in the given category.
     *
     * @param category
     *     The category to filter by.
     *
     * @return
     *     A list of file entries in the category.
     */
    public List<SystemFileModel> getFilesByCategory(String category) {
        if (fileMapper == null) {
            return List.of();
        }
        return fileMapper.selectByCategory(category);
    }

}
