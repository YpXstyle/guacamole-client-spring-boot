# Guacamole Spring Boot

**[中文](README_zh.md)** | **English**

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green.svg)](https://spring.io/projects/spring-boot)
[![Guacamole](https://img.shields.io/badge/Guacamole-1.5.5-orange.svg)](https://guacamole.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

**Apache Guacamole 1.5.5** — migrated from Google Guice to **Spring Boot 3.3.5 + Java 17**.

This project is a complete migration of the upstream [Apache Guacamole](https://github.com/apache/guacamole-client) web application, replacing the original Google Guice dependency injection framework with Spring Boot's auto-configuration and Starter pattern. All extension modules have been standardized as Spring Boot Starters while preserving the Jersey JAX-RS REST layer, WebSocket tunnel, and AngularJS frontend.

The Group ID has been changed from `org.apache.guacamole` to `com.right`. All public REST APIs remain fully compatible with the original project.

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Extensions](#extensions)
- [Quick Start](#quick-start)
- [Configuration Quick Reference](#configuration-quick-reference)
- [Migration Status](#migration-status)
- [Build and Deploy](#build-and-deploy)
- [Project Structure](#project-structure)
- [Known Issues](#known-issues)
- [Documentation](#documentation)
- [License](#license)

---

## Features

- **Spring Boot 3.3.5 + Java 17** — Production-grade framework with auto-configuration, Actuator monitoring, and simplified deployment
- **17 Extension Modules** — All converted to Spring Boot Starter artifacts, enabled/disabled via `@ConditionalOnProperty`
- **Full Protocol Support** — RDP, VNC, SSH, Telnet, Kubernetes (via guacd 1.5.5)
- **Session Recording** — Storage and playback of connection history
- **Connection Sharing** — Share active sessions with fine-grained permission control
- **Vault Integration** — Keeper Secrets Manager (KSM) credential injection
- **Docker Support** — Multi-stage Dockerfile + docker-compose (guacd + PostgreSQL)
- **Build-time JS/CSS Minification** — Google Closure Compiler (consistent with upstream behavior)
- **Build Identifier** — `${guacamole.build.identifier}` timestamp generation and injection into frontend resources, matching original Apache Guacamole build behavior

### Extension Artifacts

| Category | Artifact ID |
|----------|-------------|
| Authentication | `guacamole-auth-header-starter` |
| Authentication | `guacamole-auth-json-starter` |
| Authentication | `guacamole-auth-ldap-starter` |
| Authentication | `guacamole-auth-radius-starter` |
| Authentication | `guacamole-auth-totp-starter` |
| Authentication | `guacamole-auth-duo-starter` |
| Authentication | `guacamole-auth-mysql-starter` |
| Authentication | `guacamole-auth-postgresql-starter` |
| Authentication | `guacamole-auth-sqlserver-starter` |
| Authentication | `guacamole-auth-quickconnect-starter` |
| SSO | `guacamole-auth-sso-cas-starter` |
| SSO | `guacamole-auth-sso-openid-starter` |
| SSO | `guacamole-auth-sso-saml-starter` |
| Vault | `guacamole-vault-ksm-starter` |
| History | `guacamole-history-starter` |

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      Browser (AngularJS)                       │
│    WebSocket (guacamole protocol)  │  REST API (Jersey)       │
└──────────────────┬────────────────────┬───────────────────────┘
                   │                    │
┌──────────────────▼────────────────────▼───────────────────────┐
│                  Spring Boot Application (Fat JAR)             │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐    │
│  │                Jersey JAX-RS (/api/*)                  │    │
│  │     SessionResource   UserContextResource   ...        │    │
│  └───────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐    │
│  │             Extension System (Starter Pattern)         │    │
│  │                                                       │    │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────────┐   │    │
│  │  │Header│ │JSON  │ │JDBC  │ │LDAP  │ │SSO (CAS/ │   │    │
│  │  │Auth  │ │Auth  │ │Auth  │ │Auth  │ │OIDC/SAML)│   │    │
│  │  └──────┘ └──────┘ └──────┘ └──────┘ └──────────┘   │    │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────────┐   │    │
│  │  │TOTP  │ │RADIUS│ │DUO   │ │Quick │ │Vault KSM │   │    │
│  │  │      │ │      │ │      │ │Conn. │ │          │   │    │
│  │  └──────┘ └──────┘ └──────┘ └──────┘ └──────────┘   │    │
│  └───────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐    │
│  │                   Core Services                        │    │
│  │  Environment   TokenSessionMap   TunnelRequest         │    │
│  │  ExtensionLoader   LanguageService   ResourceServlet   │    │
│  └───────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐    │
│  │              Spring Boot Infrastructure                │    │
│  │  Tomcat (embedded)   Actuator   Config Management     │    │
│  └───────────────────────────────────────────────────────┘    │
└───────────────────────────┬───────────────────────────────────┘
                            │
┌───────────────────────────▼───────────────────────────────────┐
│                    guacd (1.5.5)                               │
│   RDP  │  VNC  │  SSH  │  Telnet  │  Kubernetes               │
└───────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Aspect | Original (Guice) | Spring Boot Migration |
|--------|-------------------|----------------------|
| DI Framework | Google Guice `@Inject` | Spring `@Autowired` |
| Module Loading | `AbstractModule.bind()` | `@AutoConfiguration` + `@Bean` |
| REST Framework | Jersey (unchanged) | Jersey (unchanged) |
| WebSocket | Guice-assisted JSR 356 | Tomcat lifecycle + JSR 356 |
| Configuration | `guacamole.properties` | `application.yml` |
| Extension Discovery | `ServiceLoader` + `GUACAMOLE_HOME/extensions/` | `ResourcePatternResolver` classpath scan |
| Property Resolution | `LocalEnvironment` + file | Spring `Environment` bridge |
| Packaging | WAR (deploy to Tomcat) | Spring Boot Fat JAR (embedded Tomcat) |
| Build Tool | Ant / Maven WAR | Spring Boot Maven Plugin |
| Group ID | `org.apache.guacamole` | `com.right` |

---

## Extensions

### Authentication Extensions

| Extension | Artifact | Description | Status |
|-----------|----------|-------------|--------|
| **Header Auth** | `guacamole-auth-header-starter` | Reverse proxy SSO: reads username from HTTP headers (e.g., `REMOTE_USER`) | ✅ Verified |
| **JSON Auth** | `guacamole-auth-json-starter` | Encrypted token authentication, no database required | ✅ Verified |
| **JDBC MySQL** | `guacamole-auth-mysql-starter` | MySQL database authentication and connection storage | ✅ Verified |
| **JDBC PostgreSQL** | `guacamole-auth-postgresql-starter` | PostgreSQL database authentication and connection storage | ✅ Verified |
| **JDBC SQL Server** | `guacamole-auth-sqlserver-starter` | SQL Server database authentication and connection storage | ✅ Verified |
| **LDAP** | `guacamole-auth-ldap-starter` | LDAP / Active Directory authentication | ✅ Verified |
| **RADIUS** | `guacamole-auth-radius-starter` | RADIUS authentication (PAP, CHAP, MSCHAPv1/v2, EAP-MD5, EAP-TLS) | ✅ Verified |
| **TOTP** | `guacamole-auth-totp-starter` | Time-based One-Time Password (Google Authenticator, Authy, etc.) | ✅ Verified |
| **DUO** | `guacamole-auth-duo-starter` | Duo Security two-factor authentication | ⚠️ Needs upgrade |
| **SSO CAS** | `guacamole-auth-sso-cas-starter` | CAS single sign-on | ✅ Verified |
| **SSO OpenID** | `guacamole-auth-sso-openid-starter` | OpenID Connect (Google, Okta, Keycloak, etc.) | ✅ Verified |
| **SSO SAML** | `guacamole-auth-sso-saml-starter` | SAML 2.0 single sign-on | ✅ Verified |

### Feature Extensions

| Extension | Artifact | Description | Status |
|-----------|----------|-------------|--------|
| **Quick Connect** | `guacamole-auth-quickconnect-starter` | Create ad-hoc connections via URI (e.g., `rdp://host:3389`) | ✅ Verified |
| **History** | `guacamole-history-starter` | Session recording storage and playback | ✅ Verified |
| **Vault KSM** | `guacamole-vault-ksm-starter` | Keeper Secrets Manager credential injection | ⏳ Pending testing |

### Mutual Exclusion Constraints

- **JDBC Database Mutual Exclusion** — MySQL, PostgreSQL, SQL Server can only enable one at a time
- **SSO Policy Mutual Exclusion** — CAS, OpenID Connect, SAML cannot be enabled simultaneously
- **DUO Requires SDK v4 Upgrade** — Current implementation uses Duo Web SDK v2, deprecated by Duo in March 2024 (see [Known Issues](#known-issues))

---

## Quick Start

For a detailed step-by-step guide, see [docs/QUICK-START_EN.md](docs/QUICK-START_EN.md). Below is a quick reference.

### Requirements

- **JDK 17** or higher
- **Maven 3.8+**
- **guacd** 1.5.5 running (Docker: `docker run -d -p 4822:4822 guacamole/guacd:1.5.5`)
- **PostgreSQL** (or MySQL / SQL Server) with Guacamole database initialized

### Build and Run from Source

```bash
# Clone and build
git clone <repository-url>
cd guacamole-spring-boot
mvn clean package -DskipTests

# Run
java -jar guacamole/target/guacamole-*.jar
```

### Run with Maven Plugin

```bash
mvn spring-boot:run -pl guacamole
```

### Run with Docker

```bash
docker-compose up -d
```

This starts three services:
- **guacd** — Guacamole proxy daemon (image: `guacamole/guacd:1.5.5`)
- **postgres** — PostgreSQL 16 (with initialized `guacamole` database)
- **guacamole** — Spring Boot application (built from local source)

### Default Access

| Item | Value |
|------|-------|
| URL | `http://localhost:8080/` |
| Default Admin | `guacadmin` / `guacadmin` (when JDBC auth enabled) |

### Initialize Database

When using JDBC authentication, create the database schema before first run:

**PostgreSQL:**
```bash
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/001-create-schema.sql
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/002-create-admin-user.sql
```

**MySQL:**
```bash
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/001-create-schema.sql
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/002-create-admin-user.sql
```

**SQL Server:**
```bash
sqlcmd -S localhost -U guacamole -P guacamole -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/001-create-schema.sql
sqlcmd -S localhost -U guacamole -P guacamole -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/002-create-admin-user.sql
```

---

## Configuration Quick Reference

Default configuration file is at `guacamole/src/main/resources/application.yml`. Full configuration reference: **[docs/CONFIGURATION_EN.md](docs/CONFIGURATION_EN.md)**.

### Minimal Configuration (PostgreSQL)

```yaml
server:
  port: 8080

guacamole:
  guacd:
    hostname: localhost
    port: 4822
  auth:
    postgresql:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD:guacamole}
    driver-class-name: org.postgresql.Driver
```

### Enabling Extensions

All extensions are disabled by default. Enable in `application.yml`:

```yaml
guacamole:
  auth:
    header:
      enabled: true       # Reverse proxy SSO
    json:
      enabled: true       # Encrypted token auth
    quickconnect:
      enabled: true       # URI quick connect
    postgresql:
      enabled: true       # Database auth (JDBC: pick one)
    mysql:
      enabled: false
    sqlserver:
      enabled: false
    ldap:
      enabled: true       # LDAP / AD auth
    totp:
      enabled: true       # TOTP two-factor auth
    radius:
      enabled: true       # RADIUS auth
    duo:
      enabled: false      # DUO (needs SDK v4 upgrade)
    sso-cas:
      enabled: false      # CAS SSO (mutually exclusive with other SSO)
    sso-openid:
      enabled: false      # OpenID Connect SSO
    sso-saml:
      enabled: false      # SAML 2.0 SSO
  vault:
    ksm:
      enabled: false      # Vault KSM
  history:
    enabled: true         # Session recording
```

Extensions load only when both conditions are met:
1. Maven dependency exists in `guacamole/pom.xml`
2. `enabled` property is set to `true`

### Property Mapping

The original `guacamole.properties` flat namespace has been reorganized into YAML hierarchy under `guacamole.*`. Examples:

| Original Property | application.yml Path |
|-------------------|---------------------|
| `guacd-hostname` | `guacamole.guacd.hostname` |
| `mysql-hostname` | `guacamole.auth.mysql.mysql-hostname` |
| `ldap-hostname` | `guacamole.auth.ldap.ldap-hostname` |
| `cas-authorization-endpoint` | `guacamole.auth.sso-cas.cas-authorization-endpoint` |
| `openid-issuer` | `guacamole.auth.sso-openid.openid-issuer` |
| `saml-idp-url` | `guacamole.auth.sso-saml.saml-idp-url` |
| `recording-search-path` | `guacamole.history.recording-search-path` |

Database connections use **Spring Boot `spring.datasource`** standard configuration, replacing the old `<db>-hostname` / `<db>-port` properties.

Full property mapping table: **[docs/MIGRATION_EN.md](docs/MIGRATION_EN.md)**.

---

## Migration Status

This project is a complete migration of **Apache Guacamole 1.5.5** (upstream: [apache/guacamole-client](https://github.com/apache/guacamole-client)) from the original Guice architecture to Spring Boot 3.3.5.

### Migrated Components

| Component | Status | Notes |
|-----------|--------|-------|
| `guacamole-common` (protocol lib, 47 files) | ✅ Complete | Java 17, Jakarta namespace |
| `guacamole-common-js` (JS API, 38 modules) | ✅ Complete | Unchanged, included via Maven reactor |
| `guacamole-ext` (extension API, 136 files) | ✅ Complete | AuthenticationProvider, etc. |
| `guacamole` (web application) | ✅ Complete | Spring Boot main app, Jersey, WebSocket |
| `guacamole-auth-jdbc` (MySQL) | ✅ Complete | Spring Boot Starter + MyBatis |
| `guacamole-auth-jdbc` (PostgreSQL) | ✅ Complete | Spring Boot Starter + MyBatis |
| `guacamole-auth-jdbc` (SQL Server) | ✅ Complete | Spring Boot Starter + MyBatis |
| `guacamole-auth-header-starter` | ✅ Complete | `@ConditionalOnProperty` enablement |
| `guacamole-auth-json-starter` | ✅ Complete | Encrypted token auth |
| `guacamole-auth-ldap-starter` | ✅ Complete | Apache Directory LDAP API |
| `guacamole-auth-radius-starter` | ✅ Complete | JRadius library |
| `guacamole-auth-totp-starter` | ✅ Complete | ZXing QR code generation |
| `guacamole-auth-quickconnect-starter` | ✅ Complete | URI quick connect |
| `guacamole-auth-sso-cas-starter` | ✅ Complete | CAS client |
| `guacamole-auth-sso-openid-starter` | ✅ Complete | jose4j JWT library |
| `guacamole-auth-sso-saml-starter` | ✅ Complete | OneLogin java-saml toolkit |
| `guacamole-history-starter` | ✅ Complete | Session recording |
| `guacamole-vault-ksm-starter` | ✅ Code complete | Awaiting KSM account for testing |

### What Changed

| Aspect | Upstream Original | This Project |
|--------|------------------|--------------|
| Build System | Ant + Maven WAR | Spring Boot Maven Plugin |
| DI Framework | Google Guice | Spring IoC / `@Autowired` |
| Packaging | WAR (deploy to Servlet container) | Fat JAR (embedded Tomcat) |
| Configuration | `guacamole.properties` file | `application.yml` |
| Extension Loading | `GUACAMOLE_HOME/extensions/*.jar` | Maven dependency + `@ConditionalOnProperty` |
| Extension Packaging | Plain JAR | Spring Boot Starter |
| Group ID | `org.apache.guacamole` | `com.right` |
| Java Version | 8 / 11 | 17 |
| Servlet API | javax (Tomcat 8/9) | jakarta (Tomcat 10 / Spring Boot 3.x) |
| JDBC Framework | Manual DataSource + JDBC | MyBatis Spring Boot Starter |

### What Was Preserved

- **All REST API endpoints** — Unchanged, fully compatible with existing clients
- **WebSocket tunnel protocol** — Unchanged
- **AngularJS 1.8 frontend** — Preserved from upstream, built via webpack
- **Database schema** — Same SQL scripts, no data migration needed
- **Extension API** — `AuthenticationProvider` interface and `guac-manifest.json` structure
- **Build-time JS/CSS minification** — Google Closure Compiler (same as upstream)
- **License** — Apache 2.0 (all original copyright notices preserved)

---

## Build and Deploy

### Build Commands

```bash
# Full build (all modules)
mvn clean package -DskipTests

# Build web application only
mvn clean package -pl guacamole -am -DskipTests

# Build and run tests
mvn clean package

# Skip frontend build
mvn clean package -Dskip.frontend

# Force npm install (when package.json changes)
mvn clean package -Dforce.npm.install
```

The build automatically skips `npm install` when `node_modules` exists (via `skip-npm-install` profile). Use `-Dforce.npm.install` to override after dependency changes.

### Frontend Development

```bash
cd guacamole/frontend
npm ci
npm run build       # Production build (webpack + Google Closure Compiler)
npm run dev         # Development watch mode
```

The frontend is an AngularJS 1.8 application built with webpack 4. Build output is at `guacamole/src/main/resources/static/`. `GuacamoleSpringBootApplication` serves it as static resources.

### Docker

```bash
# Build and start all services (guacd + PostgreSQL + Guacamole)
docker-compose up -d

# Build Guacamole image only
docker build -t guacamole-spring-boot .

# Override environment variables
GUACAMOLE_DB_PASSWORD=secret123 docker-compose up -d
```

The Dockerfile uses multi-stage builds:
1. **Build stage** — `maven:3.9-eclipse-temurin-17-alpine` compiles and packages the application
2. **Runtime stage** — `eclipse-temurin:17-jre-alpine` runs the Fat JAR as non-root user (`guacamole`)

docker-compose.yml orchestrates guacd (port 4822), PostgreSQL 16 (with health checks), and the Guacamole application (port 8080) as three services. Database password defaults to `guacamole` and can be overridden via `GUACAMOLE_DB_PASSWORD` environment variable.

### Build Identifier

A `${guacamole.build.identifier}` timestamp (`yyyyMMddHHmmss` format) is generated at build time and injected into:
- `static/index.html` — Cache-busting parameter
- `static/verifyCachedVersion.js` — Version verification

This behavior is consistent with the original Apache Guacamole build. The `maven-resources-plugin` is configured to use `${}` delimiters (restored from Spring Boot's default `@` delimiter).

---

## Project Structure

```
guacamole-spring-boot/
├── pom.xml                                      # Root POM (Spring Boot parent 3.3.5)
├── Dockerfile                                   # Multi-stage Docker build
├── docker-compose.yml                           # guacd + PostgreSQL + Guacamole
│
├── guacamole-common/                            # Guacamole protocol library (Java, 47 files)
├── guacamole-common-js/                         # Guacamole JavaScript API (npm package, 38 modules)
├── guacamole-ext/                               # Extension API (AuthenticationProvider, etc., 136 files)
│
├── guacamole/                                   # Main web application (Spring Boot)
│   ├── pom.xml                                  # All extension dependencies declared here
│   ├── frontend/                                # AngularJS 1.8 frontend (webpack)
│   │   ├── package.json
│   │   ├── webpack.config.js
│   │   ├── plugins/                             # Custom webpack plugins
│   │   └── src/                                 # Frontend source (JS, CSS, templates)
│   └── src/main/
│       ├── java/org/apache/guacamole/
│       │   ├── GuacamoleSpringBootApplication.java   # @SpringBootApplication entry point
│       │   ├── config/                                # Spring @Configuration classes
│       │   ├── extension/                        # Extension loading and manifest parsing
│       │   ├── resource/                         # ResourceServlet serving extension resources
│       │   ├── rest/                             # Jersey REST resource classes
│       │   └── tunnel/                           # WebSocket tunnel endpoint
│       └── resources/
│           ├── application.yml                   # Default configuration
│           ├── logback-spring.xml                # Logging configuration
│           └── static/                           # Frontend build output (auto-generated)
│
├── extensions/
│   ├── guacamole-auth-jdbc/                      # JDBC authentication (multi-module)
│   │   ├── guacamole-auth-jdbc-base/             # Shared JDBC code + MyBatis mappers
│   │   ├── guacamole-auth-mysql-starter/         # MySQL Starter
│   │   ├── guacamole-auth-postgresql-starter/    # PostgreSQL Starter
│   │   ├── guacamole-auth-sqlserver-starter/     # SQL Server Starter
│   │   └── pom.xml
│   │
│   ├── guacamole-auth-sso/                       # SSO authentication (multi-module)
│   │   ├── guacamole-auth-sso-base/              # Shared SSO code
│   │   ├── guacamole-auth-sso-cas-starter/       # CAS Starter
│   │   ├── guacamole-auth-sso-openid-starter/    # OpenID Connect Starter
│   │   ├── guacamole-auth-sso-saml-starter/      # SAML 2.0 Starter
│   │   └── pom.xml
│   │
│   ├── guacamole-vault/                          # Vault credential management (multi-module)
│   │   ├── guacamole-vault-base/                 # Shared Vault abstraction
│   │   ├── guacamole-vault-ksm-starter/          # Keeper Secrets Manager Starter
│   │   └── pom.xml
│   │
│   ├── guacamole-auth-header-starter/            # HTTP header authentication
│   ├── guacamole-auth-json-starter/              # Encrypted JSON token authentication
│   ├── guacamole-auth-ldap-starter/              # LDAP / Active Directory authentication
│   ├── guacamole-auth-radius-starter/            # RADIUS authentication
│   ├── guacamole-auth-totp-starter/              # Time-based One-Time Password
│   ├── guacamole-auth-duo-starter/               # Duo Security two-factor authentication
│   ├── guacamole-auth-quickconnect-starter/      # URI quick connect
│   └── guacamole-history-starter/                # Session recording storage
│
└── docs/                                         # Documentation
    ├── QUICK-START_EN.md                         # Quick start guide (English)
    ├── QUICK-START.md                            # 快速入门指南 (中文)
    ├── ARCHITECTURE_EN.md                        # Architecture analysis (English)
    ├── ARCHITECTURE.md                           # 深入架构分析 (中文)
    ├── BUILD_EN.md                               # Build, Docker, deployment (English)
    ├── BUILD.md                                  # 构建、Docker、部署指南 (中文)
    ├── CONFIGURATION_EN.md                       # Configuration reference (English)
    ├── CONFIGURATION.md                          # 完整扩展配置参考 (中文)
    ├── EXTENSIONS_EN.md                          # Extension development guide (English)
    ├── EXTENSIONS.md                             # 扩展系统与开发指南 (中文)
    ├── MIGRATION_EN.md                           # Migration reference (English)
    ├── MIGRATION.md                              # 迁移参考 (中文)
    ├── REST-API_EN.md                            # REST API reference (English)
    ├── REST-API.md                               # REST API 参考 (中文)
    ├── vault-module_EN.md                        # Vault/KSM module docs (English)
    └── vault-module.md                           # Vault/KSM 模块文档 (中文)
```

### Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.3.5 | Application framework |
| Java | 17 | Runtime |
| Jersey | 3.1.x | JAX-RS REST layer |
| MyBatis Spring Boot | 3.0.3 | JDBC database access |
| Guava | 32.1.3-jre | Utility library |
| Jackson | 2.17.2 | JSON serialization |
| ZXing | 3.5.3 | QR code generation (TOTP) |
| Apache Directory LDAP API | 2.1.6 | LDAP authentication |
| JRadius | 1.1.5 | RADIUS authentication |
| jose4j | 0.9.6 | JWT / JOSE (OpenID) |
| OneLogin java-saml | 2.9.0 | SAML 2.0 toolkit |
| CAS Client | 3.6.4 | CAS SSO |
| Keeper KSM Core | 16.6.3 | Keeper Secrets Manager |
| Kotlin | 1.9.23 | Required by KSM SDK |
| KotlinX Serialization | 1.6.3 | Required by KSM SDK |
| Bouncy Castle FIPS | 1.0.2.4 | Cryptography library |

---

## Known Issues

### DUO SDK v2 Deprecated

The DUO extension currently uses **Duo Web SDK v2** (iframe-based), which was **deprecated by Duo in March 2024**. The extension starts without errors, but Duo servers will block authentication.

Migration to **Duo Web SDK v4** is required:
- **Frontend**: iframe integration → redirect integration
- **Backend**: HMAC SHA-256 → HMAC SHA-512
- **Configuration**: `duo-application-key` removed; `ikey`/`skey` renamed to `client_id`/`client_secret`
- **Reference**: https://duo.com/docs/duoweb

### Vault KSM Pending Testing

The `guacamole-vault-ksm-starter` module is **code complete** and compiles successfully, but has not yet been end-to-end tested with a real Keeper Secrets Manager account. Testing is blocked by lack of a KSM subscription. The module uses:
- Keeper Security Secrets Manager Core SDK 16.6.3
- Kotlin 1.9.23 (required by KSM SDK)
- KotlinX Serialization 1.6.3
- Bouncy Castle FIPS 1.0.2.4

### Other Notes

- **Requires Java 17** — This project targets Java 17; frontend webpack build uses `--openssl-legacy-provider`
- **Frontend build requires Node.js 18** — `frontend-maven-plugin` automatically installs Node.js 18.18.0 and npm 9.8.1
- **Jetty not supported** — The application relies on embedded Tomcat's WebSocket support; switching to Jetty requires WebSocket configuration changes

---

## Documentation

| Document | Description |
|----------|-------------|
| [QUICK-START_EN.md](docs/QUICK-START_EN.md) | Quick start: environment setup, installation, first login (**start here for new users**) |
| [CONFIGURATION_EN.md](docs/CONFIGURATION_EN.md) | Complete configuration reference for all extensions |
| [BUILD_EN.md](docs/BUILD_EN.md) | Build, Docker, and deployment guide |
| [EXTENSIONS_EN.md](docs/EXTENSIONS_EN.md) | Extension system architecture and development guide |
| [ARCHITECTURE_EN.md](docs/ARCHITECTURE_EN.md) | In-depth system architecture analysis |
| [MIGRATION_EN.md](docs/MIGRATION_EN.md) | Guice to Spring Boot migration reference with property mapping tables |
| [REST-API_EN.md](docs/REST-API_EN.md) | REST API endpoint reference |
| [vault-module_EN.md](docs/vault-module_EN.md) | Vault / KSM module detailed documentation |

**中文文档：** 以上每份英文文档均有对应的中文版本（去掉 `_EN` 后缀即可），如 [QUICK-START.md](docs/QUICK-START.md)、[CONFIGURATION.md](docs/CONFIGURATION.md) 等。

---

## License

This project is a derivative work of [Apache Guacamole 1.5.5](https://guacamole.apache.org/), released under the **Apache License 2.0**.

### Source Code Licensing

- All source files inherited from Apache Guacamole retain their original Apache 2.0 license headers
- New Spring Boot adaptation code also uses Apache 2.0 licensing
- Full license text: see [LICENSE](LICENSE) in the project root
- Third-party attribution: see [NOTICE](NOTICE)

### Trademark Notice

Apache Guacamole, Apache, and the Apache feather logo are registered trademarks of the [Apache Software Foundation](https://www.apache.org/). This project is not affiliated with, endorsed by, or sponsored by the Apache Software Foundation.

### Third-Party Components

This project uses multiple third-party open source components, each subject to its own license terms. See [LICENSE](LICENSE) for details.

> **Note:** JRadius (used by the RADIUS authentication module) is licensed under LGPL 2.1. This library is an optional dependency, used only when RADIUS authentication is enabled. If distributing binaries that include JRadius, be aware of the LGPL 2.1 and Apache 2.0 compatibility requirements.
