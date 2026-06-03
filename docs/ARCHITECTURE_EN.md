# System Architecture

[← Back to Documentation](../README.md#documentation)

In-depth architecture analysis of the Guacamole Spring Boot system. This document covers system components, request flows, authentication mechanisms, and key design patterns.

## Table of Contents

- [Overall Architecture](#overall-architecture)
- [Request Flow](#request-flow)
- [Core Components in Detail](#core-components-in-detail)
- [Extension System Loading Mechanism](#extension-system-loading-mechanism)
- [Resource Services](#resource-services)
- [WebSocket Tunnel](#websocket-tunnel)
- [REST API Layer](#rest-api-layer)
- [System Configuration Module](#system-configuration-module)
- [Authentication Flow](#authentication-flow)
- [Property Resolution Mechanism](#property-resolution-mechanism)
- [Guice to Spring Migration Mapping](#guice-to-spring-migration-mapping)

---

## Overall Architecture

```
+---------------------------------------------------------------------+
|                          Browser                                      |
|  +------------+  +------------+  +------------+  +----------------+  |
|  | REST API   |  | WebSocket  |  | Static     |  | Extension      |  |
|  | (/api/*)   |  |(/websocket-|  | Resources  |  | Resources      |  |
|  |            |  | /tunnel)   |  | (/app.js,  |  |(/app/ext/*)    |  |
|  |            |  |            |  | /app.css)  |  |                |  |
|  +-----+------+  +------+-----+  +-----+------+  +-------+--------+  |
+--------+-------------+---------------+-------------------+-----------+
         |             |               |                   |
+--------v-------------v---------------v-------------------v-----------+
|                      Spring Boot Application (Tomcat Container)       |
|                                                                      |
|  +---------------------------------------------------------------+  |
|  | Jersey Servlet Container (/api/*)  | JacksonFeature (JSON)     |  |
|  |  +----------------+  +------------------+  +---------------+  |  |
|  |  | SessionREST    |  | TokenRESTService  |  | PatchREST     |  |  |
|  |  | Service        |  | (/api/tokens)     |  | Service       |  |  |
|  |  +-------+--------+  +------------------+  | (/api/patches) |  |  |
|  |          |                                   +---------------+  |  |
|  |  +-------v--------+                                              |  |
|  |  | SessionResource|—— Sub-resource locator pattern               |  |
|  |  |  @Path("data/  |  → UserContextResource                       |  |
|  |  |    {source}")  |    → ConnectionDirectoryResource             |  |
|  |  |                |      → ConnectionResource (CRUD + params +   |  |
|  |  |                |         history + sharingProfiles)            |  |
|  |  |                |    → ConnectionGroupDirectoryResource         |  |
|  |  |                |    → UserDirectoryResource                   |  |
|  |  |                |    → UserGroupDirectoryResource               |  |
|  |  |                |    → SharingProfileDirectoryResource          |  |
|  |  |                |    → ActiveConnectionDirectoryResource        |  |
|  |  |                |    → HistoryResource                          |  |
|  |  |                |    → SchemaResource                           |  |
|  |  +----------------+                                               |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  +---------------------------------------------------------------+  |
|  | Resource Servlet (Resource Services)                            |  |
|  |  /app.js   — Extension JS concatenation (+ verifyCachedVersion) |  |
|  |  /app.css  — Extension CSS concatenation                        |  |
|  |  /images/logo-64.png  — Small icon (overridable by extensions)  |  |
|  |  /images/logo-144.png — Large icon (overridable by extensions)  |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  +---------------------------------------------------------------+  |
|  | Core Services (Spring Beans)                                    |  |
|  |  EnvironmentConfig: Property bridge (16+1 namespace prefixes)   |  |
|  |  CoreServicesConfig: TokenSessionMap / AuthTokenGenerator        |  |
|  |  ResourceFactoryConfig: Factory beans (replaces Guice            |  |
|  |                         FactoryModuleBuilder)                    |  |
|  |  JerseyConfig: Jersey container config (/api/*)                  |  |
|  |  WebSocketConfig: JSR 356 WebSocket endpoint registration       |  |
|  |  FilterConfig: CacheRevalidationFilter + HTTP Tunnel fallback    |  |
|  |  ExtensionResourceConfig: Extension resource loading             |  |
|  |                               (guac-manifest.json)              |  |
|  |  LanguageConfig: Translation file loading (area-based filtering) |  |
|  |  LogConfig: JUL to SLF4J bridge                                 |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  +---------------------------------------------------------------+  |
|  | Extension Modules (AutoConfiguration)                           |  |
|  |  HeaderAuth | JSONAuth | JdbcAuth | LdapAuth | RadiusAuth       |  |
|  |  TOTP | Duo | QuickConnect | SSO (CAS/OpenID/SAML)              |  |
|  |  Vault KSM | History Recording                                   |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  +---------------------------------------------------------------+  |
|  | WebSocket Endpoints                                             |  |
|  |  /websocket-tunnel (JSR-356 @ServerEndpoint)                    |  |
|  |  /tunnel (HTTP fallback, RestrictedGuacamoleHTTPTunnelServlet)  |  |
|  +---------------------------------------------------------------+  |
+----------------------------+----------------------------------------+
                             | Guacamole Protocol (TCP)
+----------------------------v----------------------------------------+
|                     guacd (C Daemon, Port 4822)                      |
|  +------+  +------+  +------+  +--------+  +------------+           |
|  | RDP  |  | VNC  |  | SSH  |  | Telnet |  | Kubernetes |           |
|  +------+  +------+  +------+  +--------+  +------------+           |
+----------------------------------------------------------------------+
```

---

## Request Flow

### REST API Request

The client provides the authentication token via one of four methods: `Guacamole-Token` HTTP header, `token` query parameter, `Authorization: Basic` header, or `POST /api/tokens` form parameters on initial login.

```
Browser → HTTP GET /api/session/data/mysql/connections?token=xxx
  → Jersey Container (/api/* mapping)
    → TokenParamProvider extracts token
      → SessionRESTService.getSessionResource(token)
        → SessionResource.getUserContextResource("mysql")
          → UserContextResource.getConnectionDirectoryResource()
            → ConnectionDirectoryResource.getObjectResource("5")
              → ConnectionResource.get() → JSON serialization → HTTP 200
```

### Tunnel Request

Tunnel creation is a two-step process: first create the tunnel via REST API, then establish the WebSocket connection.

```
Step 1: REST API creates tunnel
Browser → HTTP POST /api/session/tunnels {"connectionIdentifier": "5"}
  → TunnelCollectionResource.createTunnel()
    → TunnelRequestService.createTunnel()
      → User authentication → Get UserContext
        → Connectable.connect(info, tokens) → guacd connection (TCP :4822)
          → Returns GuacamoleTunnel (with UUID)
            → Tunnel stored in GuacamoleSession

Step 2: WebSocket connection
Browser → WebSocket /websocket-tunnel?uuid=...&token=...
  → GuacamoleWebSocketEndpoint.onOpen()
    → WebSocketTunnelRequest extracts UUID and token from query params
      → TunnelRequestService.createTunnel() retrieves existing tunnel
        → Read thread starts: tunnel UUID → read instructions from guacd → send to WebSocket
        → Write handler: WebSocket messages → filter → write to guacd
```

### Static Resource Request

```
Browser → GET /app.js
  → ResourceServlet (ServletRegistrationBean)
    → ByteArrayResource (pre-concatenated at startup)
      → HTTP 200 + ETag → Browser cache (304 Not Modified support)

Browser → GET /app.css
  → ResourceServlet
    → ByteArrayResource (pre-concatenated at startup)
      → HTTP 200 + ETag → 304 support
```

### Extension Resource Request

```
Browser → GET /app/ext/totp/templates/authenticationCodeField.html
  → Spring ResourceHandler (higher priority than default static resources)
    → CachedExtensionResourceResolver
      → Lookup from pre-loaded cache (Map<String, CachedResourceData>)
        → "totp/templates/authenticationCodeField.html" → byte[]
          → HTTP 200

Browser → GET /translations/zh.json
  → TranslationController (Spring @RestController)
    → LanguageResourceService → byte[] → HTTP 200 + ETag
```

---

## Core Components in Detail

### GuacamoleSpringBootApplication

Application entry point. Configures component scanning to **exclude** extension packages (extensions are loaded by their own AutoConfiguration classes):

```java
@SpringBootApplication
@ComponentScan(
    basePackages = "org.apache.guacamole",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "org\\.apache\\.guacamole\\.(auth\\..*|vault\\..*|history\\..*)"
    )
)
```

Checks the `-Duser.timezone` system property at startup and sets the JVM default timezone.

### EnvironmentConfig

Bridges Spring `Environment` to Guacamole `LocalEnvironment`. This is the property resolution hub for the entire system.

**Core method:** `guacamoleEnvironment()` creates a `@Bean` of type `Environment`, marked as `@Primary`.

**Bridge (GuacamoleProperties adapter):**
1. Checks exact name match (e.g., `"ldap-hostname"`)
2. Checks legacy guacd property mapping (`"guacd-hostname"` → `"guacamole.guacd.hostname"`)
3. Iterates through 16 namespace prefixes attempting concatenated lookup (see [Property Resolution Mechanism](#property-resolution-mechanism))

**Second bridge:** `SystemEnvironmentGuacamoleProperties` as fallback, allowing environment variable overrides.

**Module validation (@PostConstruct):**
- **JDBC mutual exclusion:** MySQL, PostgreSQL, SQL Server — only one can be enabled
- **SSO mutual exclusion:** CAS, OpenID, SAML — only one can be enabled
- Throws `IllegalStateException` listing enabled modules when rules are violated

### JerseyConfig

Configures the Jersey JAX-RS container, mapped to `/api/*`:

```java
@Configuration
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {
    // Package scanning:
    packages("org.apache.guacamole.rest");       // Core REST resources
    packages("org.apache.guacamole.auth.sso");   // SSO REST resources
    // Conditional scanning:
    if (samlEnabled) → packages("org.apache.guacamole.auth.saml");
    
    // Register Jackson JSON serialization
    register(JacksonFeature.class);
}
```

Installs `SLF4JBridgeHandler` in the constructor to bridge Jersey logging (JUL) to SLF4J.

### CoreServicesConfig

Registers core singleton services:

- **TokenSessionMap** (`HashTokenSessionMap` implementation) — Maps authentication tokens to `GuacamoleSession`
- **AuthTokenGenerator** (`SecureRandomAuthTokenGenerator` implementation) — Cryptographically secure token generation
- **FileAuthenticationProvider** — Default file-based authentication (fallback)
- **List\<File\>** temporary file list — Manages session-level temporary files

`AuthenticationProvider` and `Listener` beans are automatically collected by Spring, no explicit list definition needed.

### ResourceFactoryConfig

Replaces Guice's `FactoryModuleBuilder` with Spring-style Lambda factories. The core helper method `autowire()` uses `AutowireCapableBeanFactory.autowireBean()` to inject `@Autowired` fields.

Provides factory beans for the following types:
- **Simple factories:** SessionResource, UserContextResource, TunnelCollectionResource, TunnelResource
- **Directory factories (Directory + Object):** ActiveConnection, Connection, ConnectionGroup, SharingProfile, User, UserGroup
- **Object translators:** Translators for all the above types

Typical pattern:

```java
@Bean
public DirectoryObjectResourceFactory<Connection, APIConnection>
        connectionResourceFactory(ConnectionObjectTranslator translator) {
    return (parent, userContext, directory) ->
        autowire(new ConnectionResource(parent, userContext, directory, translator));
}
```

### WebSocketConfig

Registers WebSocket endpoints on the Tomcat `WsServerContainer`:

```java
ServerEndpointConfig config = ServerEndpointConfig.Builder
    .create(GuacamoleWebSocketEndpoint.class, "/websocket-tunnel")
    .subprotocols(Collections.singletonList("guacamole"))
    .build();
container.addEndpoint(config);
```

Registers endpoints via `TomcatServletWebServerFactory` and `TomcatContextCustomizer` after context startup. `TunnelRequestService` is injected into `GuacamoleWebSocketEndpoint` via a static setter called during `@PostConstruct`, because JSR-356 `@ServerEndpoint` instances are created by the container, not Spring.

### FilterConfig

Registers Servlet filters:
- **CacheRevalidationFilter** — Applied to `/index.html`, prevents stale cache after deployment
- **RestrictedGuacamoleHTTPTunnelServlet** — Registered at `/tunnel`, provides HTTP tunnel fallback when WebSocket is unavailable (uses `applicationContext.getAutowireCapableBeanFactory().autowireBean()` for manual dependency injection)

### ExtensionResourceConfig

Implements `WebMvcConfigurer`. Core responsibilities:
1. Scans `classpath*:guac-manifest.json` for all extension manifests
2. Filters enabled extensions by `configProperty`
3. Pre-loads extension JavaScript/CSS into concatenated byte arrays (`/app.js` and `/app.css`)
4. Pre-loads extension static resources into memory cache (`Map<String, CachedResourceData>`)
5. Registers ResourceServlet beans (ETag/304 support)
6. Registers custom `CachedExtensionResourceResolver` for `/app/ext/**`
7. Builds `PatchResourceService` from extension HTML patches

**Icon override logic:** `loadExtensionIcon()` iterates through the manifest's `smallIcon` and `largeIcon` fields — the last extension's icon wins, falling back to default icons when not found.

### LanguageConfig

Loads translation files (`/translations/*.json`) using area-based filtering:

1. Scans all `guac-manifest.json` files to extract extension module areas
2. Checks `configProperty` to determine if module is enabled → builds `enabledAreas` and `manifestGroupAreas`
3. Loads translations from enabled areas and core areas, skipping disabled extension areas
4. Handles shared base modules (e.g., JDBC base, SSO base) — parent areas are also added to the enabled set

`extractModuleArea()` method extracts module areas from URLs, supporting fat JAR, nested JAR, and development directory layouts.

### LogConfig

Installs `SLF4JBridgeHandler` at `@PostConstruct` and uninstalls at `@PreDestroy`.

---

## Extension System Loading Mechanism

### Loading Flow

```
1. Spring Boot starts
2. AutoConfiguration.imports files discovered on classpath
3. Each AutoConfiguration class is loaded
4. @ConditionalOnProperty check → Is it enabled?
5. @Bean method executes → Creates Provider/Service instances
6. Spring auto-collects AuthenticationProvider beans → List<AuthenticationProvider>
7. Authentication providers are arranged into the authentication chain
8. ExtensionResourceConfig scans classpath for guac-manifest.json
9. Static resources, translation files, and HTML patches are loaded
10. SSO extensions (CAS, OpenID, SAML) register WebSocket endpoints when enabled
```

### guac-manifest.json Processing

`ExtensionResourceConfig.extensionManifests()` uses Spring's `ResourcePatternResolver` to scan:

```java
resolver.getResources("classpath*:guac-manifest.json");
```

Each manifest is parsed and filtered by `configProperty`. Enabled extensions are used for:
- Building concatenated `/app.js` (all extension JS merged)
- Building concatenated `/app.css` (all extension CSS merged)
- Pre-loading static resources (`/app/ext/{namespace}/*`)
- Collecting HTML patches (`/api/patches`)
- Identifying icon overrides (`smallIcon`, `largeIcon`)

### Extension Static Resource Cache

All extension static resources are pre-loaded into memory cache at startup:

```
Map<String, CachedResourceData>:
  "totp/templates/authenticationCodeField.html" → byte[] + MIME type
  "duo/styles/duo.css" → byte[] + MIME type
  ...
```

The cache is populated using `ClassLoader.getResources()`, correctly handling nested JARs in Spring Boot fat JARs. A custom `CachedExtensionResourceResolver` (extending `AbstractResourceResolver`) intercepts `/app/ext/**` requests to serve resources.

### Translation Area Filtering

`LanguageConfig` implements complex area filtering to ensure only enabled extensions' translation files are loaded. Key points:
- Translations within the same module group share areas
- Shared base modules (e.g., `guacamole-auth-jdbc-base`) inherit through parent area matching
- `areaMatches()` uses exact match or prefix+slash-boundary matching to prevent false matches

---

## Resource Services

### /app.js and /app.css

Built by `ExtensionResourceConfig` at startup:
1. Reads `verifyCachedVersion.js` from classpath (prepended first)
2. Reads each manifest's `js`/`css` array entries
3. All concatenated into a single `String` → `ByteArrayResource`
4. Registers `ResourceServlet` (supports 304 / If-None-Match / ETag)

### /app/ext/{namespace}/{path}

Served via pre-loaded cache through `CachedExtensionResourceResolver`, registered in the Spring resource handler chain with higher priority than the default static resource handler.

### /translations/{lang}.json

Loaded by `LanguageConfig` at `@PostConstruct` and registered with `LanguageResourceService`. `TranslationController` (Spring `@RestController`) serves translation files at runtime.

### /images/logo-*.png

Served by a dedicated `ResourceServlet` bean. Extension manifests can override via `smallIcon` and `largeIcon` fields (last extension's icon wins). Falls back to core webapp's `static/images/logo-64.png` and `static/images/logo-144.png`.

---

## WebSocket Tunnel

### Endpoint Registration

`WebSocketConfig` registers `GuacamoleWebSocketEndpoint` at `/websocket-tunnel` on the Tomcat `WsServerContainer`. The endpoint supports the `guacamole` sub-protocol.

### Data Flow

```
Browser WebSocket
  ↔ GuacamoleWebSocketEndpoint (JSR-356 @ServerEndpoint)
    ↔ GuacamoleTunnel (guacamole-common library)
      ↔ TunnelRequestService
        ↔ guacd TCP socket (port 4822)
          ↔ Remote Desktop Protocol (RDP/VNC/SSH/...)
```

### Read Thread

A daemon read thread is started when the WebSocket opens:
1. Sends the tunnel UUID as an internal instruction (empty opcode + UUID parameter)
2. Reads Guacamole protocol instructions from the tunnel
3. Output buffering, max `BUFFER_SIZE` (8192 characters), reducing WebSocket message count
4. Sends buffered data as WebSocket text messages
5. Graceful handling via Guacamole status codes on connection close

### Ping/Pong

The `@OnMessage` handler implements connection stability detection. Tunnel internal instructions (identified by `GuacamoleTunnel.INTERNAL_DATA_OPCODE`) are filtered and not passed to guacd. `ping` requests return `ping` responses with the same correlation ID.

### Error Handling

- **@OnError:** Logs error and closes the associated tunnel
- **@OnClose:** Closes the associated tunnel (cleanup)
- **Connection errors:** Guacamole status codes mapped to WebSocket close codes
- **TunnelRequestService:** Automatically invalidates the corresponding session when `GuacamoleUnauthorizedException` is caught

### HTTP Tunnel Fallback

`RestrictedGuacamoleHTTPTunnelServlet` registered at `/tunnel` provides HTTP long-polling fallback when WebSocket is unavailable (e.g., proxy restrictions).

---

## REST API Layer

### Resource Hierarchy Tree

```
/api/
+-- tokens/                                       TokenRESTService
|   +-- POST   — Create token (login, no auth required)
|   +-- DELETE /{token} — Invalidate token (logout)
+-- session/                                      SessionRESTService
|   +-- GET    — Get SessionResource
|   +-- DELETE — Logout current session
|   +-- data/{source}/                            SessionResource → UserContextResource
|   |   +-- self/                                 Current user info
|   |   +-- connections/                          Connection directory
|   |   |   +-- {id}/                             Connection resource
|   |   |       +-- parameters                    Get connection parameters
|   |   |       +-- history                       Connection usage history
|   |   |       +-- sharingProfiles/              Connection sharing profiles
|   |   +-- connectionGroups/                     Connection group directory
|   |   |   +-- {id}/                             Connection group resource
|   |   |       +-- tree                          Get connection group tree
|   |   +-- users/                                User directory
|   |   |   +-- {username}/                       User resource
|   |   |       +-- password                      Change password
|   |   |       +-- permissions                   Get/update user permissions
|   |   |       +-- effectivePermissions          Get effective permissions
|   |   |       +-- userGroups                    User group membership management
|   |   +-- userGroups/                           User group directory
|   |   |   +-- {id}/                             User group resource
|   |   |       +-- permissions                   User group permissions
|   |   |       +-- memberUsers                   Group member user management
|   |   |       +-- memberUserGroups              Group member group management
|   |   +-- sharingProfiles/                      Sharing profile directory
|   |   |   +-- {id}/                             Sharing profile resource
|   |   +-- activeConnections/                    Active connection directory
|   |   |   +-- {id}/                             Active connection resource
|   |   |       +-- connection                    Connection details
|   |   |       +-- connection/sharingProfiles    Sharing profiles
|   |   +-- history/                              History
|   |   |   +-- connections                       Connection history
|   |   |   +-- users                             User login history
|   |   |   +-- active                            Current active sessions
|   |   +-- schema/                               Property Schema
|   |       +-- userAttributes                    User attribute definitions
|   |       +-- userGroupAttributes               User group attribute definitions
|   |       +-- connectionAttributes              Connection attribute definitions
|   |       +-- connectionGroupAttributes         Connection group attribute definitions
|   |       +-- sharingProfileAttributes          Sharing profile attribute definitions
|   |       +-- protocols                         Protocol information
|   +-- tunnels/                                  Tunnel collection
|   |   +-- GET    — Get all active tunnel UUIDs
|   |   +-- POST   — Create new tunnel
|   |   +-- {uuid}/                               TunnelResource
|   |       +-- GET    — Get tunnel status
|   |       +-- activeConnection/                 Active connection resource
|   |       |   +-- connection                    Connection details
|   |       |   +-- connection/sharingProfiles    Sharing profiles
|   |       +-- protocol                          Tunnel protocol
|   |       +-- streams/{index}/{filename}        Stream data transfer
|   +-- ext/{source}/                             Extension-specific REST endpoints
+-- patches/                                      Extension HTML patches
+-- languages/                                    Language list
+-- /translations/{lang}.json                     Translation JSON files
```

### Sub-Resource Locator Pattern

Jersey's sub-resource locator pattern allows chained resource access without explicit registration. The core mechanism is `@Path` annotated methods returning sub-resource instances, and Jersey's runtime type discovery (`getClass()`) automatically discovers the sub-resource's `@Path` methods.

```java
// Sub-resource locator in SessionResource
@Path("data/{dataSource}")
public UserContextResource getUserContextResource(@PathParam("dataSource") String id) {
    // Jersey automatically discovers UserContextResource's @Path("connections") etc.
    return userContextResourceFactory.create(userContext);
}
```

Resources are created via factories, then `autowireBean()` injects `@Autowired` fields.

### REST Services

- **TokenRESTService** (`/api/tokens`): Supports HTTP Basic Auth and form parameter authentication. `POST` creates token, `DELETE /{token}` invalidates token.
- **SessionRESTService** (`/api/session`): Entry point, injects token via `@TokenParam`, routes to `SessionResource`.
- **PatchRESTService** (`/api/patches`): Provides extension HTML patch list.
- **SettingsRESTService** (`/api/settings`): System configuration management, `GET` returns 25 config entries, `PUT/{key}` updates single entry (requires `SYSTEM_ADMINISTER`).
- **ConfigRESTService** (`/api/config`): Public configuration endpoint, no authentication required, returns branding/theme/announcement config.
- **FileRESTService** (`/api/settings/files`): File upload/download/delete, `POST` upload (multipart), `GET/{id}` public access, `DELETE/{id}` admin delete.
- **ExtensionRESTService** (`/api/ext/{identifier}`): Provides extension custom REST resources.

---

## System Configuration Module

### Data Flow

```
Admin modifies branding/theme/security/announcement on system config page
  ↓ $http PUT /api/settings/{key} + Guacamole-Token
SettingsRESTService (Jersey JAX-RS)
  ↓ verifyAdminPermission() → SYSTEM_ADMINISTER
SystemConfigService.updateValue()
  ↓ SystemConfigMapper.update() (pure UPDATE, no INSERT)
guacamole_system_config table
  ↓ configCache.invalidate(key)
Next /api/config request returns new value
```

### Configuration Priority (Three-Level Fallback)

```
1. guacamole_system_config table (runtime modification, admin saves via web UI)
   ↓ Fallback when no value
2. application.yml guacamole.system.defaults section (build-time config)
   ↓ Fallback when no value
3. Guacamole native guacamole.properties (e.g., postgresql-user-password-min-length)
```

### Theme Engine

```
configService.getConfig() → theme config
  ↓
colorEngine.calculate(themeConfig, mode) → 35 CSS variable values
  ↓
themeService.setThemeVariable(name, value)
  ↓ document.documentElement.style.setProperty()
:root variables take effect → site-wide UI color update
```

- Modes: `light` / `dark` / `auto` (follows `prefers-color-scheme`)
- CSS variables declare fallback defaults in `variables.css`; CSS defaults are used when JS fails

### Security Policy Integration

The 4 password complexity config entries (`security.password_min_length` etc.) are integrated with all three databases' `PasswordPolicy`:

```
PasswordPolicyService.verifyPassword()
  ↓
PostgreSQLPasswordPolicy / MySQLPasswordPolicy / SQLServerPasswordPolicy
  ↓ getDBValue("security.password_min_length")
SystemConfigService.getValue(key)
  ↓ Has value → Integer.parseInt(value)
  ↓ No value → environment.getProperty(MIN_LENGTH, 0) (original property fallback)
```

### File Upload Architecture

```
Frontend guacFileUpload.js
  ↓ FormData(file + category) + Guacamole-Token
FileRESTService.uploadFile()
  ↓ @FormDataParam → FormDataBodyPart (Jersey multipart)
FileStorageService.upload()
  ↓ validateUpload(MIME, size) + validateFilename(name) + validateCategory(cat)
  ↓ Files.copy() → {file-storage-path}/{category}/{fileId}/{filename}
  ↓ SystemFileMapper.insert() → guacamole_system_file table
Returns /api/settings/files/{fileId} URL
```

---

## Authentication Flow

### Login

```
1. POST /api/tokens {username, password}
   (or Authorization: Basic base64-encoded header)
2. TokenRESTService.createToken()
   → Build Credentials from request
   → Call AuthenticationService.authenticate(credentials, token)
3. AuthenticationService.authenticate():
   a. If existing token provided → Try to get existing session
   b. Get AuthenticatedUser:
      - Has session → Re-authenticate with original AuthenticationProvider
      - No session → Iterate each AuthenticationProvider:
        provider.authenticateUser(credentials)
        - Success → Return AuthenticatedUser
        - GuacamoleInsufficientCredentialsException → Record (takes priority over invalid credentials)
        - GuacamoleCredentialsException → Record the first one
        - All fail → Throw GuacamoleInvalidCredentialsException
   c. Get UserContexts:
      - Has session → Update existing UserContexts (call updateUserContext)
      - No session → Call each provider.getUserContext(authenticatedUser)
      - Decorate each UserContext (apply SSO, TOTP, vault decorators)
   d. Store/update session:
      - Has session → Update authenticatedUser and userContexts
      - New session → Generate new token → Store in TokenSessionMap
4. Return APIAuthenticationResult:
   {authToken, username, authProviderIdentifier, availableDataSources}
```

### Authenticated Request

Token passing methods (priority):
1. `Guacamole-Token` HTTP header (highest priority)
2. `token` query parameter

```java
// AuthenticationService.getAuthenticationToken():
String token = request.getHeaderString("Guacamole-Token");
if (token != null && !token.isEmpty()) return token;
token = request.getUriInfo().getQueryParameters().getFirst("token");
return token;
```

### Token Invalidation (Logout)

```
1. DELETE /api/tokens/{token}
2. TokenSessionMap.remove(token) → GuacamoleSession
3. session.invalidate() → Close all active tunnels
4. Or DELETE /api/session/ → Get session from current request's token and remove
```

### Shared Key Authentication

```
1. GET /#/?token=share-key-xxx
2. verifyCachedVersion.js checks build identifier for cache invalidation
3. index.html loads, AngularJS application starts
4. AuthenticationService authenticates with shared key as credentials
5. Matching SharedAuthenticationProvider → SharedUserContext (shared connection only)
```

### Multi-Factor Authentication

TokenRESTService's `createToken()` supports multi-step authentication:
- First request provides `username` + `password`
- Second step provides `token` (existing token) + additional credential fields
- AuthenticationService checks existing session and updates activeAuthentication state

---

## Property Resolution Mechanism

### Two-Layer System

```
┌────────────────────────────────────────────────┐
│                Application Layer Read           │
│  JDBCEnvironment.getProperty("ldap-hostname")  │
│  → DelegatingEnvironment                       │
│    → LocalEnvironment.getInstance()             │
└──────────────────┬─────────────────────────────┘
                   │
┌──────────────────v─────────────────────────────┐
│           EnvironmentConfig (Bridge)            │
│                                                  │
│  GuacamoleProperties Adapter #1 (Spring first) │
│  1. Exact match: "ldap-hostname"                │
│  2. Legacy guacd: "guacd-hostname" →            │
│     "guacamole.guacd.hostname"                  │
│  3. Prefix loop: "guacamole.auth.ldap." + name  │
│                                                  │
│  GuacamoleProperties Adapter #2 (env fallback)  │
│  SystemEnvironmentGuacamoleProperties            │
│  → GUACAMOLE_AUTH_LDAP_LDAP_HOSTNAME            │
└──────────────────┬─────────────────────────────┘
                   │
┌──────────────────v─────────────────────────────┐
│            Spring Environment                    │
│  application.yml → Env vars → -D system props   │
│  → CLI args --guacamole.auth.ldap.ldap-         │
│    hostname=...                                 │
└────────────────────────────────────────────────┘
```

### 16 Namespace Prefixes

`EnvironmentConfig` iterates these prefixes, attempting concatenated lookup when the direct property name is not found:

| Namespace | Extension | Example Property |
|-----------|-----------|-----------------|
| `guacamole.auth.mysql` | MySQL JDBC | `mysql-hostname` |
| `guacamole.auth.postgresql` | PostgreSQL JDBC | `postgresql-hostname` |
| `guacamole.auth.sqlserver` | SQL Server JDBC | `sqlserver-hostname` |
| `guacamole.auth.header` | HTTP Header Auth | `header-redirect-url` |
| `guacamole.auth.duo` | Duo Two-Factor | `duo-integration-key` |
| `guacamole.auth.json` | JSON Encrypted Auth | `json-secret-key` |
| `guacamole.auth.ldap` | LDAP Auth | `ldap-hostname` |
| `guacamole.auth.totp` | TOTP Two-Factor | `totp-issuer` |
| `guacamole.auth.radius` | RADIUS Auth | `radius-hostname` |
| `guacamole.auth.quickconnect` | QuickConnect | `quickconnect-` |
| `guacamole.auth.sso-cas` | SSO CAS | `cas-` |
| `guacamole.auth.sso-openid` | SSO OpenID | `openid-` |
| `guacamole.auth.sso-saml` | SSO SAML | `saml-` |
| `guacamole.vault.ksm` | KSM Vault | `ksm-` |
| `guacamole.history` | History | `history-` |
| `guacamole.guacd` | guacd Connection | `guacd-hostname`, `guacd-port`, `guacd-ssl` |

### Explicit Mapping for Legacy Properties

Three guacd properties have explicit mappings in the bridge:
- `"guacd-hostname"` → `"guacamole.guacd.hostname"`
- `"guacd-port"` → `"guacamole.guacd.port"`
- `"guacd-ssl"` → `"guacamole.guacd.ssl"`

---

## Guice to Spring Migration Mapping

### Changed Items

| Layer | Guice (Original) | Spring Boot (This Project) |
|-------|-----------------|---------------------------|
| App Entry | `GuacamoleServletContextListener` | `@SpringBootApplication` |
| Module Loading | `AbstractModule` subclasses | `@Configuration` + `@Bean` |
| DI Annotation | `@Inject`, `@Singleton` | `@Autowired`, singleton by default |
| Factory Pattern | `FactoryModuleBuilder` | Lambda `DirectoryObjectResourceFactory` |
| REST Registration | Guice-HK2 bridge | Jersey auto-scanning (`packages()`) |
| Filter Registration | Guice `filter()` | `FilterRegistrationBean` / `@Bean` |
| WebSocket | Container-specific `WebSocketTunnelModule` | `WebSocketConfig` + JSR 356 |
| Servlet Context | `ServletContext` listener | Spring `ServletContextInitializer` |
| Extension Loading | `GuacamoleExtensionLoader` (ServiceLoader) | `ResourcePatternResolver` classpath scan |
| Configuration | `GUACAMOLE_HOME/guacamole.properties` | `application.yml` + Spring Environment |
| Property Bridge | Direct file reading | `EnvironmentConfig` → `LocalEnvironment` adapter |
| Timezone Setup | JVM default | `TimeZone.setDefault()` at app startup |

### Unchanged Items

| Layer | Description |
|-------|-------------|
| Protocol Layer | `guacamole-common` (~51 Java files) — Zero modification |
| Extension API | `guacamole-ext` (~136 Java files) — Only javax to jakarta migration |
| JavaScript API | `guacamole-common-js` (~34 modules) — Zero modification |
| REST Annotations | All `@Path`, `@GET`, `@POST` etc. preserved |
| URL Structure | `/api/*`, `/websocket-tunnel`, `/app.js`, `/app.css` unchanged |
| Frontend | AngularJS 1.8 webapp — Zero modification (only build identifier format) |
| Resource Service | `ResourceServlet` + 304 caching — Behavior consistent |
| MyBatis Mappers | All SQL preserved, organized in database-specific directories |
| Schema Scripts | Same as upstream Guacamole |
