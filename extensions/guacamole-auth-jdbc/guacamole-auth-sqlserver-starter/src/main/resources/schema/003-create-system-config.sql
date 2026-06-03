--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.
--

--
-- System configuration storage. Stores key-value pairs for
-- branding, theme, security policies (password complexity), and announcements.
--

CREATE TABLE [guacamole_system_config] (

    [config_key]    nvarchar(64)   NOT NULL,
    [config_value]  nvarchar(max),
    [config_type]   nvarchar(16)   NOT NULL DEFAULT N'string',
    [config_group]  nvarchar(32)   NOT NULL DEFAULT N'general',
    [updated_by]    nvarchar(128),
    [updated_at]    datetime2      NOT NULL DEFAULT GETDATE(),

    PRIMARY KEY ([config_key])

);
GO

--
-- System file storage. Stores metadata for uploaded files
-- (logos, favicons, backgrounds, etc.)
--

CREATE TABLE [guacamole_system_file] (

    [file_id]       nvarchar(36)   NOT NULL,
    [filename]      nvarchar(255)  NOT NULL,
    [mime_type]     nvarchar(128)  NOT NULL,
    [file_size]     bigint         NOT NULL,
    [file_path]     nvarchar(512)  NOT NULL,
    [category]      nvarchar(32)   NOT NULL,
    [uploaded_by]   nvarchar(128),
    [uploaded_at]   datetime2      NOT NULL DEFAULT GETDATE(),

    PRIMARY KEY ([file_id])

);
GO

--
-- Index for querying files by category
--

CREATE INDEX [guacamole_system_file_category]
    ON [guacamole_system_file] ([category]);
GO

--
-- Default configuration values
--

-- Branding
INSERT INTO [guacamole_system_config] ([config_key], [config_value], [config_type], [config_group]) VALUES
    (N'branding.site_name',         N'Apache Guacamole', N'string', N'branding'),
    (N'branding.site_name_short',   NULL,                N'string', N'branding'),
    (N'branding.logo',              NULL,                N'file',   N'branding'),
    (N'branding.logo_dark',         NULL,                N'file',   N'branding'),
    (N'branding.favicon',           NULL,                N'file',   N'branding'),
    (N'branding.login_background',  NULL,                N'file',   N'branding'),
    (N'branding.copyright',         NULL,                N'string', N'branding'),
    (N'branding.support_url',       NULL,                N'url',    N'branding'),
    (N'branding.help_url',          NULL,                N'url',    N'branding');
GO

-- Theme
INSERT INTO [guacamole_system_config] ([config_key], [config_value], [config_type], [config_group]) VALUES
    (N'theme.primary_color',  N'#1a56db', N'string', N'theme'),
    (N'theme.accent_color',   N'#0694a2', N'string', N'theme'),
    (N'theme.success_color',  N'#057a55', N'string', N'theme'),
    (N'theme.warning_color',  N'#f59e0b', N'string', N'theme'),
    (N'theme.danger_color',   N'#e02424', N'string', N'theme'),
    (N'theme.mode',           N'light',   N'enum',   N'theme');
GO

-- Security policies (password complexity only)
INSERT INTO [guacamole_system_config] ([config_key], [config_value], [config_type], [config_group]) VALUES
    (N'security.password_min_length',         N'8',     N'integer', N'security'),
    (N'security.password_require_uppercase',  N'true',  N'boolean', N'security'),
    (N'security.password_require_number',     N'true',  N'boolean', N'security'),
    (N'security.password_require_special',    N'false', N'boolean', N'security');
GO

-- Announcements
INSERT INTO [guacamole_system_config] ([config_key], [config_value], [config_type], [config_group]) VALUES
    (N'announcement.message',     NULL,    N'text',     N'announcement'),
    (N'announcement.level',       N'info', N'enum',     N'announcement'),
    (N'announcement.enabled',     N'false',N'boolean',  N'announcement'),
    (N'announcement.start_time',  NULL,    N'datetime', N'announcement'),
    (N'announcement.end_time',    NULL,    N'datetime', N'announcement'),
    (N'announcement.closable',    N'true', N'boolean',  N'announcement');
GO
