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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleResourceNotFoundException;
import org.apache.guacamole.GuacamoleSecurityException;
import org.apache.guacamole.GuacamoleSession;
import org.apache.guacamole.GuacamoleServerException;
import org.apache.guacamole.auth.jdbc.system.FileStorageService;
import org.apache.guacamole.auth.jdbc.system.SystemFileModel;
import org.apache.guacamole.net.auth.permission.SystemPermission;
import org.apache.guacamole.net.auth.permission.SystemPermissionSet;
import org.apache.guacamole.rest.TokenParam;
import org.apache.guacamole.rest.auth.AuthenticationService;
import org.apache.guacamole.rest.auth.DecoratedUserContext;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST service for file management (POST/GET/DELETE /api/settings/files).
 */
@Path("/settings/files")
@Produces(MediaType.APPLICATION_JSON)
public class FileRESTService {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired(required = false)
    private FileStorageService fileStorageService;

    /**
     * Verifies that the user has SYSTEM_ADMINISTER permission.
     *
     * @param authToken
     *     The authentication token.
     *
     * @throws GuacamoleException
     *     If the token is invalid or the user lacks permission.
     */
    private void verifyAdminPermission(String authToken) throws GuacamoleException {
        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        List<DecoratedUserContext> userContexts = session.getUserContexts();

        if (userContexts.isEmpty()) {
            throw new GuacamoleSecurityException("No user context available");
        }

        DecoratedUserContext userContext = userContexts.get(0);
        SystemPermissionSet permissions = userContext.self().getSystemPermissions();

        if (!permissions.hasPermission(SystemPermission.Type.ADMINISTER)) {
            throw new GuacamoleSecurityException(
                "SYSTEM_ADMINISTER permission required");
        }
    }

    /**
     * Uploads a file using Jersey multipart support.
     *
     * @param authToken
     *     The authentication token.
     *
     * @param fileBodyPart
     *     The uploaded file as a Jersey FormDataBodyPart.
     *
     * @param category
     *     The file category from the "category" form field.
     *
     * @return
     *     The upload response with file ID and URL.
     *
     * @throws GuacamoleException
     *     If the upload fails.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public APIFileUploadResponse uploadFile(
            @TokenParam String authToken,
            @FormDataParam("file") FormDataBodyPart fileBodyPart,
            @FormDataParam("category") String category)
            throws GuacamoleException {

        verifyAdminPermission(authToken);

        if (fileStorageService == null) {
            throw new GuacamoleServerException(
                "File storage is not available. Enable a JDBC extension first.");
        }

        if (fileBodyPart == null) {
            throw new GuacamoleClientException("No file provided");
        }

        // Get filename and MIME type from the body part
        String filename = fileBodyPart.getFormDataContentDisposition().getFileName();
        if (filename == null || filename.isEmpty()) {
            filename = "upload";
        }

        String mimeType = fileBodyPart.getMediaType().toString();

        // Default category if not provided
        if (category == null || category.isEmpty()) {
            category = "branding";
        }

        // Get username for audit
        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        String username = session.getAuthenticatedUser().getIdentifier();

        // Read file content to get accurate size (max 2MB, in memory is fine)
        byte[] fileBytes = fileBodyPart.getValueAs(byte[].class);
        long size = fileBytes.length;
        try (InputStream inputStream = new java.io.ByteArrayInputStream(fileBytes)) {
            String fileId = fileStorageService.upload(
                filename, mimeType, size, inputStream, category, username);

            // Build response
            APIFileUploadResponse response = new APIFileUploadResponse();
            response.setFileId(fileId);
            response.setFilename(filename);
            response.setMimeType(mimeType);
            response.setSize(size);
            response.setUrl("/api/settings/files/" + fileId);

            return response;

        } catch (IOException e) {
            throw new GuacamoleServerException("Failed to process file upload", e);
        }
    }

    /**
     * Returns a file for download/display.
     * This endpoint does NOT require authentication (public access for
     * logos, favicons, etc.).
     *
     * @param fileId
     *     The file ID to retrieve.
     *
     * @return
     *     The file as a streaming response.
     *
     * @throws GuacamoleException
     *     If the file is not found.
     */
    @GET
    @Path("/{fileId}")
    public Response getFile(@PathParam("fileId") String fileId)
            throws GuacamoleException {

        if (fileStorageService == null) {
            throw new GuacamoleServerException(
                "File storage is not available. Enable a JDBC extension first.");
        }

        SystemFileModel file = fileStorageService.getFile(fileId);
        if (file == null) {
            throw new GuacamoleResourceNotFoundException("File not found: " + fileId);
        }

        java.nio.file.Path filePath = fileStorageService.getFilePath(fileId);
        if (filePath == null || !Files.exists(filePath)) {
            throw new GuacamoleResourceNotFoundException("File not found on disk: " + fileId);
        }

        try {
            return Response.ok(Files.newInputStream(filePath))
                .type(file.getMimeType())
                .header("Content-Disposition", "inline; filename=\"" + file.getFilename() + "\"")
                .header("Cache-Control", "public, max-age=86400")
                .build();
        } catch (IOException e) {
            throw new GuacamoleServerException("Failed to read file", e);
        }
    }

    /**
     * Deletes a file.
     *
     * @param authToken
     *     The authentication token.
     *
     * @param fileId
     *     The file ID to delete.
     *
     * @throws GuacamoleException
     *     If the file is not found or the user lacks permission.
     */
    @DELETE
    @Path("/{fileId}")
    public void deleteFile(@TokenParam String authToken,
            @PathParam("fileId") String fileId)
            throws GuacamoleException {

        verifyAdminPermission(authToken);

        if (fileStorageService == null) {
            throw new GuacamoleServerException(
                "File storage is not available. Enable a JDBC extension first.");
        }

        SystemFileModel file = fileStorageService.getFile(fileId);
        if (file == null) {
            throw new GuacamoleResourceNotFoundException("File not found: " + fileId);
        }

        fileStorageService.deleteFile(fileId);
    }

}
