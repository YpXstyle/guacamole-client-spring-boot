# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Apache Guacamole 1.5.5 migrated from Google Guice to Spring Boot 3.3.5 (Java 17). This is a remote desktop gateway — browser-based access to RDP, VNC, SSH, Telnet, and Kubernetes via a backend daemon (guacd). The Group ID was changed from `org.apache.guacamole` to `com.right`; all public REST APIs remain compatible with upstream.

## Build & Run Commands

```bash
# Full build (skip tests)
mvn clean package -DskipTests

# Build only the web app + its dependencies (fastest usable build)
mvn clean package -pl guacamole -am -DskipTests

# Build a single extension module
mvn clean package -pl extensions/guacamole-auth-ldap-starter -am -DskipTests

# Build and run tests
mvn clean package

# Run with Spring Boot Maven plugin (dev mode)
mvn spring-boot:run -pl guacamole

# Run fat JAR
java -jar guacamole/target/guacamole-*.jar

# Docker
docker-compose up -d
```

Tests are primarily in the `guacamole` module. Extension modules use Spring Boot Test for DI integration tests.

## Module Architecture

The Maven reactor has 4 core modules + the extensions tree:

```
guacamole-common/       — Low-level Guacamole protocol: exception hierarchy, tunnel abstraction, I/O reader/writer
guacamole-common-js/    — JavaScript client (AngularJS frontend, built with frontend-maven-plugin + Node 18)
guacamole-ext/          — Extension SPI: Environment, Form/Field model, Translatable exceptions, AuthenticationProvider interface
guacamole/              — Main Spring Boot application (fat JAR target), contains all config, REST, WebSocket, and servlet code
extensions/             — Authentication/protocol extension starters (see below)
```

The `guacamole` module is the only one that produces a runnable artifact. It has `compile` scope dependencies on all extension starters, so the fat JAR includes everything.

### Extension starters

Each extension lives under `extensions/` and follows the standard Spring Boot Starter pattern:

- **Naming**: `<category>-<name>-starter` (e.g., `guacamole-auth-ldap-starter`)
- **Auto-config**: A single `*AutoConfiguration` class annotated with `@Configuration` + `@ConditionalOnProperty(prefix = "guacamole.auth.<name>", name = "enabled", havingValue = "true")`
- **Manifest**: Each starter includes a `guac-manifest.json` on its classpath listing JS, CSS, HTML patches, static resources, and a `configProperty` key that governs enablement
- **AuthenticationProvider**: Each extension exposes a `@Bean` that creates an `AuthenticationProvider` implementation (collected by Spring into `List<AuthenticationProvider>` via auto-wiring)
- **Mutual exclusion**: JDBC modules (MySQL/PostgreSQL/SQLServer) and SSO modules (CAS/OpenID/SAML) must not be enabled together — validated at startup in `EnvironmentConfig.validateModules()`

Key extension packages:
- `extensions/guacamole-auth-jdbc/` — multi-module parent containing `guacamole-auth-jdbc-base` (shared MyBatis mappers, modeled services) + 3 database-specific starters
- `extensions/guacamole-auth-sso/` — multi-module parent for CAS, OpenID Connect, and SAML
- `extensions/guacamole-vault/` — multi-module parent for Vault (base + KSM starter)
- `extensions/guacamole-history-starter/` — session recording storage/playback

## Key Architecture Decisions

### DI migration: Guice → Spring

| Original (Guice) | Spring Boot Migration |
|---|---|
| `@Inject` | `@Autowired` (with `@Qualifier` for named beans) |
| `AbstractModule.bind()` | `@Configuration` + `@Bean` |
| `FactoryModuleBuilder` | Manual factory implementations in `ResourceFactoryConfig` using `AutowireCapableBeanFactory` |
| `ServiceLoader` + `GUACAMOLE_HOME/extensions/` | `ComponentScan` (excluding extension packages) + classpath jar auto-inclusion |

### Property resolution bridge

