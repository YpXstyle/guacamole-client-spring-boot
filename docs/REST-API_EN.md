# REST API Reference

[← Back to Documentation](../README.md#documentation)

Complete REST API endpoint reference for Guacamole Spring Boot. All endpoints are fully compatible with the original Apache Guacamole 1.5.5. Verified against source code.

## Table of Contents

- [Authentication](#authentication)
- [Session Management](#session-management)
- [Connection Management](#connection-management)
- [Connection Group Management](#connection-group-management)
- [User Management](#user-management)
- [User Group Management](#user-group-management)
- [Sharing Profiles](#sharing-profiles)
- [Active Connections](#active-connections)
- [History](#history)
- [Tunnel Management](#tunnel-management)
- [Schema](#schema)
- [Extensions and Patches](#extensions-and-patches)
- [Languages](#languages)
- [System Configuration](#system-configuration)
- [General Notes](#general-notes)

---

## Authentication

### Login

Creates a new authentication token. This is the only endpoint that does not require authentication.

```
POST /api/tokens
Content-Type: application/x-www-form-urlencoded
```

**Request Parameters** (form):

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `username` | String | No | Username. If blank, obtained from `Authorization: Basic` header |
| `password` | String | No | Password. If blank, obtained from `Authorization: Basic` header |
| `token` | String | No | Existing authentication token (second step of multi-factor auth) |

Also supports HTTP Basic Auth header as fallback for username/password:

```
Authorization: Basic base64(username:password)
```

**Response** (200 OK):

```json
{
    "authToken": "a1b2c3d4e5f6...",
    "username": "admin",
    "authProviderIdentifier": "postgresql",
    "availableDataSources": ["postgresql"]
}
```

**Error** (400/401/403):

```json
{
    "message": "Permission Denied.",
    "type": "INVALID_CREDENTIALS",
    "expected": {"type": "USERNAME_PASSWORD"},
    "translatableMessage": null
}
```

### Logout

```
DELETE /api/tokens/{token}
```

**Parameters:**
| Parameter | Location | Type | Description |
|-----------|----------|------|-------------|
| `token` | Path | String | Authentication token to invalidate |

**Response:** 204 No Content (success). 404 Not Found (token does not exist).

---

## Session Management

### Get Current Session

```
GET /api/session/
```

**Query Parameters:** `token` (authentication token)

**Authentication:** Token required (via `Guacamole-Token` header or `token` query parameter)

**Response:** SessionResource, provides data source routing via sub-resource locator.

### Logout Current Session

```
DELETE /api/session/
```

**Authentication:** Token required

**Response:** 204 No Content

### Get User Context

```
GET /api/session/data/{dataSource}
```

**Parameters:**
| Parameter | Location | Type | Description |
|-----------|----------|------|-------------|
| `dataSource` | Path | String | Authentication provider identifier (e.g., `mysql`, `postgresql`) |

**Authentication:** Token required

**Response:** Provides access to connections, users, groups and other directories via sub-resource locator.

### Get Current User

```
GET /api/session/data/{dataSource}/self
```

**Authentication:** Token required

**Response:**

```json
{
    "identifier": "admin",
    "attributes": {},
    "permissions": {"systemPermissions": ["ADMINISTER"]}
}
```

---

## Connection Management

### Connection List

```
GET /api/session/data/{dataSource}/connections
```

| Parameter | Location | Type | Description |
|-----------|----------|------|-------------|
| `dataSource` | Path | String | Authentication provider identifier |

**Authentication:** Token required

**Response:** Connection object map (`Map<String, APIConnection>`):

```json
{
    "5": {
        "identifier": "5",
        "name": "Production Server",
        "protocol": "rdp",
        "parentIdentifier": "ROOT",
        "activeConnections": 0,
        "attributes": {
            "guacd-port": "4822",
            "guacd-hostname": "localhost"
        }
    },
    "8": {
        "identifier": "8",
        "name": "Development Environment",
        "protocol": "ssh",
        "parentIdentifier": "ROOT",
        "activeConnections": 1,
        "attributes": {}
    }
}
```

### Get Single Connection

```
GET /api/session/data/{dataSource}/connections/{id}
```

| Parameter | Location | Type | Description |
|-----------|----------|------|-------------|
| `id` | Path | String | Connection identifier |

**Authentication:** Token required

### Create Connection

```
POST /api/session/data/{dataSource}/connections
Content-Type: application/json
```

**Request Body:**

```json
{
    "name": "New Connection",
    "protocol": "rdp",
    "parentIdentifier": "ROOT",
    "attributes": {
        "guacd-hostname": "localhost",
        "guacd-port": "4822",
        "hostname": "192.168.1.100",
        "port": "3389",
        "username": "administrator",
        "password": "secret",
        "security": "any",
        "ignore-cert": "true"
    }
}
```

**Authentication:** Token required. Requires `CREATE_CONNECTION` system permission and `UPDATE` permission on the target group.

**Response:** Created connection object (201 Created).

### Update Connection

```
PUT /api/session/data/{dataSource}/connections/{id}
Content-Type: application/json
```

**Authentication:** Token required. Requires `UPDATE` permission on the connection.

### Partial Update Connection

```
PATCH /api/session/data/{dataSource}/connections/{id}
Content-Type: application/json
```

Supported JSON Patch operations: `add`, `replace`, `remove`.

```json
[
    {"op": "add", "path": "/attributes/hostname", "value": "192.168.1.200"},
    {"op": "replace", "path": "/name", "value": "New Name"},
    {"op": "remove", "path": "/attributes/password"}
]
```

**Authentication:** Token required. Requires `UPDATE` permission on the connection.

### Delete Connection

```
DELETE /api/session/data/{dataSource}/connections/{id}
```

**Authentication:** Token required. Requires `DELETE` permission on the connection.

### Get Connection Parameters

```
GET /api/session/data/{dataSource}/connections/{id}/parameters
```

Returns the connection's parameter list (with actual values after token replacement). Requires `ADMINISTER` system permission or `UPDATE` permission on the connection.

**Authentication:** Token required

**Response:**

```json
{
    "hostname": "192.168.1.100",
    "port": "3389",
    "username": "administrator",
    "security": "any",
    "ignore-cert": "true"
}
```

### Connection Usage History

```
GET /api/session/data/{dataSource}/connections/{id}/history
```

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `token` | String | Authentication token |
| `sort` | String | Sort field (`startDate`, `endDate`, `username`) |
| `order` | String | Sort direction (`ASC`, `DESC`) |
| `limit` int | Maximum number of records to return |

**Authentication:** Token required

### Connection Sharing Profiles

```
GET /api/session/data/{dataSource}/connections/{id}/sharingProfiles
```

Returns sharing profiles that the current user can use to share this connection.

**Authentication:** Token required

---

## Connection Group Management

### Connection Group List

```
GET /api/session/data/{dataSource}/connectionGroups
```

**Authentication:** Token required

**Response:** Connection group object map.

### Get Single Connection Group

```
GET /api/session/data/{dataSource}/connectionGroups/{id}
```

**Authentication:** Token required

### Create Connection Group

```
POST /api/session/data/{dataSource}/connectionGroups
Content-Type: application/json
```

```json
{
    "name": "New Group",
    "parentIdentifier": "ROOT",
    "type": "ORGANIZATIONAL",
    "attributes": {}
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | String | `ORGANIZATIONAL` (organizational group) or `BALANCING` (load balancing group) |

**Authentication:** Token required. Requires `CREATE_CONNECTION_GROUP` system permission.

### Update Connection Group

```
PUT /api/session/data/{dataSource}/connectionGroups/{id}
```

**Authentication:** Token required. Requires `UPDATE` permission on the connection group.

### Delete Connection Group

```
DELETE /api/session/data/{dataSource}/connectionGroups/{id}
```

**Authentication:** Token required. Requires `DELETE` permission on the connection group.

### Connection Group Tree

```
GET /api/session/data/{dataSource}/connectionGroups/{id}/tree
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `permission` | String | No | Filter by permission level (e.g., `READ`) |

**Authentication:** Token required

**Response:** Hierarchical structure of all connections and sub-groups under this group, filterable by permission.

---

## User Management

### User List

```
GET /api/session/data/{dataSource}/users
```

**Authentication:** Token required. Requires `ADMINISTER` permission.

### Get Single User

```
GET /api/session/data/{dataSource}/users/{username}
```

**Authentication:** Token required

### Create User

```
POST /api/session/data/{dataSource}/users
Content-Type: application/json
```

```json
{
    "username": "newuser",
    "password": "userpassword",
    "attributes": {}
}
```

**Authentication:** Token required. Requires `CREATE_USER` system permission.

### Update User

```
PUT /api/session/data/{dataSource}/users/{username}
```

**Authentication:** Token required. Requires `UPDATE` permission on the user.

### Delete User

```
DELETE /api/session/data/{dataSource}/users/{username}
```

**Authentication:** Token required. Requires `DELETE` permission on the user.

### Change Password

```
PUT /api/session/data/{dataSource}/users/{username}/password
Content-Type: application/json
```

```json
{
    "oldPassword": "currentpassword",
    "newPassword": "newpassword"
}
```

**Authentication:** Token required. Requires knowing the old password or having `ADMINISTER` permission.

### Get User Permissions

```
GET /api/session/data/{dataSource}/users/{username}/permissions
```

**Authentication:** Token required. Requires `ADMINISTER` permission.

**Response:**

```json
{
    "connectionPermissions": {"5": "READ", "8": "READ"},
    "connectionGroupPermissions": {"ROOT": "ADMINISTER"},
    "userPermissions": {},
    "userGroupPermissions": {},
    "systemPermissions": ["CREATE_CONNECTION", "CREATE_USER"]
}
```

### Set User Permissions

```
PUT /api/session/data/{dataSource}/users/{username}/permissions
```

Full replacement of user permissions.

### Update User Permissions

```
PATCH /api/session/data/{dataSource}/users/{username}/permissions
Content-Type: application/json
```

```json
[
    {"op": "add", "path": "/connectionPermissions/5", "value": "READ"},
    {"op": "add", "path": "/systemPermissions", "value": "ADMINISTER"},
    {"op": "remove", "path": "/connectionPermissions/3"}
]
```

### Get Effective Permissions

```
GET /api/session/data/{dataSource}/users/{username}/effectivePermissions
```

Returns the union of all effective permissions inherited from the user's own permissions and their user group memberships.

**Authentication:** Token required. Requires `ADMINISTER` permission.

### Get User Group Membership

```
GET /api/session/data/{dataSource}/users/{username}/userGroups
```

**Authentication:** Token required. Requires `ADMINISTER` permission.

### Update User Group Membership

```
PUT /api/session/data/{dataSource}/users/{username}/userGroups
PATCH /api/session/data/{dataSource}/users/{username}/userGroups
```

---

## User Group Management

### User Group List

```
GET /api/session/data/{dataSource}/userGroups
```

**Authentication:** Token required

### Get Single User Group

```
GET /api/session/data/{dataSource}/userGroups/{id}
```

**Authentication:** Token required

### Create User Group

```
POST /api/session/data/{dataSource}/userGroups
Content-Type: application/json
```

```json
{
    "identifier": "admins",
    "attributes": {}
}
```

**Authentication:** Token required. Requires `CREATE_USER_GROUP` system permission.

### Update User Group

```
PUT /api/session/data/{dataSource}/userGroups/{id}
```

**Authentication:** Token required. Requires `UPDATE` permission on the user group.

### Delete User Group

```
DELETE /api/session/data/{dataSource}/userGroups/{id}
```

**Authentication:** Token required. Requires `DELETE` permission on the user group.

### User Group Permissions

```
GET /api/session/data/{dataSource}/userGroups/{id}/permissions
PUT /api/session/data/{dataSource}/userGroups/{id}/permissions
PATCH /api/session/data/{dataSource}/userGroups/{id}/permissions
```

### User Group Member Management

```
GET /api/session/data/{dataSource}/userGroups/{id}/memberUsers
PUT /api/session/data/{dataSource}/userGroups/{id}/memberUsers
PATCH /api/session/data/{dataSource}/userGroups/{id}/memberUsers

GET /api/session/data/{dataSource}/userGroups/{id}/memberUserGroups
PUT /api/session/data/{dataSource}/userGroups/{id}/memberUserGroups
PATCH /api/session/data/{dataSource}/userGroups/{id}/memberUserGroups
```

---

## Sharing Profiles

### Sharing Profile List

```
GET /api/session/data/{dataSource}/sharingProfiles
```

**Authentication:** Token required

### Get Single Sharing Profile

```
GET /api/session/data/{dataSource}/sharingProfiles/{id}
```

**Authentication:** Token required

### Create Sharing Profile

```
POST /api/session/data/{dataSource}/sharingProfiles
Content-Type: application/json
```

```json
{
    "name": "Shared Session",
    "primaryConnectionIdentifier": "5",
    "attributes": {}
}
```

**Authentication:** Token required. Requires `CREATE_SHARING_PROFILE` system permission.

### Update Sharing Profile

```
PUT /api/session/data/{dataSource}/sharingProfiles/{id}
```

**Authentication:** Token required. Requires `UPDATE` permission on the sharing profile.

### Delete Sharing Profile

```
DELETE /api/session/data/{dataSource}/sharingProfiles/{id}
```

**Authentication:** Token required. Requires `DELETE` permission on the sharing profile.

---

## Active Connections

### Active Connection List

```
GET /api/session/data/{dataSource}/activeConnections
```

Returns all currently active remote desktop connections.

**Authentication:** Token required

### Get Single Active Connection

```
GET /api/session/data/{dataSource}/activeConnections/{id}
```

**Authentication:** Token required

### Manage Active Connection

```
PATCH /api/session/data/{dataSource}/activeConnections/{id}
Content-Type: application/json
```

```json
[
    {"op": "add", "path": "/sharingProfiles/3", "value": "READ"}
]
```

### Active Connection Details

```
GET /api/session/data/{dataSource}/activeConnections/{id}/connection
GET /api/session/data/{dataSource}/activeConnections/{id}/connection/sharingProfiles
```

**Authentication:** Token required

### Get Active Connection Credentials

```
GET /api/session/data/{dataSource}/activeConnections/{id}/credentials
```

Returns user credential information for the active connection.

**Authentication:** Token required. Requires `ADMINISTER` permission on the connection.

---

## History

### User Connection History

```
GET /api/session/data/{dataSource}/history
```

Returns the current user's connection history.

**Authentication:** Token required

### All User History

```
GET /api/session/data/{dataSource}/history/users
```

**Authentication:** Token required. Requires `ADMINISTER` permission.

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `sort` | String | Sort field (`startDate`, `endDate`, `username`, `hostname`) |
| `order` | String | Sort direction (`ASC`, `DESC`) |
| `limit` | int | Maximum number of records to return |
| `identifier` | String | Filter by user/connection identifier |

### Connection History

```
GET /api/session/data/{dataSource}/history/connections
```

### Active Session Log

```
GET /api/session/data/{dataSource}/history/active
```

Returns the list of currently active sessions with their durations.

### History Log Download

```
GET /api/session/data/{dataSource}/history/session/{id}/logs/{log}
GET /api/session/data/{dataSource}/history/connection/{id}/logs/{log}
```

Downloads the associated log file for a historical session or connection.

---

## Tunnel Management

### Active Tunnel List

```
GET /api/session/tunnels
```

Returns the set of all currently active Guacamole tunnel UUIDs.

**Authentication:** Token required

**Response:**

```json
["uuid-1", "uuid-2"]
```

### Create Tunnel

```
POST /api/session/tunnels
Content-Type: application/json
```

**Request Body:**

```json
{
    "connectionIdentifier": "5",
    "authProviderIdentifier": "postgresql",
    "width": 1024,
    "height": 768,
    "dpi": 96,
    "audioMimetypes": ["audio/wav", "audio/ogg"],
    "videoMimetypes": ["video/mp4"],
    "imageMimetypes": ["image/png", "image/jpeg"],
    "timezone": "Asia/Shanghai"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `connectionIdentifier` | String | Yes | Connection identifier |
| `authProviderIdentifier` | String | No | Authentication provider identifier |
| `width` | int | No | Optimal screen width |
| `height` | int | No | Optimal screen height |
| `dpi` | int | No | Optimal resolution (DPI) |
| `timezone` | String | No | Client timezone (e.g., `Asia/Shanghai`) |
| `audioMimetypes` | String[] | No | Supported audio MIME types |
| `videoMimetypes` | String[] | No | Supported video MIME types |
| `imageMimetypes` | String[] | No | Supported image MIME types |

**Authentication:** Token required

**Response:** JSON object containing the tunnel UUID.

### Tunnel Status

```
GET /api/session/tunnels/{uuid}
```

**Authentication:** Token required

**Response:** Tunnel current status information.

### Tunnel Protocol

```
GET /api/session/tunnels/{uuid}/protocol
```

Returns the protocol type used by the tunnel and its parameter information.

**Authentication:** Token required

**Response:**

```json
{
    "name": "rdp",
    "parameters": {
        "hostname": {"type": "TEXT", "name": "hostname"},
        "port": {"type": "NUMERIC", "name": "port"},
        "username": {"type": "TEXT", "name": "username"}
    }
}
```

### Tunnel Active Connection

```
GET /api/session/tunnels/{uuid}/activeConnection
GET /api/session/tunnels/{uuid}/activeConnection/connection
GET /api/session/tunnels/{uuid}/activeConnection/connection/sharingProfiles
```

**Authentication:** Token required

### Stream Data Transfer

**Download stream data:**

```
GET /api/session/tunnels/{uuid}/streams/{index}/{filename}
```

| Parameter | Location | Type | Description |
|-----------|----------|------|-------------|
| `index` | Path | int | Stream index |
| `filename` | Path | String | Filename |
| `type` | Query | String | MIME type (default `application/octet-stream`) |

**Upload stream data:**

```
POST /api/session/tunnels/{uuid}/streams/{index}/{filename}
Content-Type: application/octet-stream
```

**Authentication:** Token required

---

## Schema

### Get Schema

```
GET /api/session/data/{dataSource}/schema
```

Returns the property schema overview for the current authentication provider.

### User Attribute Definitions

```
GET /api/session/data/{dataSource}/schema/userAttributes
```

Returns the form collection describing possible user object attributes.

### User Group Attribute Definitions

```
GET /api/session/data/{dataSource}/schema/userGroupAttributes
```

### Connection Attribute Definitions

```
GET /api/session/data/{dataSource}/schema/connectionAttributes
```

### Connection Group Attribute Definitions

```
GET /api/session/data/{dataSource}/schema/connectionGroupAttributes
```

### Sharing Profile Attribute Definitions

```
GET /api/session/data/{dataSource}/schema/sharingProfileAttributes
```

### Protocol Information

```
GET /api/session/data/{dataSource}/schema/protocols
```

Returns a mapping of all system-defined protocol names to protocol information. Each protocol includes its available parameter definitions.

**Authentication:** Token required

---

## Extensions and Patches

### HTML Patches

```
GET /api/patches
```

Returns the list of HTML fragments from all enabled extensions, which will be injected into the frontend DOM.

**Authentication:** Not required (loaded before AngularJS application starts).

**Response:**

```json
[
    "<meta name='guacamole-extension' content='totp'>...",
    "<meta name='guacamole-extension' content='duo'>..."
]
```

### Extension REST Endpoints

```
GET /api/ext/{identifier}/
```

Sub-resource endpoints registered by each extension, provided by `AuthenticationProvider.getResource()`:

| Endpoint | SSO Type | Purpose |
|----------|----------|---------|
| `/api/ext/cas/` | CAS SSO | CAS authentication redirect and callback |
| `/api/ext/openid/` | OpenID Connect | OpenID authentication redirect and callback |
| `/api/ext/saml/` | SAML 2.0 | SAML authentication redirect and callback |
| `/api/session/ext/{dataSource}/` | QuickConnect | QuickConnect interface |

These endpoints are provided by the `org.apache.guacamole.auth.sso` package auto-scanned by Jersey.

---

## Languages

### Language List

```
GET /api/languages
```

Returns the list of installed language packs.

**Response:**

```json
{
    "en": {"name": "English"},
    "zh": {"name": "Chinese"},
    "de": {"name": "Deutsch"},
    ...
}
```

### Translation Files

```
GET /translations/{lang}.json
```

Returns the translation JSON file for the specified language.

**Supported Languages:** `ca` (Catalan), `cs` (Czech), `de` (German), `en` (English), `es` (Spanish), `fr` (French), `it` (Italian), `ja` (Japanese), `ko` (Korean), `nl` (Dutch), `no` (Norwegian), `pt` (Portuguese), `ru` (Russian), `zh` (Chinese).

**Response:** JSON object of translation key-value pairs.

---

## System Configuration

REST endpoints for the system configuration module, managing branding, theme, security policy, and announcements. Requires at least one JDBC extension to be enabled.

### Get Public Configuration

```
GET /api/config
```

**Authentication:** Not required. This endpoint is publicly accessible for the frontend to load branding, theme, and announcement configuration.

**Response:**

```json
{
    "branding": {
        "siteName": "My Company Portal",
        "logo": "/api/settings/files/a1b2c3d4",
        "favicon": "/api/settings/files/b2c3d4e5",
        "copyright": "© 2026 My Company"
    },
    "theme": {
        "primaryColor": "#1a56db",
        "accentColor": "#0694a2",
        "mode": "light"
    },
    "announcement": {
        "message": "System maintenance tonight",
        "level": "warning",
        "enabled": "true",
        "startTime": "2026-06-15T22:00:00.000Z",
        "endTime": "2026-06-16T06:00:00.000Z",
        "closable": "true"
    }
}
```

All values are of type String; the frontend parses boolean/integer/datetime itself.

### Get All Configuration (Admin)

```
GET /api/settings
Guacamole-Token: xxx
```

**Authentication:** Token required. Requires `SYSTEM_ADMINISTER` permission.

**Response:** Configuration item list (25 entries), each containing `key`, `value`, `type`, `group`, `updatedBy`, `updatedAt` metadata.

### Update Configuration Item (Admin)

```
PUT /api/settings/{key}
Content-Type: application/json
Guacamole-Token: xxx

{ "value": "#1a73e8" }
```

**Authentication:** Token required. Requires `SYSTEM_ADMINISTER` permission.

**Behavior:**

- Updates an existing configuration row, does not create new rows (key must be pre-populated by DDL)
- Color values (`theme.*_color`) are validated for `#RRGGBB` format; invalid values return 400
- `null` values are stored as empty strings, not deleting the row
- Cache is immediately invalidated after update

### Upload File (Admin)

```
POST /api/settings/files
Content-Type: multipart/form-data
Guacamole-Token: xxx
```

**Form Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | File | Yes | Upload file (SVG/PNG/JPG/ICO/GIF, max 2MB) |
| `category` | String | No | Category, default `branding` |

**Authentication:** Token required. Requires `SYSTEM_ADMINISTER` permission.

**Response:**

```json
{
    "fileId": "a1b2c3d4-...",
    "filename": "logo.png",
    "mimeType": "image/png",
    "size": 12345,
    "url": "/api/settings/files/a1b2c3d4-..."
}
```

### Get File (Public)

```
GET /api/settings/files/{fileId}
```

**Authentication:** Not required. This endpoint is publicly accessible (Logo, Favicon etc. need to be directly referenced by `<img>` / `<link>` tags).

**Response:** File stream, `Content-Type` is the MIME type from upload time, `Cache-Control: public, max-age=86400`.

### Delete File (Admin)

```
DELETE /api/settings/files/{fileId}
Guacamole-Token: xxx
```

**Authentication:** Token required. Requires `SYSTEM_ADMINISTER` permission.

**Behavior:** Deletes both filesystem and database records simultaneously.

---

## General Notes

### Authentication Token Passing

All authenticated endpoints support the following token passing methods (priority from high to low):

1. **HTTP Header:** `Guacamole-Token: a1b2c3d4...`
2. **Query Parameter:** `GET /api/...?token=a1b2c3d4...`

```
# Header method (recommended)
GET /api/session/data/postgresql/connections
Guacamole-Token: a1b2c3d4...

# Query parameter method
GET /api/session/data/postgresql/connections?token=a1b2c3d4...
```

### Content-Type

| Endpoint Type | Content-Type |
|--------------|-------------|
| JSON API (most endpoints) | `application/json` |
| Stream data transfer | `application/octet-stream` |
| Login endpoint | `application/x-www-form-urlencoded` |

### Response Format

**Success Response:** HTTP 2xx, JSON response body.

**Client Error (4xx)**:
```json
{
    "message": "Permission Denied.",
    "type": "PERMISSION_DENIED",
    "expected": null,
    "translatableMessage": null
}
```

**Server Error (5xx)**:
```json
{
    "message": "Internal server error.",
    "type": "INTERNAL_ERROR",
    "expected": null,
    "translatableMessage": null
}
```

### Permission Model

| Object Permission Level | Description |
|------------------------|-------------|
| `READ` | Can view and use connections |
| `UPDATE` | Can modify connection configuration |
| `DELETE` | Can delete connections |
| `ADMINISTER` | Full control (includes permission management, inherits all lower-level permissions) |

| System Permission | Description |
|------------------|-------------|
| `CREATE_CONNECTION` | Create connections |
| `CREATE_CONNECTION_GROUP` | Create connection groups |
| `CREATE_SHARING_PROFILE` | Create sharing profiles |
| `CREATE_USER` | Create users |
| `CREATE_USER_GROUP` | Create user groups |
| `ADMINISTER` | Full system administration permission |

### Error Types

| Error Type | HTTP Status Code | Description |
|-----------|-----------------|-------------|
| `BAD_REQUEST` | 400 | Invalid request parameters |
| `UNAUTHORIZED` | 401 | Missing authentication token |
| `INVALID_CREDENTIALS` | 403 | Invalid credentials |
| `PERMISSION_DENIED` | 403 | Insufficient permissions |
| `RESOURCE_NOT_FOUND` | 404 | Resource not found |
| `INTERNAL_ERROR` | 500 | Internal server error |
| `SERVER_BUSY` | 503 | Server busy |

### Data Source Identifiers

The value of the `{dataSource}` path parameter depends on the enabled authentication providers:

| Identifier | Authentication Provider |
|-----------|----------------------|
| `mysql` | MySQL JDBC |
| `postgresql` | PostgreSQL JDBC |
| `sqlserver` | SQL Server JDBC |
| `ldap` | LDAP |
| `json` | JSON encrypted authentication |
| `header` | HTTP header authentication |
| `file` | File authentication (fallback) |

When multiple authentication providers are enabled simultaneously, each provider exposes an independent data source.
