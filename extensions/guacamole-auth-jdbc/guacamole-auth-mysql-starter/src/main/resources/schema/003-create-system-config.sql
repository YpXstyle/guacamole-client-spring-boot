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
-- branding, theme, site settings, security policies, feature
-- toggles, and announcements.
--

CREATE TABLE `guacamole_system_config` (

    `config_key`    varchar(64)   NOT NULL,
    `config_value`  text,
    `config_type`   varchar(16)   NOT NULL DEFAULT 'string',
    `config_group`  varchar(32)   NOT NULL DEFAULT 'general',
    `updated_by`    varchar(128),
    `updated_at`    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`config_key`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--
-- System file storage. Stores metadata for uploaded files
-- (logos, favicons, backgrounds, etc.)
--

CREATE TABLE `guacamole_system_file` (

    `file_id`       varchar(36)   NOT NULL,
    `filename`      varchar(255)  NOT NULL,
    `mime_type`     varchar(128)  NOT NULL,
    `file_size`     bigint        NOT NULL,
    `file_path`     varchar(512)  NOT NULL,
    `category`      varchar(32)   NOT NULL,
    `uploaded_by`   varchar(128),
    `uploaded_at`   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`file_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--
-- Index for querying files by category
--

CREATE INDEX `guacamole_system_file_category`
    ON `guacamole_system_file` (`category`);

--
-- Default configuration values
--

-- Branding
INSERT INTO `guacamole_system_config` (`config_key`, `config_value`, `config_type`, `config_group`) VALUES
    ('branding.site_name',         'Apache Guacamole', 'string', 'branding'),
    ('branding.site_name_short',   NULL,               'string', 'branding'),
    ('branding.logo',              NULL,               'file',   'branding'),
    ('branding.logo_dark',         NULL,               'file',   'branding'),
    ('branding.favicon',           NULL,               'file',   'branding'),
    ('branding.login_background',  NULL,               'file',   'branding'),
    ('branding.copyright',         NULL,               'string', 'branding'),
    ('branding.support_url',       NULL,               'url',    'branding'),
    ('branding.help_url',          NULL,               'url',    'branding');

-- Theme
INSERT INTO `guacamole_system_config` (`config_key`, `config_value`, `config_type`, `config_group`) VALUES
    ('theme.primary_color',  '#1a56db',  'string', 'theme'),
    ('theme.accent_color',   '#0694a2',  'string', 'theme'),
    ('theme.success_color',  '#057a55',  'string', 'theme'),
    ('theme.warning_color',  '#f59e0b',  'string', 'theme'),
    ('theme.danger_color',   '#e02424',  'string', 'theme'),
    ('theme.mode',           'light',    'enum',   'theme');

-- Security policies (password complexity only)
INSERT INTO `guacamole_system_config` (`config_key`, `config_value`, `config_type`, `config_group`) VALUES
    ('security.password_min_length',         '8',     'integer', 'security'),
    ('security.password_require_uppercase',  'true',  'boolean', 'security'),
    ('security.password_require_number',     'true',  'boolean', 'security'),
    ('security.password_require_special',    'false', 'boolean', 'security');

-- Announcements
INSERT INTO `guacamole_system_config` (`config_key`, `config_value`, `config_type`, `config_group`) VALUES
    ('announcement.message',     NULL,    'text',     'announcement'),
    ('announcement.level',       'info',  'enum',     'announcement'),
    ('announcement.enabled',     'false', 'boolean',  'announcement'),
    ('announcement.start_time',  NULL,    'datetime', 'announcement'),
    ('announcement.end_time',    NULL,    'datetime', 'announcement'),
    ('announcement.closable',    'true',  'boolean',  'announcement');
