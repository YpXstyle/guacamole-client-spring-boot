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

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for system file entries in the guacamole_system_file table.
 */
@Mapper
public interface SystemFileMapper {

    /**
     * Returns the file entry with the given ID.
     *
     * @param fileId
     *     The file ID to look up.
     *
     * @return
     *     The file entry, or null if not found.
     */
    SystemFileModel selectById(@Param("fileId") String fileId);

    /**
     * Returns all file entries belonging to the given category.
     *
     * @param category
     *     The category to filter by (e.g., "branding").
     *
     * @return
     *     A list of file entries in the specified category.
     */
    List<SystemFileModel> selectByCategory(@Param("category") String category);

    /**
     * Inserts a new file entry.
     *
     * @param file
     *     The file entry to insert.
     *
     * @return
     *     The number of rows inserted.
     */
    int insert(@Param("file") SystemFileModel file);

    /**
     * Deletes the file entry with the given ID.
     *
     * @param fileId
     *     The file ID to delete.
     *
     * @return
     *     The number of rows deleted.
     */
    int delete(@Param("fileId") String fileId);

}
