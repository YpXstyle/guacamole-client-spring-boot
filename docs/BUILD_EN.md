# Build and Deployment Guide

[← Back to Documentation](../README.md#documentation)

Complete guide for building, running, and deploying the Guacamole Spring Boot application.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Building from Source](#building-from-source)
- [Build Stages in Detail](#build-stages-in-detail)
- [Build Identifier in Detail](#build-identifier-in-detail)
- [Frontend Development](#frontend-development)
- [Running the Application](#running-the-application)
- [Docker Deployment](#docker-deployment)
- [Database Initialization](#database-initialization)
- [Maven Build System](#maven-build-system)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Component | Version | Purpose |
|-----------|---------|---------|
| JDK | 17+ | Java compilation and runtime |
| Maven | 3.8+ | Build system |
| Node.js | 18.18.0 | Frontend build (auto-installed by `frontend-maven-plugin`) |
| npm | 9.8.1 | Frontend dependency management (auto-installed by `frontend-maven-plugin`) |
| guacd | 1.5.5 | Guacamole proxy daemon (handles remote desktop connections) |
| PostgreSQL | 14+ | Database (optional; MySQL 8+ and SQL Server 2019+ also supported) |

JDK 17 is mandatory. Node.js and npm do not need to be installed manually — `frontend-maven-plugin` 1.12.1 automatically downloads the specified versions during the build process.

---

## Building from Source

### Full Build

```bash
cd guacamole-spring-boot
mvn clean package -DskipTests
```

Build artifacts are located at `guacamole/target/guacamole-*.jar`.

### Build by Module

```bash
# Build only the web application and its dependencies
mvn clean package -pl guacamole -am -DskipTests

# Build all extension modules
mvn clean package -pl extensions -am -DskipTests

# Build a single extension (e.g., LDAP starter)
mvn clean package -pl extensions/guacamole-auth-ldap-starter -am -DskipTests
```

`-am` (also-make) ensures that all dependencies required by the specified module are also built.

### Build and Run Tests

```bash
mvn clean package
```

Tests are primarily in the `guacamole` module. Extension modules use Spring Boot Test for dependency injection integration tests.

### Quick Start (Development Mode)

Skip tests and frontend build to quickly get a runnable JAR:

```bash
mvn clean package -DskipTests -Dskip.frontend
```

### Proxy Settings

If building behind a proxy, Maven needs proxy configuration to automatically download Node.js:

```bash
mvn clean package -DskipTests -Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080
```

### Skipping Frontend Build

```bash
# Skip entire frontend (webpack won't run either)
mvn clean package -DskipTests -Dskip.frontend

# Force re-install npm dependencies (after package.json changes)
mvn clean package -DskipTests -Dforce.npm.install
```

---

## Build Stages in Detail

The complete Maven build consists of the following stages:

### Stage 1: generate-resources (Frontend Build)

**Goal:** Generate frontend static resources.

1. `frontend-maven-plugin` runs `install-node-and-npm` — automatically downloads Node.js 18.18.0 and npm 9.8.1 to `target/node/`
2. `frontend-maven-plugin` runs `npm install` — installs dependencies from `guacamole/frontend/package.json`
3. `frontend-maven-plugin` runs `npm run build` — runs the webpack build

**Auto-skip mechanism:** If the `frontend/node_modules` directory already exists and the Maven property `force.npm.install` is not set, the `skip-npm-install` profile automatically activates, skipping `npm install` (the slowest step). The webpack build always runs.

### Stage 2: process-resources (Resource Processing)

**Goal:** Filter and copy resource files.

1. **Maven resource filtering** (`maven-resources-plugin`) — replaces `${guacamole.build.identifier}` with a timestamp in specific files only
2. **Copy guacamole-common-js** — copies JS files from `guacamole-common-js` to `static/guacamole-common-js/`
3. **Copy core translation files** — copies `frontend/src/translations/*.json` to `target/classes/translations/`
4. **flatten-maven-plugin** — resolves `${revision}` version variable into the published POM

### Stage 3: compile (Java Compilation)

**Goal:** Compile all Java source code.

- JDK 17 compilation with `-Xlint:all` compiler flags
- All modules compile in parallel (Maven default behavior)

### Stage 4: prepare-package (Extension Resource Minification)

**Goal:** Minify extension module JS and CSS files.

- Only runs on extension modules
- Uses `minify-maven-plugin` (Google Closure Compiler)
- Outputs `.min.js` and `.min.css` files to `target/classes/`

**Note:** The main application JS is already minified by TerserPlugin in Stage 1's webpack build and is not re-minified at this stage.

### Stage 5: package (Packaging)

**Goal:** Generate the executable Fat JAR.

- `spring-boot-maven-plugin` runs the `repackage` goal
- Packages all dependencies (including extensions) into a single JAR
- Sets `org.apache.guacamole.GuacamoleSpringBootApplication` as the main class
- Excludes `lombok` dependency (compile-time only)

---

## Build Identifier in Detail

### Timestamp Injection

The build identifier is a timestamp injected into frontend files via Maven resource filtering:

```xml
<maven.build.timestamp.format>yyyyMMddHHmmss</maven.build.timestamp.format>
<guacamole.build.identifier>${maven.build.timestamp}</guacamole.build.identifier>
```

For example, a build on May 31, 2026 at 14:30:00 produces the identifier `20260531143000`.

This enables automatic browser cache invalidation: each build produces a unique identifier. `verifyCachedVersion.js` compares this identifier at runtime and forces a full page reload if a new version deployment is detected.

### Selective Filtering

Resource filtering must be selective to prevent corrupting binary files (images, fonts):

| File | Filtered | Purpose |
|------|----------|---------|
| `static/index.html` | Yes | `${guacamole.build.identifier}` replacement |
| `static/verifyCachedVersion.js` | Yes | `${guacamole.build.identifier}` replacement |
| `**/application*.yml` | Yes | Spring `@...@` placeholders |
| `**/application*.yaml` | Yes | Spring `@...@` placeholders |
| `**/application*.properties` | Yes | Spring `@...@` placeholders |
| All other resources (JSON, JS, CSS, images, fonts) | No | Pass through unaffected |

Implementation: `guacamole/pom.xml` uses two `<resource>` declarations — the first enables filtering and includes target files, the second disables filtering and excludes them.

### Delimiter Configuration (useDefaultDelimiters)

The root POM overrides Spring Boot's default `@` delimiter by enabling `useDefaultDelimiters`:

```xml
<plugin>
    <artifactId>maven-resources-plugin</artifactId>
    <configuration>
        <useDefaultDelimiters>true</useDefaultDelimiters>
    </configuration>
</plugin>
```

This allows `${guacamole.build.identifier}` (native Maven syntax) and `@...@` (Spring Boot placeholders for `application.yml`) to **coexist** in the same project. Without this setting, Maven only recognizes the `@...@` syntax overridden by the Spring Boot parent POM.

---

## Frontend Development

### Technology Stack

- **Framework:** AngularJS 1.8
- **Build Tool:** webpack 4
- **JS Minification:** TerserPlugin (main app) / Closure Compiler (extensions)
- **CSS Minification:** CssMinimizerPlugin

### Local Frontend Development

```bash
cd guacamole/frontend

# Install dependencies
npm install

# Production build (outputs to ../src/main/resources/static/)
npm run build

# Development mode (watches for file changes, auto-rebuilds)
npm run dev
```

### Frontend Build Pipeline (11 Steps)

1. **webpack** bundles `frontend/src/app/` JS/CSS into `guacamole.[contenthash].js` and `guacamole.[contenthash].css`
2. **AngularTemplateCacheWebpackPlugin** bundles HTML templates into `templates.js`
3. **TerserPlugin** minifies JavaScript (ES5 compatible output, excludes `templates.js`, enables parallel execution)
4. **CssMinimizerPlugin** minifies CSS
5. **CopyPlugin** copies static assets (app files, fonts, images, layouts, `verifyCachedVersion.js`)
6. **CopyPlugin** copies core libraries from `node_modules/` (Angular, jQuery, Lodash, Blob polyfill, datalist polyfill)
7. **CleanWebpackPlugin** cleans the `static/` output directory (preserves `guacamole-common-js/`)
8. **HtmlWebpackPlugin** generates `index.html` with auto-injected content-hash URLs
9. **MiniCssExtractPlugin** extracts CSS into separate files
10. **DependencyListPlugin** (custom) lists bundled node modules for license generation
11. **NormalizeTemplatePaths** (custom inline plugin) normalizes backslash paths in `$templateCache.put()` keys to forward slashes (cross-platform compatibility)

### Maven Integration

`frontend-maven-plugin` automates the entire process — no manual Node.js installation needed.

**npm install behavior:** The Maven plugin runs `npm install` (not `npm ci`). A profile named `skip-npm-install` automatically activates when:
- The `frontend/node_modules` directory already exists
- The `force.npm.install` property is not set

This profile sets `npm.install.skip` to `true`, skipping the `npm install` step, while the webpack build runs every time.

### Dual Minifier Mechanism

Since the main application and extensions use different build systems, their JavaScript minification approaches differ:

| Component | Minifier | Engine | Stage |
|-----------|----------|--------|-------|
| **Main Application** (webapp) | TerserPlugin (`terser-webpack-plugin`) | Terser (ES6+) | webpack build time (`generate-resources`) |
| **Extension Modules** | `minify-maven-plugin` | Google Closure Compiler | Maven package time (`prepare-package`) |

The `minify-maven-plugin` configuration is centralized in the root POM's `<pluginManagement>` and independently used by each extension module's pom.xml. Source files are in `src/main/resources/`, minified output goes to `target/classes/`, and manifests should reference `.min.*` files.

---

## Running the Application

### Run Fat JAR Directly

```bash
java -jar guacamole/target/guacamole-*.jar
```

### Maven Spring Boot Plugin

```bash
mvn spring-boot:run -pl guacamole
```

This method doesn't require building the JAR in advance during development. Modified Java source files are automatically recompiled (incremental).

### Using Custom Configuration Files

```bash
java -jar guacamole/target/guacamole-*.jar --spring.config.location=/path/to/application.yml
```

### Command-Line Property Overrides

```bash
java -jar guacamole/target/guacamole-*.jar \
  --server.port=9090 \
  --spring.datasource.password=secret \
  --guacamole.guacd.hostname=guacd.example.com \
  --guacamole.guacd.port=4822
```

### Setting Timezone

```bash
java -Duser.timezone=Asia/Shanghai -jar guacamole/target/guacamole-*.jar
```

The application reads the `user.timezone` system property at startup and calls `TimeZone.setDefault()`. If not set, the JVM default (usually the system timezone) is used.

### Enabling Debug Logging

```bash
java -jar guacamole/target/guacamole-*.jar --logging.level.org.apache.guacamole=DEBUG
```

---

## Docker Deployment

### Quick Start

The project root contains `Dockerfile` and `docker-compose.yml` for one-click startup of the complete environment:

```bash
docker-compose up -d
```

This command starts three services:
- **guacd** (`guacamole/guacd:1.5.5`) — port 4822
- **PostgreSQL** (`postgres:16-alpine`) — internal access, automatic health checks
- **Guacamole** (built from local Dockerfile) — port 8080

### Environment Variables

```bash
# Set custom database password
GUACAMOLE_DB_PASSWORD=securepass123 docker-compose up -d

# Custom timezone
JAVA_TOOL_OPTIONS="-Duser.timezone=America/New_York" docker-compose up -d

# Custom database connection
SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db.example.com:5432/guacamole \
  docker-compose up -d
```

### Building Docker Image

```bash
docker build -t guacamole-spring-boot:latest .
```

### Docker Multi-Stage Build

The Dockerfile uses multi-stage builds to optimize image size:

**Stage 1 (`build`):**
- Base image: `maven:3.9-eclipse-temurin-17-alpine`
- Copies all `pom.xml` files first, runs `mvn dependency:go-offline` to pre-fetch dependencies (leveraging Docker cache layers)
- Then copies all source code and runs the full build

**Stage 2 (`runtime`):**
- Base image: `eclipse-temurin:17-jre-alpine` (JRE only, smaller size)
- Copies build artifacts `*.jar` from the build stage
- Creates non-root user `guacamole` for security
- Exposes port 8080
- Entry point: `java -jar app.jar`

### Production-Grade Docker Compose

```yaml
version: '3.8'
services:
  guacd:
    image: guacamole/guacd:1.5.5
    restart: unless-stopped
    ports:
      - "127.0.0.1:4822:4822"    # Bind to loopback only (secure)

  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: guacamole
      POSTGRES_USER: guacamole
      POSTGRES_PASSWORD: ${GUACAMOLE_DB_PASSWORD:-guacamole}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U guacamole"]
      interval: 5s
      timeout: 5s
      retries: 5

  guacamole:
    build: .
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      GUACAMOLE_DB_PASSWORD: ${GUACAMOLE_DB_PASSWORD:-guacamole}
      GUACAMOLE_HISTORY_RECORDING_PATH: ${GUACAMOLE_HISTORY_RECORDING_PATH:-/tmp/guacamole/recordings}
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:-jdbc:postgresql://postgres:5432/guacamole}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME:-guacamole}
      JAVA_TOOL_OPTIONS: ${JAVA_TIMEZONE:--Duser.timezone=Asia/Shanghai}
    depends_on:
      guacd:
        condition: service_started
      postgres:
        condition: service_healthy

volumes:
  postgres-data:
```

---

## Database Initialization

### PostgreSQL

```bash
# Create database and user
sudo -u postgres psql <<EOF
CREATE USER guacamole WITH PASSWORD 'securepass';
CREATE DATABASE guacamole OWNER guacamole;
GRANT ALL PRIVILEGES ON DATABASE guacamole TO guacamole;
EOF

# Initialize Schema
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/001-create-schema.sql
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/002-create-admin-user.sql
```

### MySQL

```bash
mysql -u root -p <<EOF
CREATE DATABASE guacamole;
CREATE USER 'guacamole'@'%' IDENTIFIED BY 'securepass';
GRANT ALL PRIVILEGES ON guacamole.* TO 'guacamole'@'%';
FLUSH PRIVILEGES;
EOF

mysql -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/001-create-schema.sql
mysql -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/002-create-admin-user.sql
```

### SQL Server

```bash
sqlcmd -S localhost -U sa -P 'YourPassword' -Q "CREATE DATABASE guacamole;"
sqlcmd -S localhost -U sa -P 'YourPassword' -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/001-create-schema.sql
sqlcmd -S localhost -U sa -P 'YourPassword' -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/002-create-admin-user.sql
```

### Version Upgrades

Each database module contains upgrade scripts in the `schema/upgrade/` directory:

```bash
ls extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/upgrade/
# upgrade-pre-0.9.7.sql, upgrade-pre-0.9.8.sql, ..., upgrade-pre-1.5.5.sql
```

Only execute scripts newer than the current schema version. Execute in filename order.

---

## Maven Build System

### Module Hierarchy Tree

```
guacamole-client-spring-boot (pom)  ── Root POM, version ${revision}=1.5.5
│
├── guacamole-common                 ── Protocol library (JAR, ~51 Java files)
├── guacamole-common-js              ── JavaScript API (JAR)
├── guacamole-ext                    ── Extension API (JAR, ~136 Java files)
│
├── guacamole                        ── Web Application (Spring Boot Fat JAR)
│   └── frontend/                    ── Frontend source (AngularJS 1.8 + webpack 4)
│
└── extensions/                      ── Extension modules directory
    ├── guacamole-auth-jdbc (pom)
    │   ├── guacamole-auth-jdbc-base         ── JDBC shared code
    │   ├── guacamole-auth-mysql-starter     ── MySQL Starter
    │   ├── guacamole-auth-postgresql-starter─ PostgreSQL Starter
    │   └── guacamole-auth-sqlserver-starter ── SQL Server Starter
    ├── guacamole-auth-header-starter        ── HTTP Header Authentication
    ├── guacamole-auth-json-starter          ── JSON Encrypted Authentication
    ├── guacamole-auth-ldap-starter          ── LDAP Authentication
    ├── guacamole-auth-radius-starter        ── RADIUS Authentication
    ├── guacamole-auth-totp-starter          ── TOTP Two-Factor Authentication
    ├── guacamole-auth-duo-starter           ── Duo Two-Factor Authentication
    ├── guacamole-auth-quickconnect-starter  ── QuickConnect
    ├── guacamole-auth-sso (pom)
    │   ├── guacamole-auth-sso-base          ── SSO shared code
    │   ├── guacamole-auth-sso-cas-starter   ── CAS SSO
    │   ├── guacamole-auth-sso-openid-starter─ OpenID Connect SSO
    │   └── guacamole-auth-sso-saml-starter  ── SAML 2.0 SSO
    ├── guacamole-vault (pom)
    │   ├── guacamole-vault-base             ── Vault shared code
    │   └── guacamole-vault-ksm-starter      ── Keeper Secrets Manager Vault
    └── guacamole-history-starter            ── Session Recording Storage
```

### Key Maven Plugins

| Plugin | Stage | Purpose |
|--------|-------|---------|
| `spring-boot-maven-plugin` | `package` | Fat JAR repackaging, `repackage` goal, sets main class |
| `frontend-maven-plugin` 1.12.1 | `generate-resources` | Auto-installs Node.js/npm, runs `npm install` + webpack build |
| `minify-maven-plugin` 2.0.1 | `prepare-package` | Extension JS/CSS minification (Google Closure Compiler) |
| `maven-resources-plugin` | `process-resources` | Resource filtering (`useDefaultDelimiters=true`), resource copying |
| `maven-compiler-plugin` | `compile` | JDK 17 compilation with `-Xlint:all` |
| `flatten-maven-plugin` 1.6.0 | `process-resources` | Resolves `${revision}` into the published POM |

### Resource Filtering Configuration

The root POM sets `useDefaultDelimiters=true` in `<pluginManagement>`, inherited by all modules. `guacamole/pom.xml` uses two `<resource>` configurations for selective filtering:

```xml
<resources>
    <!-- File set with filtering enabled -->
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
        <includes>
            <include>static/index.html</include>
            <include>static/verifyCachedVersion.js</include>
            <include>**/application*.yml</include>
            <include>**/application*.yaml</include>
            <include>**/application*.properties</include>
        </includes>
    </resource>
    <!-- File set with filtering disabled -->
    <resource>
        <directory>src/main/resources</directory>
        <filtering>false</filtering>
        <excludes>
            <exclude>static/index.html</exclude>
            <exclude>static/verifyCachedVersion.js</exclude>
            <exclude>**/application*.yml</exclude>
            <exclude>**/application*.yaml</exclude>
            <exclude>**/application*.properties</exclude>
        </excludes>
    </resource>
</resources>
```

### Dependency Management

All dependency versions are centralized in the root POM's `<dependencyManagement>`. Sub-modules declare dependencies **without specifying versions** (unless different from the managed version).

Key managed dependencies:

| Dependency | Version | Purpose |
|------------|---------|---------|
| Guava | 32.1.3-jre | General utility library |
| Jackson | 2.17.2 | JSON serialization |
| MyBatis Spring Boot | 3.0.3 | Database mapping |
| Apache Directory LDAP API | 2.1.6 | LDAP authentication |
| JRadius | 1.1.5 | RADIUS authentication |
| CAS Client | 3.6.4 | CAS SSO |
| jose4j | 0.9.6 | JOSE implementation (JWT) |
| OneLogin java-saml | 2.9.0 | SAML SSO |
| Keeper KSM | 16.6.3 | Keeper secrets management |
| ZXing | 3.5.3 | QR code generation (TOTP) |

### Version Management

Uses Maven CI-Friendly version management. The root POM defines:

```xml
<properties>
    <revision>1.5.5</revision>
</properties>
```

All modules inherit `${revision}` as their version. `flatten-maven-plugin` resolves this variable during the `process-resources` stage, so published POMs contain the actual version number instead of a placeholder.

---

## Troubleshooting

### Build Error: "Node.js not found"

`frontend-maven-plugin` automatically downloads Node.js. If the download fails (proxy, network issues):

```bash
# Via proxy
mvn clean package -DskipTests -Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080

# Manually install Node.js 18.18.0 to PATH and retry
```

### Webpack Build Error

Clear node_modules and retry:

```bash
cd guacamole/frontend
rm -rf node_modules package-lock.json
npm install
npm run build
```

If the error involves OpenSSL (e.g., `ERR_OSSL_EVP_UNSUPPORTED`), the build automatically sets `NODE_OPTIONS=--openssl-legacy-provider`.

### Maven Compilation Error

```bash
# Full clean rebuild
mvn clean compile

# Verbose output
mvn clean compile -X
```

### Application Cannot Connect to guacd

```bash
# Check if guacd is running
docker ps | grep guacd

# Test connection
telnet localhost 4822

# Check guacd connection details in application logs
# Confirm guacamole.guacd.hostname and guacamole.guacd.port in application.yml are correct
```

### Slow Build (npm install every time)

The `skip-npm-install` profile auto-activates when `node_modules` exists. Force re-install:

```bash
mvn clean package -Dforce.npm.install
```

Skip frontend entirely (backend development):

```bash
mvn clean package -Dskip.frontend
```
