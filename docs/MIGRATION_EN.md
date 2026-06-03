# Migration Guide: Apache Guacamole 1.5.5 to Spring Boot 3.3.5

[← Back to Documentation](../README.md#documentation)

This document records all changes required for migrating from the original Apache Guacamole 1.5.5 (Google Guice + WAR + guacamole.properties) to the Spring Boot 3.3.5 version (`com.right` groupId).

All property mappings below have been verified against the actual Java source code (ConfigurationService and GuacamoleProperties classes) in this repository.

---

## Table of Contents

- [What Was Migrated](#what-was-migrated)
- [Migration Status](#migration-status)
- [Complete Property Mapping Table](#complete-property-mapping-table)
  - [Core guacd](#core-guacd)
  - [JDBC PostgreSQL](#jdbc-postgresql)
  - [JDBC MySQL](#jdbc-mysql)
  - [JDBC SQL Server](#jdbc-sql-server)
  - [LDAP](#ldap)
  - [RADIUS](#radius)
  - [TOTP](#totp)
  - [DUO](#duo)
  - [Header HTTP Authentication](#header-http-authentication)
  - [JSON](#json)
  - [QuickConnect](#quickconnect)
  - [SSO / CAS](#sso--cas)
  - [SSO / OpenID Connect](#sso--openid-connect)
  - [SSO / SAML 2.0](#sso--saml-20)
  - [Vault / KSM](#vault--ksm)
  - [History Recording](#history-recording)
- [Extension Loading Changes](#extension-loading-changes)
- [Database Migration](#database-migration)
- [Deployment Changes](#deployment-changes)
- [API Compatibility](#api-compatibility)
- [Breaking Changes](#breaking-changes)
- [Custom Extension Migration](#custom-extension-migration)

---

## What Was Migrated

| Aspect | Original (1.5.5) | After Migration |
|--------|-----------------|----------------|
| Base Framework | Apache Guacamole 1.5.5 | Spring Boot 3.3.5 |
| Java Version | Java 8 / 11 | JDK 17 |
| Dependency Injection | Google Guice (`@Inject`) | Spring IoC (`@Autowired`) |
| Servlet API | `javax.servlet.*` | `jakarta.servlet.*` (Jakarta EE 9+) |
| Configuration File | `guacamole.properties` | `application.yml` |
| Deployment Artifact | WAR (deploy to Tomcat/Jetty) | Spring Boot Fat JAR (embedded Tomcat) |
| Extension Loading | `GUACAMOLE_HOME/extensions/` directory JARs | Maven dependencies on classpath |
| Maven Group ID | `org.apache.guacamole` | `com.right` |
| Build System | Maven (original parent POM) | Spring Boot parent POM (`spring-boot-starter-parent`) |

### Key Technical Upgrades

- **Jakarta EE**: All `javax.*` imports replaced with `jakarta.*` (JAX-RS 3.1.0, Servlet 6.0, Annotation 2.1)
- **Guava**: Upgraded to 32.1.3-jre
- **Jackson**: Upgraded to 2.17.2
- **MyBatis**: Upgraded to MyBatis Spring Boot Starter 3.0.3
- **Jose4j**: 0.9.6 for JWT handling
- **OneLogin SAML**: 2.9.0
- **CAS Client**: 3.6.4
- **Keeper KSM SDK**: 16.6.3 with Kotlin 1.9.23 support
- **Bouncy Castle FIPS**: 1.0.2.4

---

## Migration Status

| Module | Status | Notes |
|--------|--------|-------|
| guacamole-common | Complete | Core protocol library, no migration issues |
| guacamole-common-js | Complete | JavaScript client, built via frontend-maven-plugin |
| guacamole-ext | Complete | Extension API ported to Spring-friendly SPI |
| guacamole (webapp) | Complete | Main Spring Boot application, embedded Tomcat |
| guacamole-auth-jdbc-base | Complete | Shared JDBC authentication infrastructure |
| guacamole-auth-mysql-starter | Complete | MySQL JDBC authentication |
| guacamole-auth-postgresql-starter | Complete | PostgreSQL JDBC authentication |
| guacamole-auth-sqlserver-starter | Complete | SQL Server JDBC authentication |
| guacamole-auth-header-starter | Complete | HTTP request header authentication |
| guacamole-auth-json-starter | Complete | JSON/API token authentication |
| guacamole-auth-ldap-starter | Complete | LDAP/Active Directory authentication |
| guacamole-auth-totp-starter | Complete | TOTP multi-factor authentication |
| guacamole-auth-radius-starter | Complete | RADIUS multi-factor authentication |
| guacamole-auth-quickconnect-starter | Complete | QuickConnect feature |
| guacamole-auth-duo-starter | Needs Update | Uses deprecated Duo SDK v2, needs v4 SDK upgrade |
| guacamole-auth-sso-cas-starter | Complete | CAS single sign-on |
| guacamole-auth-sso-openid-starter | Complete | OpenID Connect single sign-on |
| guacamole-auth-sso-saml-starter | Complete | SAML 2.0 single sign-on |
| guacamole-vault-ksm-starter | Needs Testing | Keeper Secrets Manager integration, needs KSM account for testing |
| guacamole-history-starter | Complete | Connection history and recording search |

### Status Meanings

- **Complete**: Feature-complete, compilation verified
- **Needs Update**: Compiles but has issues. DUO uses deprecated Duo SDK v2 (`DuoWeb`), needs upgrade to Duo SDK v4 (Universal Prompt)
- **Needs Testing**: Code complete and compiles, end-to-end testing requires external credentials (KSM account) not yet available

---

## Complete Property Mapping Table

Each property below has been verified against the corresponding `*GuacamoleProperties.java` and `*Environment.java` source files. Properties marked as **Required** will cause startup errors if missing.

Unrecognized properties in `guacamole.properties` are silently ignored. The migrated `application.yml` uses scoped namespaces, so there is no risk of key name conflicts between modules.

### Core guacd

**Source files:** `guacamole-ext/.../environment/Environment.java` and `LocalEnvironment.java`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `guacd-hostname` | `guacamole.guacd.hostname` | `localhost` | No |
| `guacd-port` | `guacamole.guacd.port` | `4822` | No |
| `guacd-ssl` | `guacamole.guacd.ssl` | `false` | No |

```yaml
guacamole:
  guacd:
    hostname: localhost
    port: 4822
    ssl: false
```

---

### JDBC / PostgreSQL

**Source files:** `guacamole-auth-postgresql-starter/.../conf/PostgreSQLGuacamoleProperties.java`, `PostgreSQLEnvironment.java`

**Prefix:** `guacamole.auth.postgresql.`

#### Active Properties (still used in business logic)

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `postgresql-user-required` | `guacamole.auth.postgresql.user-required` | `false` | No |
| `postgresql-absolute-max-connections` | `guacamole.auth.postgresql.absolute-max-connections` | `0` | No |
| `postgresql-default-max-connections` | `guacamole.auth.postgresql.default-max-connections` | `0` | No |
| `postgresql-default-max-group-connections` | `guacamole.auth.postgresql.default-max-group-connections` | `0` | No |
| `postgresql-default-max-connections-per-user` | `guacamole.auth.postgresql.default-max-connections-per-user` | `0` | No |
| `postgresql-default-max-group-connections-per-user` | `guacamole.auth.postgresql.default-max-group-connections-per-user` | `1` | No |
| `postgresql-batch-size` | `guacamole.auth.postgresql.batch-size` | `5000` | No |
| `postgresql-auto-create-accounts` | `guacamole.auth.postgresql.auto-create-accounts` | `false` | No |

#### Zombie Properties (no longer used for database connections)

| guacamole.properties | Status | Migration Path |
|---------------------|--------|---------------|
| `postgresql-hostname` | Zombie | Use `spring.datasource.url` |
| `postgresql-port` | Zombie | Use `spring.datasource.url` |
| `postgresql-database` | Zombie | Use `spring.datasource.url` |
| `postgresql-username` | Zombie | Use `spring.datasource.username` |
| `postgresql-password` | Zombie | Use `spring.datasource.password` |
| `postgresql-ssl-mode` | Zombie | Use `spring.datasource.url` parameters |
| `postgresql-ssl-cert-file` | Zombie | Use `spring.datasource.url` parameters |
| `postgresql-ssl-key-file` | Zombie | Use `spring.datasource.url` parameters |
| `postgresql-ssl-root-cert-file` | Zombie | Use `spring.datasource.url` parameters |
| `postgresql-ssl-key-password` | Zombie | Use `spring.datasource.url` parameters |
| `postgresql-default-statement-timeout` | Zombie | Use DataSource configuration |
| `postgresql-socket-timeout` | Zombie | Use DataSource configuration |

```yaml
guacamole:
  auth:
    postgresql:
      enabled: true
      user-required: false
      batch-size: 5000
      auto-create-accounts: false

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${PG_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

---

### JDBC / MySQL

**Source files:** `guacamole-auth-mysql-starter/.../conf/MySQLGuacamoleProperties.java`, `MySQLEnvironment.java`

**Prefix:** `guacamole.auth.mysql.`

#### Active Properties (still used in business logic)

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `mysql-user-required` | `guacamole.auth.mysql.user-required` | `false` | No |
| `mysql-absolute-max-connections` | `guacamole.auth.mysql.absolute-max-connections` | `0` | No |
| `mysql-default-max-connections` | `guacamole.auth.mysql.default-max-connections` | `0` | No |
| `mysql-default-max-group-connections` | `guacamole.auth.mysql.default-max-group-connections` | `0` | No |
| `mysql-default-max-connections-per-user` | `guacamole.auth.mysql.default-max-connections-per-user` | `0` | No |
| `mysql-default-max-group-connections-per-user` | `guacamole.auth.mysql.default-max-group-connections-per-user` | `1` | No |
| `mysql-batch-size` | `guacamole.auth.mysql.batch-size` | `1000` | No |
| `mysql-auto-create-accounts` | `guacamole.auth.mysql.auto-create-accounts` | `false` | No |

#### Zombie Properties (no longer used for database connections)

| guacamole.properties | Status | Migration Path |
|---------------------|--------|---------------|
| `mysql-hostname` | Zombie | Use `spring.datasource.url` |
| `mysql-port` | Zombie | Use `spring.datasource.url` |
| `mysql-database` | Zombie | Use `spring.datasource.url` |
| `mysql-username` | Zombie | Use `spring.datasource.username` |
| `mysql-password` | Zombie | Use `spring.datasource.password` |
| `mysql-driver` | Zombie | Use `spring.datasource.driver-class-name` |
| `mysql-ssl-mode` | Zombie | Use `spring.datasource.url` parameters |
| `mysql-ssl-trust-store` | Zombie | Use `spring.datasource.url` parameters |
| `mysql-ssl-trust-password` | Zombie | Use `spring.datasource.url` parameters |
| `mysql-ssl-client-store` | Zombie | Use `spring.datasource.url` parameters |
| `mysql-ssl-client-password` | Zombie | Use `spring.datasource.url` parameters |
| `mysql-server-timezone` | Zombie | Use `spring.datasource.url` parameters |

```yaml
guacamole:
  auth:
    mysql:
      enabled: true
      user-required: false
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

### JDBC / SQL Server

**Source files:** `guacamole-auth-sqlserver-starter/.../conf/SQLServerGuacamoleProperties.java`, `SQLServerEnvironment.java`

**Prefix:** `guacamole.auth.sqlserver.`

#### Active Properties (still used in business logic)

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `sqlserver-user-required` | `guacamole.auth.sqlserver.user-required` | `false` | No |
| `sqlserver-absolute-max-connections` | `guacamole.auth.sqlserver.absolute-max-connections` | `0` | No |
| `sqlserver-default-max-connections` | `guacamole.auth.sqlserver.default-max-connections` | `0` | No |
| `sqlserver-default-max-group-connections` | `guacamole.auth.sqlserver.default-max-group-connections` | `0` | No |
| `sqlserver-default-max-connections-per-user` | `guacamole.auth.sqlserver.default-max-connections-per-user` | `0` | No |
| `sqlserver-default-max-group-connections-per-user` | `guacamole.auth.sqlserver.default-max-group-connections-per-user` | `1` | No |
| `sqlserver-batch-size` | `guacamole.auth.sqlserver.batch-size` | `500` | No |
| `sqlserver-auto-create-accounts` | `guacamole.auth.sqlserver.auto-create-accounts` | `false` | No |

#### Zombie Properties (no longer used for database connections)

| guacamole.properties | Status | Migration Path |
|---------------------|--------|---------------|
| `sqlserver-hostname` | Zombie | Use `spring.datasource.url` |
| `sqlserver-port` | Zombie | Use `spring.datasource.url` |
| `sqlserver-database` | Zombie | Use `spring.datasource.url` |
| `sqlserver-username` | Zombie | Use `spring.datasource.username` |
| `sqlserver-password` | Zombie | Use `spring.datasource.password` |
| `sqlserver-driver` | Zombie | Use `spring.datasource.driver-class-name` |
| `sqlserver-instance` | Zombie | Use `spring.datasource.url` parameters |

```yaml
guacamole:
  auth:
    sqlserver:
      enabled: true
      user-required: false
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

### LDAP

**Source files:** `guacamole-auth-ldap-starter/.../conf/LDAPGuacamoleProperties.java`, `EnvironmentLDAPConfiguration.java`, `DefaultLDAPConfiguration.java`

**Prefix:** `guacamole.auth.ldap.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `ldap-hostname` | `guacamole.auth.ldap.ldap-hostname` | `localhost` | No |
| `ldap-port` | `guacamole.auth.ldap.ldap-port` | Depends on encryption | No |
| `ldap-encryption-method` | `guacamole.auth.ldap.ldap-encryption-method` | `none` | No |
| `ldap-user-base-dn` | `guacamole.auth.ldap.ldap-user-base-dn` | -- | **Yes** |
| `ldap-username-attribute` | `guacamole.auth.ldap.ldap-username-attribute` | `uid` | No |
| `ldap-search-bind-dn` | `guacamole.auth.ldap.ldap-search-bind-dn` | -- | No |
| `ldap-search-bind-password` | `guacamole.auth.ldap.ldap-search-bind-password` | -- | No |
| `ldap-user-search-filter` | `guacamole.auth.ldap.ldap-user-search-filter` | `(objectClass=*)` | No |
| `ldap-config-base-dn` | `guacamole.auth.ldap.ldap-config-base-dn` | -- | No |
| `ldap-group-base-dn` | `guacamole.auth.ldap.ldap-group-base-dn` | -- | No |
| `ldap-group-name-attribute` | `guacamole.auth.ldap.ldap-group-name-attribute` | `cn` | No |
| `ldap-group-search-filter` | `guacamole.auth.ldap.ldap-group-search-filter` | `(objectClass=*)` | No |
| `ldap-member-attribute` | `guacamole.auth.ldap.ldap-member-attribute` | `member` | No |
| `ldap-member-attribute-type` | `guacamole.auth.ldap.ldap-member-attribute-type` | `dn` | No |
| `ldap-max-search-results` | `guacamole.auth.ldap.ldap-max-search-results` | `1000` | No |
| `ldap-operation-timeout` | `guacamole.auth.ldap.ldap-operation-timeout` | `30` (seconds) | No |
| `ldap-network-timeout` | `guacamole.auth.ldap.ldap-network-timeout` | `30000` (ms) | No |
| `ldap-follow-referrals` | `guacamole.auth.ldap.ldap-follow-referrals` | `false` | No |
| `ldap-max-referral-hops` | `guacamole.auth.ldap.ldap-max-referral-hops` | `5` | No |
| `ldap-dereference-aliases` | `guacamole.auth.ldap.ldap-dereference-aliases` | `never` | No |
| `ldap-user-attributes` | `guacamole.auth.ldap.ldap-user-attributes` | (empty) | No |

**Encryption method and default ports:** `none` defaults to port `389`, `ssl` (LDAPS) defaults to `636`, `starttls` defaults to `389`.

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ldap.example.com
      ldap-port: 389
      ldap-encryption-method: starttls
      ldap-user-base-dn: dc=example,dc=com
      ldap-username-attribute: sAMAccountName
      ldap-search-bind-dn: cn=admin,dc=example,dc=com
      ldap-search-bind-password: ${LDAP_BIND_PASSWORD}
      ldap-user-search-filter: (objectClass=person)
      ldap-group-base-dn: ou=Groups,dc=example,dc=com
      ldap-member-attribute: member
```

---

### RADIUS

**Source files:** `guacamole-auth-radius-starter/.../conf/RadiusGuacamoleProperties.java`, `ConfigurationService.java`

**Prefix:** `guacamole.auth.radius.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `radius-hostname` | `guacamole.auth.radius.radius-hostname` | `localhost` | No |
| `radius-auth-port` | `guacamole.auth.radius.radius-auth-port` | `1812` | No |
| `radius-acct-port` | `guacamole.auth.radius.radius-acct-port` | `1813` | No |
| `radius-shared-secret` | `guacamole.auth.radius.radius-shared-secret` | -- | **Yes** |
| `radius-auth-protocol` | `guacamole.auth.radius.radius-auth-protocol` | -- | **Yes** |
| `radius-max-retries` | `guacamole.auth.radius.radius-max-retries` | `5` | No |
| `radius-timeout` | `guacamole.auth.radius.radius-timeout` | `60` (seconds) | No |
| `radius-nas-ip` | `guacamole.auth.radius.radius-nas-ip` | (auto-detect) | No |
| `radius-trust-all` | `guacamole.auth.radius.radius-trust-all` | `false` | No |
| `radius-ca-file` | `guacamole.auth.radius.radius-ca-file` | `GUACAMOLE_HOME/ca.crt` | No |
| `radius-ca-type` | `guacamole.auth.radius.radius-ca-type` | `pem` | No |
| `radius-ca-password` | `guacamole.auth.radius.radius-ca-password` | -- | No |
| `radius-key-file` | `guacamole.auth.radius.radius-key-file` | `GUACAMOLE_HOME/radius.key` | No |
| `radius-key-type` | `guacamole.auth.radius.radius-key-type` | `pem` | No |
| `radius-key-password` | `guacamole.auth.radius.radius-key-password` | -- | No |
| `radius-eap-ttls-inner-protocol` | `guacamole.auth.radius.radius-eap-ttls-inner-protocol` | -- | EAP-TTLS only |

```yaml
guacamole:
  auth:
    radius:
      enabled: true
      radius-hostname: radius.example.com
      radius-auth-port: 1812
      radius-shared-secret: ${RADIUS_SECRET}
      radius-auth-protocol: pap
      radius-timeout: 30
      radius-nas-ip: 192.168.1.100
```

---

### TOTP

**Source files:** `guacamole-auth-totp-starter/.../conf/ConfigurationService.java`

**Prefix:** `guacamole.auth.totp.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `totp-issuer` | `guacamole.auth.totp.totp-issuer` | `Apache Guacamole` | No |
| `totp-digits` | `guacamole.auth.totp.totp-digits` | `6` | No |
| `totp-period` | `guacamole.auth.totp.totp-period` | `30` (seconds) | No |
| `totp-mode` | `guacamole.auth.totp.totp-mode` | `sha1` | No |

`totp-mode` accepts: `sha1`, `sha256`, `sha512`.
`totp-digits` must be between 6 and 8 (inclusive).

```yaml
guacamole:
  auth:
    totp:
      enabled: true
      totp-issuer: My Company
      totp-period: 30
      totp-mode: sha256
      totp-digits: 6
```

---

### DUO

**Source files:** `guacamole-auth-duo-starter/.../conf/ConfigurationService.java`

**Prefix:** `guacamole.auth.duo.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `duo-api-hostname` | `guacamole.auth.duo.duo-api-hostname` | -- | **Yes** |
| `duo-integration-key` | `guacamole.auth.duo.duo-integration-key` | -- | **Yes** |
| `duo-secret-key` | `guacamole.auth.duo.duo-secret-key` | -- | **Yes** |
| `duo-application-key` | `guacamole.auth.duo.duo-application-key` | -- | **Yes** |

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

**Warning:** The migrated DUO module uses the deprecated Duo SDK v2 (`DuoWeb` class), which Duo Security has retired. This module will stop working when Duo shuts down the v2 API endpoints. Production environments need to upgrade to Duo SDK v4 (Universal Prompt).

---

### Header (HTTP Authentication)

**Source files:** `guacamole-auth-header-starter/.../conf/HTTPHeaderGuacamoleProperties.java`, `ConfigurationService.java`

**Prefix:** `guacamole.auth.header.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `http-auth-header` | `guacamole.auth.header.http-auth-header` | `REMOTE_USER` | No |

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: REMOTE_USER
```

---

### JSON

**Source files:** `guacamole-auth-json-starter/.../conf/ConfigurationService.java`

**Prefix:** `guacamole.auth.json.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `json-secret-key` | `guacamole.auth.json.json-secret-key` | -- | **Yes** |
| `json-trusted-networks` | `guacamole.auth.json.json-trusted-networks` | (all addresses) | No |

```yaml
guacamole:
  auth:
    json:
      enabled: true
      json-secret-key: ${JSON_SECRET_KEY_BASE64}
      json-trusted-networks: 10.0.0.0/8,172.16.0.0/12
```

---

### QuickConnect

**Source files:** `guacamole-auth-quickconnect-starter/.../conf/ConfigurationService.java`

**Prefix:** `guacamole.auth.quickconnect.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `quickconnect-allowed-parameters` | `guacamole.auth.quickconnect.quickconnect-allowed-parameters` | (all allowed) | No |
| `quickconnect-denied-parameters` | `guacamole.auth.quickconnect.quickconnect-denied-parameters` | (none denied) | No |

```yaml
guacamole:
  auth:
    quickconnect:
      enabled: true
      quickconnect-allowed-parameters: hostname,port,protocol
      quickconnect-denied-parameters: password,private-key
```

---

### SSO / CAS

**Source files:** `guacamole-auth-sso-cas-starter/.../conf/CASGuacamoleProperties.java`, `ConfigurationService.java`

**Prefix:** `guacamole.auth.sso-cas.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `cas-authorization-endpoint` | `guacamole.auth.sso-cas.cas-authorization-endpoint` | -- | **Yes** |
| `cas-redirect-uri` | `guacamole.auth.sso-cas.cas-redirect-uri` | -- | **Yes** |
| `cas-clearpass-key` | `guacamole.auth.sso-cas.cas-clearpass-key` | -- | No |
| `cas-group-attribute` | `guacamole.auth.sso-cas.cas-group-attribute` | -- | No |
| `cas-group-format` | `guacamole.auth.sso-cas.cas-group-format` | `plain` | No |
| `cas-group-ldap-base-dn` | `guacamole.auth.sso-cas.cas-group-ldap-base-dn` | -- | No |
| `cas-group-ldap-attribute` | `guacamole.auth.sso-cas.cas-group-ldap-attribute` | -- | No |

```yaml
guacamole:
  auth:
    sso-cas:
      enabled: true
      cas-authorization-endpoint: https://cas.example.org/cas
      cas-redirect-uri: https://guacamole.example.com/
```

---

### SSO / OpenID Connect

**Source files:** `guacamole-auth-sso-openid-starter/.../conf/ConfigurationService.java`

**Prefix:** `guacamole.auth.sso-openid.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `openid-authorization-endpoint` | `guacamole.auth.sso-openid.openid-authorization-endpoint` | -- | **Yes** |
| `openid-jwks-endpoint` | `guacamole.auth.sso-openid.openid-jwks-endpoint` | -- | **Yes** |
| `openid-issuer` | `guacamole.auth.sso-openid.openid-issuer` | -- | **Yes** |
| `openid-client-id` | `guacamole.auth.sso-openid.openid-client-id` | -- | **Yes** |
| `openid-redirect-uri` | `guacamole.auth.sso-openid.openid-redirect-uri` | -- | **Yes** |
| `openid-scope` | `guacamole.auth.sso-openid.openid-scope` | `openid email profile` | No |
| `openid-username-claim-type` | `guacamole.auth.sso-openid.openid-username-claim-type` | `email` | No |
| `openid-groups-claim-type` | `guacamole.auth.sso-openid.openid-groups-claim-type` | `groups` | No |
| `openid-allowed-clock-skew` | `guacamole.auth.sso-openid.openid-allowed-clock-skew` | `30` (seconds) | No |
| `openid-max-token-validity` | `guacamole.auth.sso-openid.openid-max-token-validity` | `300` (minutes) | No |
| `openid-max-nonce-validity` | `guacamole.auth.sso-openid.openid-max-nonce-validity` | `10` (minutes) | No |

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

---

### SSO / SAML 2.0

**Source files:** `guacamole-auth-sso-saml-starter/.../conf/ConfigurationService.java`

**Prefix:** `guacamole.auth.sso-saml.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `saml-idp-metadata-url` | `guacamole.auth.sso-saml.saml-idp-metadata-url` | -- | Conditionally required |
| `saml-idp-url` | `guacamole.auth.sso-saml.saml-idp-url` | -- | Conditionally required |
| `saml-entity-id` | `guacamole.auth.sso-saml.saml-entity-id` | -- | Conditionally required |
| `saml-callback-url` | `guacamole.auth.sso-saml.saml-callback-url` | -- | **Yes** |
| `saml-strict` | `guacamole.auth.sso-saml.saml-strict` | `true` | No |
| `saml-debug` | `guacamole.auth.sso-saml.saml-debug` | `false` | No |
| `saml-compress-request` | `guacamole.auth.sso-saml.saml-compress-request` | `true` | No |
| `saml-compress-response` | `guacamole.auth.sso-saml.saml-compress-response` | `true` | No |
| `saml-group-attribute` | `guacamole.auth.sso-saml.saml-group-attribute` | `groups` | No |
| `saml-auth-timeout` | `guacamole.auth.sso-saml.saml-auth-timeout` | `5` (minutes) | No |

```yaml
guacamole:
  auth:
    sso-saml:
      enabled: true
      saml-callback-url: https://guacamole.example.com/
      saml-idp-url: https://idp.example.com/sso
      saml-entity-id: https://guacamole.example.com/
      saml-strict: true
```

---

### Vault / KSM

**Source files:** `guacamole-vault-ksm-starter/.../conf/KsmConfigurationService.java`

**Prefix:** `guacamole.vault.ksm.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `ksm-config` | `guacamole.vault.ksm.ksm-config` | -- | **Yes** |
| `ksm-allow-unverified-cert` | `guacamole.vault.ksm.ksm-allow-unverified-cert` | `false` | No |

KSM module also reads `GUACAMOLE_HOME/ksm-token-mapping.yml` and `GUACAMOLE_HOME/guacamole.properties.ksm`.

```yaml
guacamole:
  vault:
    ksm:
      enabled: true
      ksm-config: ${KSM_CONFIG_BASE64}
      ksm-allow-unverified-cert: false
```

---

### History (Recording)

**Source files:** `guacamole-history-starter/.../HistoryAutoConfiguration.java`

**Prefix:** `guacamole.history.`

| guacamole.properties | application.yml Path | Default | Required |
|---------------------|---------------------|---------|----------|
| `recording-search-path` | `guacamole.history.recording-search-path` | `/var/lib/guacamole/recordings` | No |

```yaml
guacamole:
  history:
    enabled: true
    recording-search-path: ${GUACAMOLE_HISTORY_RECORDING_PATH:/tmp/guacamole/recordings}
```

---

## Extension Loading Changes

### Original (Apache Guacamole 1.5.5)

1. Each extension compiled as a standalone `.jar` file
2. Place `.jar` in `GUACAMOLE_HOME/extensions/`
3. Each `.jar` contains `guac-manifest.json` declaring identity
4. Guacamole scans `GUACAMOLE_HOME/extensions/` at startup via `GuacamoleExtensionLoader`
5. Dependency injection managed by Google Guice modules (`AbstractModule`, `FactoryModuleBuilder`)
6. Extension JARs activate automatically upon presence

### After Migration (Spring Boot 3.3.5)

1. Extensions are standard Spring Boot Starter modules with auto-configuration
2. Add extension Maven dependency in `guacamole/pom.xml`
3. Each starter provides `spring.factories` or `AutoConfiguration.imports` entry for Spring Boot auto-discovery
4. `@ConditionalOnProperty(prefix = "...", name = "enabled", havingValue = "true")` controls activation
5. **All extensions disabled by default** — must explicitly set `enabled: true` in `application.yml`
6. Extensions packaged inside Fat JAR; no external directory scanning
7. Spring scans `classpath*:guac-manifest.json` for frontend resources

### Comparison

| Operation | Original | After Migration |
|-----------|---------|----------------|
| Install extension | Copy JAR to `GUACAMOLE_HOME/extensions/` | Add Maven dependency in `guacamole/pom.xml` |
| Enable extension | Automatic (JAR presence = enabled) | Dependency exists + `enabled: true` in YAML |
| Disable extension | Delete JAR file | Set `enabled: false` or remove Maven dependency |
| Configure extension | `guacamole.properties` | Under module namespace in `application.yml` |

---

## Database Migration

### Schema Compatibility

Database Schema is **100% unchanged**. All `schema/` SQL scripts from the original project are preserved as-is:

```
extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/
  +-- 001-create-schema.sql
  +-- 002-create-admin-user.sql
  +-- 003-create-system-config.sql  ← New: System Config module (optional)
  +-- upgrade/

extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/
  +-- 001-create-schema.sql
  +-- 002-create-admin-user.sql
  +-- 003-create-system-config.sql  ← New: System Config module (optional)
  +-- upgrade/

extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/
  +-- 001-create-schema.sql
  +-- 002-create-admin-user.sql
  +-- 003-create-system-config.sql  ← New: System Config module (optional)
  +-- upgrade/
```

### System Configuration Module (Optional Upgrade)

The system configuration module is a new feature that does not affect the existing database. Enable as needed.

**Upgrade Steps:**

```bash
# 1. Backup database
pg_dump guacamole > backup_before_system_config.sql   # PostgreSQL
mysqldump guacamole > backup_before_system_config.sql  # MySQL

# 2. Execute DDL (creates 2 new tables + pre-populates 25 config entries)
psql -d guacamole -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/003-create-system-config.sql

# 3. Start application (must also enable JDBC extension)
mvn spring-boot:run -pl guacamole
# Or
java -jar guacamole/target/guacamole-*.jar
```

**New Tables Created:**

| Table | Row Count | Description |
|-------|-----------|-------------|
| `guacamole_system_config` | 25 | Branding/theme/security/announcement config (key-value) |
| `guacamole_system_file` | 0 | Uploaded file metadata (Logo, Favicon, etc.) |

**Not enabling system configuration module:** Don't execute `003-create-system-config.sql`; the application runs normally, and branding/theme use `application.yml` defaults.

### Data Compatibility

All tables, columns, constraints, and indexes are identical to those created by Apache Guacamole 1.5.5. The migrated application can connect directly to your existing database without data migration or schema changes.

### Schema Version Detection

The database schema version must match the application's expected version (`guacamole.version` = `1.5.5`). If upgrading the database from an earlier Guacamole version, run the appropriate `upgrade/` scripts before starting the migrated application.

---

## Deployment Changes

| Aspect | Original (WAR) | After Migration (Fat JAR) |
|--------|---------------|--------------------------|
| Packaging | `guacamole.war` (deploy to `webapps/`) | `guacamole-{version}.jar` (standalone) |
| Servlet Container | External Tomcat / Jetty 8/9/10 | Embedded Tomcat (Spring Boot) |
| Startup | Container startup, deploy WAR | `java -jar guacamole-*.jar` |
| Config Location | `GUACAMOLE_HOME/guacamole.properties` | classpath `application.yml` (or `--spring.config.location`) |
| Extension Location | `GUACAMOLE_HOME/extensions/*.jar` | Inside Fat JAR (classpath) |
| Environment Vars | `GUACAMOLE_HOME` | Spring Boot standard env vars |
| HTTP Port | Tomcat's `server.xml` | `server.port: 8080` in `application.yml` |
| Logging | Container logs / `GUACAMOLE_HOME/logs/` | `logging.*` in `application.yml` (Spring Boot / Logback) |

### Quick Start

```bash
# Build the entire project
mvn clean package -DskipTests

# Run Fat JAR
java -jar guacamole/target/guacamole-1.5.5.jar

# Override config location
java -jar guacamole/target/guacamole-1.5.5.jar \
  --spring.config.location=/etc/guacamole/application.yml

# Override individual properties
java -jar guacamole/target/guacamole-1.5.5.jar \
  --server.port=8443 \
  --guacamole.guacd.hostname=guacd.example.com
```

### GUACAMOLE_HOME in the Migrated Version

`GUACAMOLE_HOME` is still used by `Environment` implementations for certain runtime lookups (e.g., `ldap-servers.yml`, `ksm-token-mapping.yml`, SSL certificate files, `guacamole.properties.ksm`). However, all major configuration has moved to `application.yml`. GUACAMOLE_HOME directory is now only used for supplementary files that are traditionally placed there.

---

## API Compatibility

All REST API endpoints and WebSocket paths are **identical** to Apache Guacamole 1.5.5. There are no changes to the public HTTP API surface.

### REST Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/tokens` | POST | Create authentication token |
| `/api/tokens/{token}` | DELETE | Invalidate token (logout) |
| `/api/session/data/{dataSource}/connections` | GET | List connections |
| `/api/session/data/{dataSource}/connections/{id}` | GET | Get connection details |
| `/api/session/data/{dataSource}/connections/{id}` | PUT | Update connection |
| `/api/session/data/{dataSource}/connections/{id}` | DELETE | Delete connection |
| `/api/session/data/{dataSource}/connections/{id}/parameters` | GET | Get connection parameters |
| `/api/session/data/{dataSource}/users` | GET/POST | List/create users |
| `/api/session/data/{dataSource}/users/{username}` | GET/PUT/DELETE | User CRUD |
| `/api/session/data/{dataSource}/userGroups` | GET/POST | Group management |
| `/api/session/data/{dataSource}/activeConnections` | GET | Active connections |
| `/api/session/data/{dataSource}/connectionGroups` | GET | Connection groups |
| `/api/session/tunnels` | GET/POST | Tunnel management |
| `/api/patches` | GET | Frontend patches (extension injection) |

### WebSocket

| Path | Protocol | Purpose |
|------|----------|---------|
| `/websocket-tunnel` | Guacamole protocol over WebSocket | Primary tunnel transport |

### Static Resources

All frontend resource paths are preserved.

### SSO Callback Endpoints

| SSO Type | Callback Path |
|----------|--------------|
| CAS | `/api/ext/cas/callback` |
| OpenID Connect | `/api/ext/openid/callback` |
| SAML | `/api/ext/saml/callback` |

---

## Breaking Changes

### 1. DUO SDK v2 Deprecated

The DUO authentication module still uses the deprecated Duo SDK v2 (`DuoWeb` class). Duo has announced end-of-life for this SDK. This module will stop working when Duo shuts down its v2 API. **Must upgrade to Duo SDK v4 (Universal Prompt)** before using DUO in production.

### 2. WebSocket Container: Tomcat Only, Jetty Removed

The original Apache Guacamole supported multiple WebSocket container adapters: Jetty 8, Jetty 9, and Tomcat (`tunnel/websocket/jetty8/`, `jetty9/`, `tomcat/`). All three adapters have been removed in the migrated version, replaced by a single JSR 356 standard endpoint (`GuacamoleWebSocketEndpoint.java`) running on Spring Boot's embedded Tomcat container.

> **Important distinction:** This project still uses **Jersey** (JAX-RS implementation for REST API, via `spring-boot-starter-jersey`). Jersey and Jetty are **two completely different components**: Jersey is a REST framework, Jetty is a Servlet container. What was removed is Jetty (container); Jersey (REST framework) is unaffected.

If you previously ran the original Guacamole on the Jetty container, you need to switch to Spring Boot's embedded Tomcat.

### 3. Maven Group ID Changed

```
Old: org.apache.guacamole
New: com.right
```

All internal Maven artifact coordinates have changed. If your custom extension depends on `org.apache.guacamole:guacamole-ext`, you must update the dependency to `com.right:guacamole-ext`.

### 4. Java Package Names Preserved

Despite the groupId change, all Java package names remain under `org.apache.guacamole.*`. Only Maven coordinates changed.

### 5. Extension Auto-Discovery Changed

In the original project, placing a JAR in `GUACAMOLE_HOME/extensions/` was sufficient. Extensions were passive — they only activated when corresponding entries existed in `guacamole.properties`.

In the migrated version:
- Extensions must be Maven dependencies (packaged inside Fat JAR)
- Extensions are **disabled by default** and require explicit `enabled: true` in YAML
- Multiple JDBC or SSO modules cannot be enabled simultaneously (validated at startup)

### 6. javax.* to jakarta.* Rename

All `javax.servlet.*`, `javax.ws.rs.*`, `javax.annotation.*`, `javax.inject.*`, and `javax.xml.bind.*` imports have been migrated to `jakarta.*`. Any custom extension code needs the same migration.

### 7. Google Guice Removed

All `@Inject` annotations have been replaced with Spring `@Autowired`. Guice `AbstractModule` classes have been replaced with Spring `@Configuration` classes. Guice `FactoryModuleBuilder`-generated factories have been replaced with direct Spring bean wiring.

### 8. Build Identifier Behavior

The `${guacamole.build.identifier}` property is still used in `index.html` and `verifyCachedVersion.js`, generating a timestamp-based build identifier identical to the original project. Maven resource plugin delimiters have been restored from `@` (Spring Boot default) to `${}` to maintain this behavior.

---

## Custom Extension Migration

If you maintain a custom Guacamole authentication extension, here is a checklist for migrating to the Spring Boot version.

### Step 1: Update Dependencies

```xml
<!-- Old -->
<dependency>
    <groupId>org.apache.guacamole</groupId>
    <artifactId>guacamole-ext</artifactId>
    <version>1.5.5</version>
</dependency>

<!-- New -->
<dependency>
    <groupId>com.right</groupId>
    <artifactId>guacamole-ext</artifactId>
    <version>1.5.5</version>
</dependency>
```

### Step 2: Replace Imports

| Old | New |
|-----|-----|
| `javax.inject.Inject` | `org.springframework.beans.factory.annotation.Autowired` |
| `javax.inject.Singleton` | `org.springframework.stereotype.Component` |
| `javax.servlet.*` | `jakarta.servlet.*` |
| `javax.ws.rs.*` | `jakarta.ws.rs.*` |
| `javax.xml.bind.*` | `jakarta.xml.bind.*` |

### Step 3: Replace Dependency Injection Configuration

```java
// Old (Guice)
public class MyModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AuthenticationProvider.class)
            .to(MyAuthProvider.class);
    }
}

// New (Spring)
@Configuration
public class MyAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "guacamole.auth.my", name = "enabled",
                           havingValue = "true")
    public MyAuthProvider myAuthProvider() {
        return new MyAuthProvider();
    }
}
```

### Step 4: Register Auto-Configuration

Create `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.example.MyAutoConfiguration
```

### Step 5: Update guac-manifest.json

Add the `configProperty` field linking YAML namespace to extension's property definition:

```json
{
    "guacamole-version": "1.5.0",
    "namespace": "guacamole-auth-my",
    "configProperty": "guacamole.auth.my.enabled"
}
```

### Step 6: Define YAML Properties

If your extension reads from `Environment`, properties will automatically resolve from `application.yml` under the module's namespace using the original property name. For example, a property named `my-secret-key` in `guacamole.properties` will read from `guacamole.auth.my.my-secret-key` in YAML.

See [EXTENSIONS_EN.md](EXTENSIONS_EN.md) for details.

---

## References

- [CONFIGURATION_EN.md](CONFIGURATION_EN.md) — Complete configuration reference
- [EXTENSIONS_EN.md](EXTENSIONS_EN.md) — Extension development guide
- [ARCHITECTURE_EN.md](ARCHITECTURE_EN.md) — System architecture