`EnvironmentConfig` creates a `LocalEnvironment` that bridges Spring's `Environment` into the Guacamole `Environment` interface. It maps legacy flat property names (e.g., `guacd-hostname`) to Spring namespaced keys (`guacamole.guacd.hostname`) by probing 16+ namespace prefixes. System environment variables are also available through `SystemEnvironmentGuacamoleProperties`.

### Extension resource loading

`ExtensionResourceConfig` scans `classpath*:guac-manifest.json` at startup, filters by `configProperty` (enabled/disabled), then:
- Concatenates all JS/CSS into `/app.js` and `/app.css` via `ResourceServlet` (with 304 caching)
- Caches static resources in-memory for `/app/ext/{namespace}/{path}` (works around `classpath*:` limitations with nested JARs in fat JAR)
- Loads HTML patches via `PatchResourceService` → `/api/patches`
- `LanguageConfig` loads translations only from enabled extension modules, using module-area analysis of classpath URLs (works for both exploded dev layout and fat JAR)

### REST API (Jersey JAX-RS)

The REST layer is NOT Spring MVC — it's Jersey JAX-RS served at `/api/*` via `JerseyConfig`. Key sub-resource locator chain:

```
SessionRESTService (/api/session/{token})
  └── SessionResource (/api/session/{token}/data/{source})
        └── UserContextResource
              ├── ConnectionDirectoryResource
              │     └── ConnectionResource
              ├── ConnectionGroupDirectoryResource
              ├── UserDirectoryResource / UserGroupDirectoryResource
              ├── SharingProfileDirectoryResource
              ├── ActiveConnectionDirectoryResource
              ├── HistoryResource
              └── SchemaResource
```

Authentication tokens are generated by `SecureRandomAuthTokenGenerator` and stored in `HashTokenSessionMap`. The token is passed via `@QueryParam("token")`.

### WebSocket tunnel

`WebSocketConfig` registers a JSR-356 `@ServerEndpoint` at `/websocket-tunnel` using the Guacamole sub-protocol. The HTTP fallback tunnel is at `/tunnel` via `RestrictedGuacamoleHTTPTunnelServlet`. Both connect to guacd over TCP (port 4822 by default).

### MyBatis for JDBC extensions

The JDBC extensions (`guacamole-auth-jdbc-base`) use MyBatis (`mybatis-spring-boot:3.0.3`) for database access. Mapper interfaces extend `ModeledDirectoryObjectMapper`, and services extend generic `ModeledDirectoryObjectService<T>`.

## Configuration

All configuration is in `guacamole/src/main/resources/application.yml`. Extension properties use the prefix `guacamole.auth.<type>` or `guacamole.vault.<type>`. The `guacamole.guacd` prefix configures the connection to the guacd daemon.

Environment variables (e.g., `GUACAMOLE_DB_PASSWORD`, `GUACAMOLE_HISTORY_RECORDING_PATH`) are referenced in `application.yml` with `${NAME:default}` syntax.

## Important Constraints

- **Java 17** is mandatory (defined in `pom.xml` properties)
- **JDBC modules are mutually exclusive** — at most one of MySQL, PostgreSQL, SQL Server can be enabled
- **SSO modules are mutually exclusive** — at most one of CAS, OpenID, SAML can be enabled
- **DUO uses SDK v2** which was deprecated in March 2024 — needs upgrade to v4
- The `guacamole` module `@ComponentScan` excludes `org.apache.guacamole.auth.*`, `org.apache.guacamole.vault.*`, and `org.apache.guacamole.history.*` — these are loaded only through their starter auto-configurations
- The frontend is an AngularJS application built by `frontend-maven-plugin` (Node 18.18.0) in the `guacamole-common-js` module

## Docs

- `docs/ARCHITECTURE.md` — detailed system architecture
- `docs/BUILD.md` — build and deployment guide
- `docs/CONFIGURATION.md` — complete configuration reference
- `docs/EXTENSIONS.md` — extension module guide
- `docs/MIGRATION.md` — Guice-to-Spring migration details
- `docs/REST-API.md` — REST API reference
- `docs/QUICK-START.md` — step-by-step setup
