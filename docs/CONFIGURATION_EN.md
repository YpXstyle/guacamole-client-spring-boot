# Configuration Reference

[← Back to Documentation](../README.md#documentation)

Complete configuration reference for Guacamole Spring Boot.

## Table of Contents

- [Introduction](#introduction)
- [Core Configuration](#core-configuration)
- [Database Connection Configuration](#database-connection-configuration)
- [Authentication Extension Configuration](#authentication-extension-configuration)
  - [JDBC PostgreSQL](#jdbc-postgresql)
  - [JDBC MySQL](#jdbc-mysql)
  - [JDBC SQL Server](#jdbc-sql-server)
  - [Header Authentication](#header-authentication)
  - [JSON Authentication](#json-authentication)
  - [LDAP Authentication](#ldap-authentication)
  - [RADIUS Authentication](#radius-authentication)
  - [TOTP Two-Factor Authentication](#totp-two-factor-authentication)
  - [DUO Two-Factor Authentication](#duo-two-factor-authentication)
  - [QuickConnect](#quickconnect)
  - [CAS Single Sign-On](#cas-single-sign-on)
  - [OpenID Connect Single Sign-On](#openid-connect-single-sign-on)
  - [SAML 2.0 Single Sign-On](#saml-20-single-sign-on)
- [Feature Extension Configuration](#feature-extension-configuration)
  - [History Session Recording](#history-session-recording)
  - [Vault KSM Secrets Management](#vault-ksm-secrets-management)
- [System Configuration Module (Runtime Modifiable)](#system-configuration-module-runtime-modifiable)
  - [Branding](#branding)
  - [Theme](#theme)
  - [Security Policy](#security-policy)
  - [System Announcement](#system-announcement)
  - [Configuration Priority](#configuration-priority)
- [Multi-Authentication Chain Configuration](#multi-authentication-chain-configuration)
- [Environment Variable Overrides](#environment-variable-overrides)
- [Common Configuration Scenarios](#common-configuration-scenarios)

---

## Introduction

Guacamole Spring Boot uses a **two-layer property system**:

### Layer 1: Spring Boot Native Properties

These properties are handled directly by the Spring Boot framework, following standard Spring Boot binding rules. Typical examples:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}

logging:
  level:
    org.apache.guacamole: info
```

### Layer 2: Guacamole Bridge Properties

These properties are prefixed with `guacamole.*` and read through Guacamole's `Environment` bridge. The `Environment` implementation maps values from `application.yml` to legacy `guacamole.properties` property names, allowing all extension module code to work without modification:

```
application.yml path                              guacamole.properties legacy key
guacamole.guacd.hostname                 →        guacd-hostname
guacamole.auth.ldap.ldap-hostname        →        ldap-hostname
guacamole.auth.postgresql.batch-size     →        postgresql-batch-size
```

Bridge rule: In YAML paths under `guacamole.*`, the last segment (e.g., `ldap-hostname`) directly corresponds to the key name in `guacamole.properties`.

### Extension Enablement Rules

All authentication and feature extensions follow a unified enablement pattern:

```yaml
guacamole:
  auth:
    <module-name>:
      enabled: true    # true = enabled, false = disabled
```

Two conditions must be simultaneously met for a module to activate:
1. Maven dependency exists on classpath (add starter in `guacamole/pom.xml`)
2. `enabled: true` is set in `application.yml`

**All extension modules are disabled by default.** The legacy Guacamole behavior where extension JARs in `GUACAMOLE_HOME/extensions/` are automatically loaded has been replaced.

---

## Core Configuration

### Server Port and guacd

```yaml
server:
  port: 8080

guacamole:
  guacd:
    hostname: localhost     # guacd daemon hostname
    port: 4822              # guacd port (default 4822)
    ssl: false              # Whether to use SSL/TLS connection to guacd
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `server.port` | int | No | `8080` | HTTP server port |
| `guacamole.guacd.hostname` | string | No | `localhost` | guacd proxy daemon hostname or IP |
| `guacamole.guacd.port` | int | No | `4822` | guacd port |
| `guacamole.guacd.ssl` | boolean | No | `false` | Enable SSL/TLS connection to guacd |

### Timezone

Set via JVM system property at startup:

```bash
java -Duser.timezone=Asia/Shanghai -jar guacamole/target/guacamole-*.jar
```

Docker environment:

```yaml
environment:
  JAVA_TOOL_OPTIONS: "-Duser.timezone=Asia/Shanghai"
```

---

## Database Connection Configuration

### Important Note

**DataSource is managed by Spring Boot's `spring.datasource.*`.** Legacy JDBC connection properties from `guacamole.properties` (`<db>-hostname`, `<db>-port`, `<db>-database`, `<db>-username`, `<db>-password`, etc.) are **no longer used for database connections**.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

### Database Driver Class Names

| Database | Driver Class |
|----------|-------------|
| PostgreSQL | `org.postgresql.Driver` |
| MySQL | `com.mysql.cj.jdbc.Driver` |
| SQL Server | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |

### Connection Pool Tuning

Spring Boot DataSource supports HikariCP connection pool tuning:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### Legacy JDBC Property Status

The legacy properties listed below are **defined in code** but **no longer used for database connections**. They exist only in `Environment` classes and are not called by actual business code. Listed here for reference when identifying historical configurations:

| Zombie Property (Global) | Description |
|--------------------------|-------------|
| `<db>-hostname` | No longer used |
| `<db>-port` | No longer used |
| `<db>-database` | No longer used |
| `<db>-username` | No longer used |
| `<db>-password` | No longer used |
| All `<db>-ssl-*` properties | No longer used |
| `<db>-driver` | No longer used |
| `mysql-server-timezone` | No longer used |
| `postgresql-socket-timeout` | No longer used |
| `postgresql-default-statement-timeout` | No longer used |
| `sqlserver-instance` | No longer used |

---

## Authentication Extension Configuration

### JDBC PostgreSQL

PostgreSQL database authentication. Uses MyBatis to access user, connection, and permission tables in the database.

```yaml
guacamole:
  auth:
    postgresql:
      enabled: true
```

**DataSource must also be configured:**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.postgresql.enabled` | boolean | Yes | `false` | Enable PostgreSQL authentication |
| `guacamole.auth.postgresql.user-required` | boolean | No | `false` | Whether to require user account in database |
| `guacamole.auth.postgresql.absolute-max-connections` | int | No | `0` | Global concurrent connection limit (0=unlimited) |
| `guacamole.auth.postgresql.default-max-connections` | int | No | `0` | Per-connection default concurrent limit |
| `guacamole.auth.postgresql.default-max-group-connections` | int | No | `0` | Per-connection-group default concurrent limit |
| `guacamole.auth.postgresql.default-max-connections-per-user` | int | No | `0` | Per-user per-connection default limit |
| `guacamole.auth.postgresql.default-max-group-connections-per-user` | int | No | `1` | Per-user per-connection-group default limit |
| `guacamole.auth.postgresql.batch-size` | int | No | `5000` | SQL batch size |
| `guacamole.auth.postgresql.auto-create-accounts` | boolean | No | `false` | Auto-create database accounts for users authenticated by other sources |

**Complete Example:**

```yaml
guacamole:
  auth:
    postgresql:
      enabled: true
      user-required: false
      absolute-max-connections: 0
      default-max-connections: 0
      default-max-group-connections: 0
      default-max-connections-per-user: 0
      default-max-group-connections-per-user: 1
      batch-size: 5000
      auto-create-accounts: false

spring:
  datasource:
    url: jdbc:postgresql://192.168.1.100:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

---

### JDBC MySQL

MySQL / MariaDB database authentication.

```yaml
guacamole:
  auth:
    mysql:
      enabled: true

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/guacamole?useSSL=false&serverTimezone=UTC
    username: guacamole
    password: ${MYSQL_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.mysql.enabled` | boolean | Yes | `false` | Enable MySQL authentication |
| `guacamole.auth.mysql.user-required` | boolean | No | `false` | Whether to require user account in database |
| `guacamole.auth.mysql.absolute-max-connections` | int | No | `0` | Global concurrent connection limit (0=unlimited) |
| `guacamole.auth.mysql.default-max-connections` | int | No | `0` | Per-connection default concurrent limit |
| `guacamole.auth.mysql.default-max-group-connections` | int | No | `0` | Per-connection-group default concurrent limit |
| `guacamole.auth.mysql.default-max-connections-per-user` | int | No | `0` | Per-user per-connection default limit |
| `guacamole.auth.mysql.default-max-group-connections-per-user` | int | No | `1` | Per-user per-connection-group default limit |
| `guacamole.auth.mysql.batch-size` | int | No | `1000` | SQL batch size |
| `guacamole.auth.mysql.auto-create-accounts` | boolean | No | `false` | Auto-create database accounts for users authenticated by other sources |

**Complete Example:**

```yaml
guacamole:
  auth:
    mysql:
      enabled: true
      user-required: false
      absolute-max-connections: 0
      default-max-connections: 0
      default-max-group-connections: 0
      default-max-connections-per-user: 0
      default-max-group-connections-per-user: 1
      batch-size: 1000
      auto-create-accounts: false

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/guacamole?useSSL=false&serverTimezone=UTC
    username: guacamole
    password: ${MYSQL_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

---

### JDBC SQL Server

Microsoft SQL Server database authentication.

```yaml
guacamole:
  auth:
    sqlserver:
      enabled: true

spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=guacamole
    username: guacamole
    password: ${MSSQL_PASSWORD}
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.sqlserver.enabled` | boolean | Yes | `false` | Enable SQL Server authentication |
| `guacamole.auth.sqlserver.user-required` | boolean | No | `false` | Whether to require user account in database |
| `guacamole.auth.sqlserver.absolute-max-connections` | int | No | `0` | Global concurrent connection limit (0=unlimited) |
| `guacamole.auth.sqlserver.default-max-connections` | int | No | `0` | Per-connection default concurrent limit |
| `guacamole.auth.sqlserver.default-max-group-connections` | int | No | `0` | Per-connection-group default concurrent limit |
| `guacamole.auth.sqlserver.default-max-connections-per-user` | int | No | `0` | Per-user per-connection default limit |
| `guacamole.auth.sqlserver.default-max-group-connections-per-user` | int | No | `1` | Per-user per-connection-group default limit |
| `guacamole.auth.sqlserver.batch-size` | int | No | `500` | SQL batch size |
| `guacamole.auth.sqlserver.auto-create-accounts` | boolean | No | `false` | Auto-create database accounts for users authenticated by other sources |

**Complete Example:**

```yaml
guacamole:
  auth:
    sqlserver:
      enabled: true
      user-required: false
      absolute-max-connections: 0
      default-max-connections: 0
      default-max-group-connections: 0
      default-max-connections-per-user: 0
      default-max-group-connections-per-user: 1
      batch-size: 500
      auto-create-accounts: false

spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=guacamole
    username: guacamole
    password: ${MSSQL_PASSWORD}
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
```

---

### Header Authentication

Reverse proxy SSO — authenticates users based on HTTP request headers (typically set by Nginx, Apache, or IdP proxies).

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: REMOTE_USER    # Optional, default REMOTE_USER
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.header.enabled` | boolean | Yes | `false` | Enable Header authentication |
| `guacamole.auth.header.http-auth-header` | string | No | `REMOTE_USER` | HTTP header containing the authenticated username |

**Nginx Configuration Example:**

```nginx
location / {
    proxy_pass http://localhost:8080;
    proxy_set_header REMOTE_USER $remote_user;
    auth_basic "Guacamole";
    auth_basic_user_file /etc/nginx/.htpasswd;
}
```

---

### JSON Authentication

Encrypted token authentication, no database required. All connection and user data is encrypted in the JSON payload submitted by the client, with server-side decryption and signature verification.

```yaml
guacamole:
  auth:
    json:
      enabled: true
      json-secret-key: <base64-encoded-256-bit-key>
      json-trusted-networks: 192.168.1.0/24,10.0.0.0/8   # Optional
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.json.enabled` | boolean | Yes | `false` | Enable JSON authentication |
| `guacamole.auth.json.json-secret-key` | string (base64) | **Yes** | -- | Base64-encoded 256-bit AES key for encryption and HMAC signing |
| `guacamole.auth.json.json-trusted-networks` | string (comma-separated) | No | Allow all | CIDR network list (comma-separated), only allow requests from these sources |

**Generate Key:**

```bash
openssl rand -base64 32
```

**Note:** The client must use the same key when generating tokens. Tokens use AES-256-CBC encryption + HMAC-SHA256 signing.

---

### LDAP Authentication

Supports LDAP directory and Microsoft Active Directory authentication.

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ldap.example.com
      ldap-user-base-dn: ou=users,dc=example,dc=com
      ldap-username-attribute: uid
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.ldap.enabled` | boolean | Yes | `false` | Enable LDAP authentication |
| `guacamole.auth.ldap.ldap-hostname` | string | No | `localhost` | LDAP server hostname |
| `guacamole.auth.ldap.ldap-port` | int | No | `389` (or `636` for SSL) | LDAP server port |
| `guacamole.auth.ldap.ldap-user-base-dn` | string | **Yes** | -- | User search base DN |
| `guacamole.auth.ldap.ldap-username-attribute` | string | No | `uid` | Username field (e.g., `uid`, `sAMAccountName`) |
| `guacamole.auth.ldap.ldap-config-base-dn` | string | No | -- | Guacamole configuration base DN |
| `guacamole.auth.ldap.ldap-group-base-dn` | string | No | -- | Group search base DN |
| `guacamole.auth.ldap.ldap-group-name-attribute` | string | No | `cn` | Group name field |
| `guacamole.auth.ldap.ldap-search-bind-dn` | string | No | -- | Search bind DN (leave empty for anonymous search) |
| `guacamole.auth.ldap.ldap-search-bind-password` | string | No | -- | Search bind password |
| `guacamole.auth.ldap.ldap-encryption-method` | enum | No | `none` | Encryption method: `none`, `ssl`, `starttls` |
| `guacamole.auth.ldap.ldap-max-search-results` | int | No | `1000` | Maximum LDAP query results |
| `guacamole.auth.ldap.ldap-dereference-aliases` | enum | No | `never` | Alias dereferencing: `never`, `always`, `finding`, `searching` |
| `guacamole.auth.ldap.ldap-user-search-filter` | string | No | `(objectClass=*)` | User search LDAP filter (`{0}` replaced with username) |
| `guacamole.auth.ldap.ldap-group-search-filter` | string | No | `(objectClass=*)` | Group search LDAP filter |
| `guacamole.auth.ldap.ldap-follow-referrals` | boolean | No | `false` | Whether to follow LDAP referrals |
| `guacamole.auth.ldap.ldap-max-referral-hops` | int | No | `5` | Maximum referral hops |
| `guacamole.auth.ldap.ldap-operation-timeout` | int | No | `30` | LDAP operation timeout (seconds) |
| `guacamole.auth.ldap.ldap-network-timeout` | int | No | `30000` | LDAP network timeout (milliseconds) |
| `guacamole.auth.ldap.ldap-user-attributes` | string (comma-separated) | No | -- | Additional LDAP attributes (comma-separated), exposed for connection use |
| `guacamole.auth.ldap.ldap-member-attribute` | string | No | `member` | Group member enumeration attribute |
| `guacamole.auth.ldap.ldap-member-attribute-type` | enum | No | `dn` | Member attribute type: `dn` (value is DN), `uid` (value is username) |

**Active Directory Configuration Example:**

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ad.example.com
      ldap-port: 389
      ldap-username-attribute: sAMAccountName
      ldap-user-base-dn: ou=Users,dc=example,dc=com
      ldap-user-search-filter: (&(objectCategory=person)(objectClass=user)(sAMAccountName={0}))
      ldap-group-base-dn: ou=Groups,dc=example,dc=com
      ldap-group-search-filter: (&(objectClass=group)(member={0}))
      ldap-member-attribute: member
      ldap-member-attribute-type: dn
```

**Multiple LDAP Servers:** Configure in `GUACAMOLE_HOME/ldap-servers.yml`. When this file exists, it takes priority over the properties above.

---

### RADIUS Authentication

Two-factor authentication based on the RADIUS protocol.

```yaml
guacamole:
  auth:
    radius:
      enabled: true
      radius-hostname: radius.example.com
      radius-shared-secret: ${RADIUS_SECRET}
      radius-auth-protocol: pap
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.radius.enabled` | boolean | Yes | `false` | Enable RADIUS authentication |
| `guacamole.auth.radius.radius-hostname` | string | No | `localhost` | RADIUS server hostname |
| `guacamole.auth.radius.radius-auth-port` | int | No | `1812` | RADIUS authentication port |
| `guacamole.auth.radius.radius-acct-port` | int | No | `1813` | RADIUS accounting port |
| `guacamole.auth.radius.radius-shared-secret` | string | **Yes** | -- | RADIUS shared secret |
| `guacamole.auth.radius.radius-auth-protocol` | enum | **Yes** | -- | Authentication protocol (lowercase): `pap`, `chap`, `mschapv1`, `mschapv2`, `eap-md5`, `eap-tls`, `eap-ttls` |
| `guacamole.auth.radius.radius-max-retries` | int | No | `5` | Maximum retry count |
| `guacamole.auth.radius.radius-timeout` | int | No | `60` | Timeout (seconds) |
| `guacamole.auth.radius.radius-ca-file` | string | No | `{guacamoleHome}/ca.crt` | CA certificate file path |
| `guacamole.auth.radius.radius-ca-type` | string | No | `pem` | CA file type: `pem`, `pkcs12`, `der` |
| `guacamole.auth.radius.radius-ca-password` | string | No | -- | CA file password |
| `guacamole.auth.radius.radius-key-file` | string | No | `{guacamoleHome}/radius.key` | Client key file path |
| `guacamole.auth.radius.radius-key-type` | string | No | `pem` | Key file type: `pem`, `pkcs12`, `der` |
| `guacamole.auth.radius.radius-key-password` | string | No | -- | Key file password |
| `guacamole.auth.radius.radius-trust-all` | boolean | No | `false` | Trust all server certificates |
| `guacamole.auth.radius.radius-eap-ttls-inner-protocol` | enum | EAP-TTLS only | -- | Inner protocol for EAP-TTLS |
| `guacamole.auth.radius.radius-nas-ip` | string | No | Auto-detect | NAS IP address sent to RADIUS server |

---

### TOTP Two-Factor Authentication

Time-based One-Time Password (RFC 6238). Compatible with Google Authenticator, Authy, FreeOTP, etc.

```yaml
guacamole:
  auth:
    totp:
      enabled: true
      totp-issuer: Apache Guacamole
      totp-period: 30
      totp-mode: sha1
      totp-digits: 6
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.totp.enabled` | boolean | Yes | `false` | Enable TOTP authentication |
| `guacamole.auth.totp.totp-issuer` | string | No | `Apache Guacamole` | Issuer name displayed in authenticator app |
| `guacamole.auth.totp.totp-digits` | int | No | `6` | Verification code digits (6 or 8) |
| `guacamole.auth.totp.totp-period` | int | No | `30` | Verification code validity period (seconds) |
| `guacamole.auth.totp.totp-mode` | enum | No | `sha1` | Hash algorithm: `sha1`, `sha256`, `sha512` |

**Note:** TOTP must be combined with another authentication provider (such as JDBC or LDAP). Users need to complete TOTP key registration on first login.

---

### DUO Two-Factor Authentication

```yaml
guacamole:
  auth:
    duo:
      enabled: true
      duo-api-hostname: api-XXXXXXXX.duosecurity.com
      duo-integration-key: ${DUO_IKEY}
      duo-secret-key: ${DUO_SKEY}
      duo-application-key: ${DUO_AKEY}
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.duo.enabled` | boolean | Yes | `false` | Enable Duo authentication |
| `guacamole.auth.duo.duo-api-hostname` | string | **Yes** | -- | Duo API hostname (e.g., `api-XXXXXXXX.duosecurity.com`) |
| `guacamole.auth.duo.duo-integration-key` | string | **Yes** | -- | Duo integration key (exactly 20 characters) |
| `guacamole.auth.duo.duo-secret-key` | string | **Yes** | -- | Duo secret key (exactly 40 characters) |
| `guacamole.auth.duo.duo-application-key` | string | **Yes** | -- | Any random key (at least 40 characters) |

**Important:** This extension uses Duo Web SDK v2, which Duo deprecated in March 2024. Production environments need to upgrade to Web SDK v4 (Universal Prompt).

---

### QuickConnect

Create ad-hoc instant connections via URIs in the Guacamole interface.

```yaml
guacamole:
  auth:
    quickconnect:
      enabled: true
```

**Supported URI Formats:**

```
rdp://hostname:3389
vnc://hostname:5900
ssh://hostname:22?username=user&password=pass
telnet://hostname:23
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.quickconnect.enabled` | boolean | Yes | `false` | Enable QuickConnect |
| `guacamole.auth.quickconnect.quickconnect-allowed-parameters` | string (comma-separated) | No | All allowed | Allowed connection parameters list |
| `guacamole.auth.quickconnect.quickconnect-denied-parameters` | string (comma-separated) | No | None denied | Denied connection parameters list |

**Parameter Filtering Example:**

```yaml
guacamole:
  auth:
    quickconnect:
      enabled: true
      quickconnect-allowed-parameters: hostname,port,protocol
      quickconnect-denied-parameters: password,private-key
```

---

### CAS Single Sign-On

CAS single sign-on authentication.

```yaml
guacamole:
  auth:
    sso-cas:
      enabled: true
      cas-authorization-endpoint: https://cas.example.org/cas
      cas-redirect-uri: http://localhost:8080/
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.sso-cas.enabled` | boolean | Yes | `false` | Enable CAS authentication |
| `guacamole.auth.sso-cas.cas-authorization-endpoint` | URI | **Yes** | -- | CAS server authentication endpoint URL |
| `guacamole.auth.sso-cas.cas-redirect-uri` | URI | **Yes** | -- | CAS callback URL after authentication (Guacamole access URL) |
| `guacamole.auth.sso-cas.cas-clearpass-key` | string | No | -- | ClearPass password decryption private key file path |
| `guacamole.auth.sso-cas.cas-group-attribute` | string | No | -- | CAS attribute name for group membership |
| `guacamole.auth.sso-cas.cas-group-format` | enum | No | `plain` | Group name format: `plain` or `ldap` |
| `guacamole.auth.sso-cas.cas-group-ldap-base-dn` | string | No | -- | Base DN for LDAP-format groups |
| `guacamole.auth.sso-cas.cas-group-ldap-attribute` | string | No | `cn` | LDAP group name attribute |

---

### OpenID Connect Single Sign-On

OpenID Connect authentication (supports Google, Okta, Keycloak, Auth0, Azure AD, etc.).

```yaml
guacamole:
  auth:
    sso-openid:
      enabled: true
      openid-authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
      openid-jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
      openid-issuer: https://accounts.google.com
      openid-client-id: ${OPENID_CLIENT_ID}
      openid-redirect-uri: http://localhost:8080/
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.sso-openid.enabled` | boolean | Yes | `false` | Enable OpenID Connect authentication |
| `guacamole.auth.sso-openid.openid-authorization-endpoint` | URI | **Yes** | -- | OpenID authentication endpoint URL |
| `guacamole.auth.sso-openid.openid-jwks-endpoint` | URI | **Yes** | -- | JWKS endpoint URL for JWT verification |
| `guacamole.auth.sso-openid.openid-issuer` | URI | **Yes** | -- | JWT issuer |
| `guacamole.auth.sso-openid.openid-client-id` | string | **Yes** | -- | OpenID client ID |
| `guacamole.auth.sso-openid.openid-redirect-uri` | URI | **Yes** | -- | OpenID callback URL after authentication |
| `guacamole.auth.sso-openid.openid-scope` | string | No | `openid email profile` | Space-separated OpenID scopes |
| `guacamole.auth.sso-openid.openid-username-claim-type` | string | No | `email` | JWT claim type for username field |
| `guacamole.auth.sso-openid.openid-groups-claim-type` | string | No | `groups` | JWT claim type for group membership |
| `guacamole.auth.sso-openid.openid-allowed-clock-skew` | int | No | `30` | Allowed clock skew (seconds) |
| `guacamole.auth.sso-openid.openid-max-token-validity` | int | No | `300` | Maximum token validity (minutes, default 5 hours) |
| `guacamole.auth.sso-openid.openid-max-nonce-validity` | int | No | `10` | Maximum nonce validity (minutes) |

**Provider Configuration Examples:**

Google:

```yaml
openid-authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
openid-jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
openid-issuer: https://accounts.google.com
openid-username-claim-type: email
```

Okta:

```yaml
openid-authorization-endpoint: https://dev-XXXX.okta.com/oauth2/v1/authorize
openid-jwks-endpoint: https://dev-XXXX.okta.com/oauth2/v1/keys
openid-issuer: https://dev-XXXX.okta.com
openid-username-claim-type: preferred_username
```

Keycloak:

```yaml
openid-authorization-endpoint: https://keycloak.example.com/realms/myrealm/protocol/openid-connect/auth
openid-jwks-endpoint: https://keycloak.example.com/realms/myrealm/protocol/openid-connect/certs
openid-issuer: https://keycloak.example.com/realms/myrealm
```

---

### SAML 2.0 Single Sign-On

SAML 2.0 single sign-on, supporting both IdP metadata URL and manual configuration.

```yaml
guacamole:
  auth:
    sso-saml:
      enabled: true
      saml-callback-url: http://localhost:8080/
      # Method A: IdP metadata URL (recommended)
      saml-idp-metadata-url: https://idp.example.com/metadata.xml
      # Method B: Manual configuration (when no metadata)
      saml-idp-url: https://idp.example.com/sso
      saml-entity-id: http://localhost:8080/
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.auth.sso-saml.enabled` | boolean | Yes | `false` | Enable SAML authentication |
| `guacamole.auth.sso-saml.saml-callback-url` | URI | **Yes** | -- | SAML callback base URL (Guacamole access URL) |
| `guacamole.auth.sso-saml.saml-idp-metadata-url` | URI | Conditionally required | -- | IdP metadata XML URL (Method A) |
| `guacamole.auth.sso-saml.saml-idp-url` | URI | Conditionally required | -- | IdP SSO login URL (Method B) |
| `guacamole.auth.sso-saml.saml-entity-id` | URI | Conditionally required | -- | SP entity ID (required when not provided in metadata) |
| `guacamole.auth.sso-saml.saml-strict` | boolean | No | `true` | Enforce strict security checks |
| `guacamole.auth.sso-saml.saml-debug` | boolean | No | `false` | Enable SAML debug logging |
| `guacamole.auth.sso-saml.saml-compress-request` | boolean | No | `true` | Compress SAML authentication request |
| `guacamole.auth.sso-saml.saml-compress-response` | boolean | No | `true` | Compress SAML response |
| `guacamole.auth.sso-saml.saml-group-attribute` | string | No | `groups` | Group member attribute name in IdP response |
| `guacamole.auth.sso-saml.saml-auth-timeout` | int | No | `5` | SAML authentication timeout (minutes) |

---

## Feature Extension Configuration

### History Session Recording

Stores session recordings and connection history.

```yaml
guacamole:
  history:
    enabled: true
    recording-search-path: /var/lib/guacamole/recordings
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.history.enabled` | boolean | Yes | `false` | Enable History recording |
| `guacamole.history.recording-search-path` | string | No | `/var/lib/guacamole/recordings` | Recording file search path |

**Default Value Note:** The code-level default is `/var/lib/guacamole/recordings`. The default `application.yml` sets it to `${GUACAMOLE_HISTORY_RECORDING_PATH:/tmp/guacamole/recordings}`, so the effective default is `/tmp/guacamole/recordings` (overridable via `GUACAMOLE_HISTORY_RECORDING_PATH` environment variable).

**Note:** Session recordings are generated by guacd. `recording-search-path` must point to the directory where guacd writes recording files, and the Guacamole web application needs read access to this directory.

**Docker Shared Volume:**

```yaml
volumes:
  - /host/path/recordings:/tmp/guacamole/recordings
```

---

### Vault KSM Secrets Management

Keeper Secrets Manager credential injection integration.

```yaml
guacamole:
  vault:
    ksm:
      enabled: true
      ksm-config: "keeper://<base64-encoded-config>"
      ksm-allow-unverified-cert: false
```

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `guacamole.vault.ksm.enabled` | boolean | Yes | `false` | Enable KSM Vault |
| `guacamole.vault.ksm.ksm-config` | string | **Yes** | -- | Base64-encoded config generated by Keeper Commander CLI |
| `guacamole.vault.ksm.ksm-allow-unverified-cert` | boolean | No | `false` | Accept unverified server certificates |

The KSM module also reads the following files:

- **Token mapping file:** `<GUACAMOLE_HOME>/ksm-token-mapping.yml` — Maps connection parameter tokens to Keeper key names
- **Properties file:** `<GUACAMOLE_HOME>/guacamole.properties.ksm` — Maps Guacamole property names to Keeper key names

See [vault-module_EN.md](vault-module_EN.md) for details.

---

## System Configuration Module (Runtime Modifiable)

The system configuration module stores branding, theme, security policy, and announcement management in the database, allowing administrators to modify in real-time via the web interface without restarting the application. Requires at least one JDBC extension (PostgreSQL/MySQL/SQL Server) to be enabled.

### Prerequisites

1. Execute `003-create-system-config.sql` DDL to create `guacamole_system_config` and `guacamole_system_file` tables
2. Enable JDBC extension in `application.yml` (e.g., `guacamole.auth.postgresql.enabled: true`)

### Branding

| config_key | Type | Default | Description |
|------------|------|---------|-------------|
| `branding.site_name` | string | Apache Guacamole | Application full name (displayed on login page and browser title) |
| `branding.site_name_short` | string | -- | Application short name |
| `branding.logo` | file | -- | Login page logo (light) |
| `branding.logo_dark` | file | -- | Login page logo (dark) |
| `branding.favicon` | file | -- | Browser tab icon |
| `branding.login_background` | file | -- | Login page background image |
| `branding.copyright` | string | -- | Page footer copyright text |
| `branding.support_url` | url | -- | Technical support link |
| `branding.help_url` | url | -- | Help documentation link |

### Theme

| config_key | Type | Default | Description |
|------------|------|---------|-------------|
| `theme.primary_color` | string | #1a56db | Brand primary color (#RRGGBB) |
| `theme.accent_color` | string | #0694a2 | Brand accent color |
| `theme.success_color` | string | #057a55 | Success color |
| `theme.warning_color` | string | #f59e0b | Warning color |
| `theme.danger_color` | string | #e02424 | Danger color |
| `theme.mode` | enum | light | Light mode: `light`; Dark mode: `dark`; Follow system: `auto` |

Theme colors are driven by CSS variables. The primary color derives 35 variables covering buttons, navigation, background, text, borders, shadows, and all visual elements.

### Security Policy — Integrated with Backend PasswordPolicy

| config_key | Type | Default | Description |
|------------|------|---------|-------------|
| `security.password_min_length` | integer | 8 | Minimum password length |
| `security.password_require_uppercase` | boolean | true | Require uppercase letter |
| `security.password_require_number` | boolean | true | Require number |
| `security.password_require_special` | boolean | false | Require special character |

The above 4 items are integrated with all three databases' (PostgreSQL/MySQL/SQL Server) `PasswordPolicy` implementation. Changes take effect **immediately** — the next time a user creates or modifies a password, the new rules are enforced. Rules:

- DB has value → Use DB value
- DB has no value → Fallback to legacy properties in `application.yml` like `postgresql-user-password-min-length`
- Legacy property also has no value → No restriction

### System Announcement

| config_key | Type | Default | Description |
|------------|------|---------|-------------|
| `announcement.message` | text | -- | Announcement content |
| `announcement.level` | enum | info | `info` (blue) / `warning` (orange) / `error` (red) |
| `announcement.enabled` | boolean | false | Whether to enable |
| `announcement.start_time` | datetime | -- | Effective start time (UTC, always valid if empty) |
| `announcement.end_time` | datetime | -- | Effective end time (UTC, always valid if empty) |
| `announcement.closable` | boolean | true | Whether to allow users to close the announcement |

The announcement displays as a single-line banner at the top of the page. When `closable=true`, users can close it, and the close state is saved in `localStorage` — it won't reappear on refresh. When the admin modifies the announcement content, it automatically reappears. `start_time`/`end_time` control the announcement to only display within the specified time window.

### File Storage

Branding assets like logos, favicons, and background images are managed through file upload components. Files are stored on the server filesystem, with metadata recorded in the `guacamole_system_file` table.

| Property | Default | Description |
|----------|---------|-------------|
| `guacamole.system.file-storage-path` | `${user.dir}/files` | File storage root directory, supports environment variables |

Supported formats: SVG, PNG, JPG, ICO, GIF. Maximum 2MB per file.

### Configuration Priority

```
Database guacamole_system_config table (runtime modification)
    ↓ Fallback when no value
application.yml guacamole.system.defaults section (build-time configuration)
    ↓ Fallback when no value
Guacamole native guacamole.properties
```

---

## Multi-Authentication Chain Configuration

Multiple authentication providers can be enabled simultaneously. Guacamole tries each provider in sequence:

```
Login request
  → Provider 1 (e.g., LDAP) → Success → User logged in
  → Provider 1 fails
  → Provider 2 (e.g., PostgreSQL) → Success → User logged in
  → Provider 2 fails
  → Provider 3 (e.g., JSON) → Success → User logged in
  → All fail → Login rejected
```

### Mutual Exclusion Rules

**Enforced at startup** (violation causes application startup failure):

| Mutual Exclusion Group | Rule |
|----------------------|------|
| JDBC backends | `mysql`, `postgresql`, `sqlserver` — at most one can be enabled |
| SSO providers | `sso-cas`, `sso-openid`, `sso-saml` — at most one can be enabled |

**Allowed Combination Examples:**

- JDBC + LDAP + TOTP — Allowed
- LDAP + JSON + QuickConnect — Allowed
- PostgreSQL + CAS — Allowed
- PostgreSQL + RADIUS — Allowed (RADIUS as external auth source, requires `auto-create-accounts: true` for automatic DB account creation)
- MySQL + PostgreSQL — **Rejected** (JDBC conflict)
- CAS + OpenID — **Rejected** (SSO conflict)

### External Authentication Sources and Auto Account Creation

External auth sources like RADIUS, LDAP, Header, and SSO only handle **password verification** — they don't manage user authorization. After authentication succeeds, the JDBC module's `getUserContext()` checks if the user exists in the database:

| `auto-create-accounts` | External Auth Succeeds | Result |
|-----------------------|--------------|--------|
| `false` (default) | User doesn't exist in DB | Login succeeds but no connection permissions (empty page) |
| `true` | User doesn't exist in DB | Automatically creates account in `guacamole_user` table, login successful |
| `true` | User already exists in DB | Uses existing account directly (existing permissions preserved) |

> **Note:** Auto-created accounts have no connection permissions. An administrator must manually assign permissions via the Guacamole management interface.

### Example: LDAP + PostgreSQL + TOTP

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ldap.example.com
      ldap-user-base-dn: ou=users,dc=example,dc=com
      ldap-username-attribute: uid
    postgresql:
      enabled: true
    totp:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

Effect:
1. LDAP users authenticate via LDAP first, then complete TOTP verification
2. Non-LDAP users fall back to PostgreSQL authentication
3. TOTP is enforced for all users after primary authentication

### Example: Header Authentication + JDBC

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: REMOTE_USER
    postgresql:
      enabled: true
      postgresql-auto-create-accounts: true

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

Users authenticated by the reverse proxy automatically get database accounts created via `auto-create-accounts: true`, with permissions managed in the database.

### Example: Pure SSO

```yaml
guacamole:
  auth:
    sso-openid:
      enabled: true
      openid-authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
      openid-jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
      openid-issuer: https://accounts.google.com
      openid-client-id: ${OPENID_CLIENT_ID}
      openid-redirect-uri: https://guacamole.example.com/
```

In pure SSO mode, connections and permissions need to be managed through a JDBC backend or through the SSO provider's built-in connection support.

---

## Environment Variable Overrides

The application supports Spring Boot convention-style environment variable overrides.

| Environment Variable | Mapped Property | Default |
|---------------------|----------------|---------|
| `SERVER_PORT` | `server.port` | `8080` |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | (from `application.yml`) |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | (from `application.yml`) |
| `GUACAMOLE_DB_PASSWORD` | `spring.datasource.password` | `guacamole` |
| `GUACAMOLE_HISTORY_RECORDING_PATH` | `guacamole.history.recording-search-path` | `/tmp/guacamole/recordings` |
| `OPENID_CLIENT_ID` | `guacamole.auth.sso-openid.openid-client-id` | -- |
| `DUO_IKEY` | `guacamole.auth.duo.duo-integration-key` | -- |
| `DUO_SKEY` | `guacamole.auth.duo.duo-secret-key` | -- |
| `DUO_AKEY` | `guacamole.auth.duo.duo-application-key` | -- |
| `RADIUS_SECRET` | `guacamole.auth.radius.radius-shared-secret` | -- |
| `GUACAMOLE_HOME` | Runtime environment variable | `~/.guacamole` |

Any Spring Boot property can be overridden using uppercase underscore notation (e.g., `GUACAMOLE_AUTH_LDAP_LDAP_HOSTNAME=ldap.example.com`, `GUACAMOLE_AUTH_LDAP_ENABLED=true`).

**Docker Environment:**

```bash
docker run -e GUACAMOLE_DB_PASSWORD=secret123 \
           -e SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/guacamole \
           -p 8080:8080 \
           guacamole:latest
```

---

## Common Configuration Scenarios

### Scenario 1: LDAP + PostgreSQL Combined Authentication

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ad.company.com
      ldap-port: 389
      ldap-username-attribute: sAMAccountName
      ldap-user-base-dn: ou=Users,dc=company,dc=com
      ldap-search-bind-dn: cn=guacadmin,cn=Users,dc=company,dc=com
      ldap-search-bind-password: ${LDAP_BIND_PASSWORD}
    postgresql:
      enabled: true
      auto-create-accounts: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

Effect: Users authenticate via AD, database auto-creates accounts, permissions managed in database.

### Scenario 2: Pure SSO + JDBC Backend

```yaml
guacamole:
  auth:
    sso-openid:
      enabled: true
      openid-authorization-endpoint: https://keycloak.company.com/realms/company/protocol/openid-connect/auth
      openid-jwks-endpoint: https://keycloak.company.com/realms/company/protocol/openid-connect/certs
      openid-issuer: https://keycloak.company.com/realms/company
      openid-client-id: ${OPENID_CLIENT_ID}
      openid-redirect-uri: https://guacamole.company.com/
    postgresql:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
```

Effect: Users log in via Keycloak SSO, database manages connection configuration and permissions.

### Scenario 3: Multi-Factor Authentication (LDAP + TOTP)

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ldap.company.com
      ldap-user-base-dn: ou=users,dc=company,dc=com
    totp:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
```

Effect: After LDAP password authentication, users must also enter a TOTP verification code.

### Scenario 4: API Token Authentication (JSON + JDBC)

```yaml
guacamole:
  auth:
    json:
      enabled: true
      json-secret-key: ${JSON_SECRET_KEY}
      json-trusted-networks: 10.0.0.0/8,172.16.0.0/12
    postgresql:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
```

Effect: Allows API token authentication from internal networks, while also supporting database user login.

### Scenario 5: Reverse Proxy SSO (Header + JDBC)

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: X-Forwarded-User
    postgresql:
      enabled: true
      auto-create-accounts: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
```

Effect: Nginx/Apache passes user header information after authentication, Guacamole auto-creates database accounts.

### Scenario 6: Docker Compose Full Deployment

```yaml
version: "3.8"
services:
  guacamole:
    image: guacamole:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/guacamole
      SPRING_DATASOURCE_USERNAME: guacamole
      GUACAMOLE_DB_PASSWORD: secret123
      GUACAMOLE_AUTH_LDAP_ENABLED: "true"
      GUACAMOLE_AUTH_LDAP_LDAP_HOSTNAME: ldap.company.com
      GUACAMOLE_AUTH_LDAP_LDAP_USER_BASE_DN: ou=users,dc=company,dc=com
      GUACAMOLE_AUTH_TOTP_ENABLED: "true"
    volumes:
      - recordings:/tmp/guacamole/recordings

  guacd:
    image: guacd:latest
    ports:
      - "4822:4822"

  db:
    image: postgres:15
    environment:
      POSTGRES_DB: guacamole
      POSTGRES_USER: guacamole
      POSTGRES_PASSWORD: secret123
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
  recordings:
```

This configuration deploys Guacamole + guacd + PostgreSQL with LDAP authentication and TOTP two-factor authentication enabled.
