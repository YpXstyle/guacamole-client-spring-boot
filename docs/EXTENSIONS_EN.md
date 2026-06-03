# Extension Development Guide

[← Back to Documentation](../README.md#documentation)

Complete development guide for the Guacamole Spring Boot extension system. Covers extension architecture, detailed steps for creating new extensions, manifest file reference, and best practices.

## Table of Contents

- [Extension Architecture](#extension-architecture)
- [Extension Discovery Mechanism](#extension-discovery-mechanism)
- [Creating a New Extension (8-Step Tutorial)](#creating-a-new-extension-8-step-tutorial)
- [guac-manifest.json Complete Field Reference](#guac-manifestjson-complete-field-reference)
- [AutoConfiguration Pattern](#autoconfiguration-pattern)
- [AuthenticationProvider Pattern](#authenticationprovider-pattern)
- [Frontend Resources](#frontend-resources)
- [Translation Files](#translation-files)
- [Extension Minification (minify-maven-plugin)](#extension-minification-minify-maven-plugin)
- [Existing Extensions Quick Reference](#existing-extensions-quick-reference)
- [LDAP Schema Reference](#ldap-schema-reference)

---

## Extension Architecture

### Overview

Each extension is a standard **Spring Boot Starter** — a self-contained JAR that auto-configures when the Maven dependency is present on the classpath and the configuration property `enabled` is `true`.

Extensions no longer need to be placed in the `GUACAMOLE_HOME/extensions/` directory. They are introduced via Maven dependencies, and Spring Boot's `AutoConfiguration` mechanism automatically discovers and loads them.

### Extension Structure

```
┌──────────────────────────────────────────────┐
│             Extension Starter JAR             │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  guac-manifest.json                    │  │  ← Declares auth provider, resources
│  └────────────────────────────────────────┘  │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  AutoConfiguration Class               │  │  ← @Configuration + @Bean
│  └────────────────────────────────────────┘  │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  AutoConfiguration.imports             │  │  ← Spring Boot registration file
│  └────────────────────────────────────────┘  │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  AuthenticationProvider Implementation │  │  ← Authentication logic
│  └────────────────────────────────────────┘  │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  JS / CSS / HTML / Translation Files   │  │  ← Frontend resources
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

### Differences from the Original Project

| Aspect | Original Project (Guice) | Spring Boot (This Project) |
|--------|-------------------------|---------------------------|
| Registration | `META-INF/services/org.apache.guacamole.GuacamoleExtension` | `AutoConfiguration.imports` |
| Loading | `GuacamoleExtensionLoader` (ServiceLoader) | Spring Bean auto-scanning |
| DI | Guice `@Inject` / `FactoryModuleBuilder` | Spring `@Autowired` / `@Bean` |
| Config Source | `guacamole.properties` | `application.yml` + `@ConditionalOnProperty` |
| Deployment | Extension `.jar` in `GUACAMOLE_HOME/extensions/` | Maven dependency on classpath |
| Module Discovery | Explicit ServiceLoader iteration | Spring auto-collects `List<AuthenticationProvider>` |

---

## Extension Discovery Mechanism

### 4-Step Loading Flow

#### Step 1: AutoConfiguration Registration

Spring Boot reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` on the classpath to discover all AutoConfiguration classes.

File content example (`guacamole-auth-header-starter`):
```
org.apache.guacamole.auth.header.HTTPHeaderAuthenticationAutoConfiguration
```

#### Step 2: Conditional Activation

Each AutoConfiguration class uses `@ConditionalOnProperty` to check if the extension is enabled:

```java
@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.header", name = "enabled", havingValue = "true")
public class HTTPHeaderAuthenticationAutoConfiguration { ... }
```

Corresponding `application.yml` configuration:
```yaml
guacamole:
  auth:
    header:
      enabled: true
```

#### Step 3: Bean Auto-Collection

Spring automatically collects all `AuthenticationProvider` beans into `List<AuthenticationProvider>`. `CoreServicesConfig` explicitly notes not to define explicit `@Bean` for these lists, as it would override auto-collection.

```java
// Comment in CoreServicesConfig:
// List<AuthenticationProvider> and List<Listener> are auto-collected by Spring
// from all AuthenticationProvider/Listener beans in the application context.
// Do NOT define explicit @Bean for these lists - it would override auto-collection.
```

#### Step 4: Extension Resource Loading

`ExtensionResourceConfig` scans `guac-manifest.json` on the classpath:

```java
resolver.getResources("classpath*:guac-manifest.json");
```

Each manifest is parsed and filtered by the `configProperty` field. Enabled extensions are used to build:
- Concatenated JS (`/app.js`) and CSS (`/app.css`)
- Static resource cache (`/app/ext/{namespace}/**`)
- HTML patch list (`/api/patches`)
- Translation files (`/translations/{lang}.json`)
- Icon overrides (`smallIcon`, `largeIcon`)

---

## Creating a New Extension (8-Step Tutorial)

This tutorial creates a hypothetical authentication extension called "My Provider", demonstrating the complete extension development flow.

### Step 1: Create Module Directory Structure

```
extensions/
└── guacamole-auth-myprovider-starter/
    ├── pom.xml
    └── src/main/
        ├── java/org/apache/guacamole/auth/myprovider/
        │   ├── MyProviderAuthenticationProvider.java
        │   ├── MyProviderAuthenticationAutoConfiguration.java
        │   └── conf/
        │       └── ConfigurationService.java
        └── resources/
            ├── guac-manifest.json
            ├── META-INF/spring/
            │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
            └── translations/
                ├── en.json
                └── zh.json
```

### Step 2: Create pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                            http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.right</groupId>
        <artifactId>guacamole-client-spring-boot</artifactId>
        <version>${revision}</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>guacamole-auth-myprovider-starter</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.right</groupId>
            <artifactId>guacamole-ext</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

Key points:
- `parent` must be the root POM (inherits plugins and dependency management)
- Core dependency is `guacamole-ext` (extension API)
- Version numbers don't need explicit specification (inherited from root POM's `<dependencyManagement>`)

### Step 3: Create guac-manifest.json

`src/main/resources/guac-manifest.json`:

```json
{
    "guacamoleVersion": "1.5.5",
    "name": "My Provider Authentication Extension",
    "namespace": "myprovider",
    "configProperty": "guacamole.auth.myprovider.enabled",
    "authProviders": [
        "org.apache.guacamole.auth.myprovider.MyProviderAuthenticationProvider"
    ],
    "js": ["myprovider.min.js"],
    "css": ["myprovider.min.css"],
    "html": ["html/myprovider-config.html"],
    "translations": ["translations/en.json", "translations/zh.json"],
    "resources": {
        "html/myprovider-config.html": "text/html"
    }
}
```

### Step 4: Create AuthenticationProvider

`MyProviderAuthenticationProvider.java`:

```java
package org.apache.guacamole.auth.myprovider;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.UserContext;
import org.springframework.beans.factory.annotation.Autowired;

public class MyProviderAuthenticationProvider extends AbstractAuthenticationProvider {

    @Autowired
    private MyProviderService providerService;

    @Override
    public String getIdentifier() {
        return "myprovider";
    }

    @Override
    public AuthenticatedUser authenticateUser(Credentials credentials)
            throws GuacamoleException {
        return providerService.authenticateUser(this, credentials);
    }

    @Override
    public UserContext getUserContext(AuthenticatedUser authenticatedUser)
            throws GuacamoleException {
        return providerService.getUserContext(this, authenticatedUser);
    }

    @Override
    public UserContext updateUserContext(UserContext context,
            AuthenticatedUser authenticatedUser, Credentials credentials)
            throws GuacamoleException {
        return providerService.updateUserContext(this, context,
                authenticatedUser, credentials);
    }
}
```

Key points:
- Extends `AbstractAuthenticationProvider` (zero abstract methods)
- Provides unique `getIdentifier()` return value (used for REST routing and data source identification)
- Uses `@Autowired` for service dependency injection
- Delegates authentication logic to `AuthenticationProviderService`

### Step 5: Create AutoConfiguration

`MyProviderAuthenticationAutoConfiguration.java`:

```java
package org.apache.guacamole.auth.myprovider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    prefix = "guacamole.auth.myprovider",
    name = "enabled",
    havingValue = "true"
)
public class MyProviderAuthenticationAutoConfiguration {

    private static final Logger logger =
        LoggerFactory.getLogger(MyProviderAuthenticationAutoConfiguration.class);

    @Bean("myProviderService")
    public MyProviderService myProviderService() {
        return new MyProviderService();
    }

    @Bean("myProviderAuthenticationProvider")
    public MyProviderAuthenticationProvider myProviderAuthenticationProvider() {
        logger.info("My Provider authentication extension enabled.");
        return new MyProviderAuthenticationProvider();
    }
}
```

Key points:
- `@Configuration` + `@ConditionalOnProperty` — loads only when configuration is enabled
- Use qualified names for shared services (e.g., `@Bean("myProviderService")`) to avoid inter-module conflicts

### Step 6: Register AutoConfiguration

Create file `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
org.apache.guacamole.auth.myprovider.MyProviderAuthenticationAutoConfiguration
```

This is the discovery file used by Spring Boot 3.x. In Spring Boot 2.x, this file was at `META-INF/spring.factories`.

### Step 7: Add to Root POM

**Add to root `pom.xml`'s `<modules>`:**
```xml
<module>extensions/guacamole-auth-myprovider-starter</module>
```

**Add to root `pom.xml`'s `<dependencyManagement>`:**
```xml
<dependency>
    <groupId>com.right</groupId>
    <artifactId>guacamole-auth-myprovider-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 8: Add to Web Application

**Add dependency in `guacamole/pom.xml`:**
```xml
<dependency>
    <groupId>com.right</groupId>
    <artifactId>guacamole-auth-myprovider-starter</artifactId>
</dependency>
```

**Add configuration in `application.yml`:**
```yaml
guacamole:
  auth:
    myprovider:
      enabled: false
      myprovider-url: https://myprovider.example.com
```

---

## guac-manifest.json Complete Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `guacamoleVersion` | String | Yes | Extension API version (should match `guacamole.version`, currently `1.5.5`) |
| `name` | String | Yes | Human-readable extension name |
| `namespace` | String | Yes | Unique namespace for static resources (`/app/ext/{namespace}/`) |
| `configProperty` | String | No | Spring config property key; extension loads only when value is `true` |
| `authProviders` | String[] | No | Fully qualified class names of `AuthenticationProvider` implementations |
| `js` | String[] | No | JavaScript file paths (relative to classpath root) |
| `css` | String[] | No | CSS file paths (relative to classpath root) |
| `html` | String[] | No | HTML patch file paths |
| `translations` | String[] | No | Translation JSON file paths |
| `resources` | Object | No | Static resource path to MIME type mapping |
| `smallIcon` | String | No | 64x64 icon path (overrides default logo-64.png) |
| `largeIcon` | String | No | 144x144 icon path (overrides default logo-144.png) |

### Field Details

**guacamoleVersion:** Must match the `guacamole-ext` API version used at runtime. Current project version is `1.5.5`.

**namespace:** Must be unique. Used to build static resource URLs (`/app/ext/{namespace}/...`). Also the basis for translation loader to identify module areas.

**configProperty:** Control property for enabling/disabling the extension. Extension loads when the property value is `true`:

```json
{ "configProperty": "guacamole.auth.ldap.enabled" }
```

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
```

If this field is omitted, the extension always loads (unconditionally).

**resources:** Keys are resource paths, values are MIME types. These resources are served via `/app/ext/{namespace}/{path}` and pre-loaded into memory cache at startup:

```json
{
    "resources": {
        "templates/authenticationCodeField.html": "text/html",
        "images/logo.png": "image/png"
    }
}
```

---

## AutoConfiguration Pattern

### Core Annotations

```java
@Configuration                              // Spring configuration class
@ConditionalOnProperty(                     // Activate only when...
    prefix = "guacamole.auth.myprovider",   // ...this prefix...
    name = "enabled",                       // ...this property...
    havingValue = "true")                   // ...has value "true"
public class MyAutoConfiguration {
    // ...
}
```

### Bean Naming Strategy

Use qualified bean names for shared services to avoid inter-module conflicts:

```java
@Bean("myProviderConfigurationService")
public ConfigurationService configService() { ... }

@Bean("headerConfigurationService")
public ConfigurationService headerConfigService() { ... }
```

This pattern is widely used in existing extensions (Header Auth: `headerConfigurationService`; LDAP: `ldapConfigurationService`).

### Prototype-Scoped Beans

For beans that need new instances per request (e.g., `AuthenticatedUser`, `UserContext` subclasses):

```java
@Bean
@Scope("prototype")
public LDAPAuthenticatedUser ldapAuthenticatedUser() {
    return new LDAPAuthenticatedUser();
}

@Bean
@Scope("prototype")
public LDAPUserContext ldapUserContext() {
    return new LDAPUserContext();
}
```

### Existing Extension AutoConfiguration Examples

**HTTP Header Auth** (`HTTPHeaderAuthenticationAutoConfiguration`):
- Registers `headerConfigurationService` (`ConfigurationService`)
- Registers `headerAuthenticationProviderService` (`AuthenticationProviderService`)
- Registers `@Scope("prototype")` `headerAuthenticatedUser` (`AuthenticatedUser`)
- Registers `headerAuthenticationProvider` (`HTTPHeaderAuthenticationProvider`)

**LDAP Auth** (`LDAPAuthenticationAutoConfiguration`):
- Registers 7 `@Bean`s: `ldapConfigurationService`, `ldapConnectionService`, `ldapLDAPConnectionService`, `ldapObjectQueryService`, `ldapUserGroupService`, `ldapUserService`, `ldapAuthenticationProviderService`
- Registers `ldapAuthenticationProvider`
- Registers `@Scope("prototype")` `ldapAuthenticatedUser` and `ldapUserContext`

---

## AuthenticationProvider Pattern

### Simple Provider

Providers without complex constructor dependencies use `@Autowired` directly:

```java
public class SimpleAuthProvider extends AbstractAuthenticationProvider {
    @Autowired
    private SimpleAuthService authService;

    @Override
    public String getIdentifier() { return "simple"; }

    @Override
    public AuthenticatedUser authenticateUser(Credentials credentials)
            throws GuacamoleException {
        return authService.authenticateUser(this, credentials);
    }

    @Override
    public UserContext getUserContext(AuthenticatedUser user)
            throws GuacamoleException {
        return authService.getUserContext(this, user);
    }

    @Override
    public UserContext updateUserContext(UserContext ctx,
            AuthenticatedUser user, Credentials cred)
            throws GuacamoleException {
        return authService.updateUserContext(this, ctx, user, cred);
    }
}
```

### Constructor Injection Provider

When a Provider needs to receive dependencies at creation time, use constructor injection (e.g., `HTTPHeaderAuthenticationProvider`):

```java
public class HTTPHeaderAuthenticationProvider extends AbstractAuthenticationProvider {
    private final AuthenticationProviderService authProviderService;

    public HTTPHeaderAuthenticationProvider(
            AuthenticationProviderService authProviderService) {
        this.authProviderService = authProviderService;
    }

    @Override
    public String getIdentifier() { return "header"; }

    @Override
    public AuthenticatedUser authenticateUser(Credentials credentials)
            throws GuacamoleException {
        return authProviderService.authenticateUser(credentials);
    }
}
```

Corresponding AutoConfiguration:

```java
@Bean("headerAuthenticationProvider")
public HTTPHeaderAuthenticationProvider headerAuthenticationProvider(
        AuthenticationProviderService authProviderService) {
    return new HTTPHeaderAuthenticationProvider(authProviderService);
}
```

### AuthenticationProvider → Service Delegation Pattern

It is recommended to separate `AuthenticationProvider` (identity) from `AuthenticationProviderService` (behavior logic):

```
AuthenticationProvider
  → getIdentifier() returns unique identifier
  → Delegates authenticateUser/getUserContext/updateUserContext to Service

AuthenticationProviderService
  → Actual authentication logic
  → UserContext creation and configuration
  → Can be shared by multiple Providers (different identifiers)
```

### Provider Authentication Chain Behavior

All AuthenticationProvider beans are auto-collected into `List<AuthenticationProvider>`. During login:
1. Iterate each Provider in order
2. Call `provider.authenticateUser(credentials)`
3. First to return non-null wins
4. If all Providers return null, return credentials exception

---

## Frontend Resources

### JavaScript Structure

Extension JS should follow the AngularJS module pattern:

```javascript
angular.module('myExtension', ['rest', 'auth'])
    .controller('myExtensionController', ['$scope', function($scope) {
        // Extension logic
    }]);
```

AngularJS module dependency chain:
```
index → auth → rest → myExtension
```

### CSS Structure

Extension CSS loads **after** webapp CSS, so styles can be overridden:

```css
/* Override Guacamole styles */
.my-extension-class {
    background: #f0f0f0;
}
```

### HTML Patches

HTML files listed in the manifest are injected into the DOM via `PatchResourceService`:

```json
{
    "html": ["html/myprovider-config.html"]
}
```

Can be used for:
- Adding menu items
- Adding settings panels
- Adding connection editor tabs
- Adding authentication fields (e.g., TOTP, Duo)

### Static Resources

Resources defined in the manifest's `resources` field are served via `/app/ext/{namespace}/{path}`. All resources are pre-loaded into memory cache at startup for efficient runtime access:

```json
{
    "resources": {
        "templates/authenticationCodeField.html": "text/html",
        "styles/extra.css": "text/css"
    }
}
```

### Extension Resource Cache

All extension static resources are pre-loaded into `Map<String, CachedResourceData>` at startup. The cache key is `namespace/resourcePath`, and the value is a byte array and MIME type. At runtime, resources are looked up and returned from cache via `CachedExtensionResourceResolver` without reading the classpath.

---

## Translation Files

### Format

```json
{
    "MY_MODULE": {
        "SECTION_HEADER": "My Module",
        "ACTION_SAVE": "Save Configuration"
    }
}
```

### Naming Convention

| Prefix | Purpose | Example |
|--------|---------|---------|
| `SECTION_HEADER_*` | Section titles | `SECTION_HEADER_MYPROVIDER` |
| `FIELD_HEADER_*` | Form field labels | `FIELD_HEADER_MYPROVIDER_URL` |
| `ACTION_*` | Button labels | `ACTION_CONNECT` |
| `HELP_*` | Help text | `HELP_MYPROVIDER_URL` |
| `INFO_*` | Informational messages | `INFO_CONNECTED` |
| `ERROR_*` | Error messages | `ERROR_AUTH_FAILED` |
| `TEXT_*` | General text | `TEXT_WELCOME` |

### Adding a New Language

1. Create `translations/{lang}.json`
2. Add to `guac-manifest.json`'s `translations` array
3. Ensure `TranslationController` supports the language

### Translation Loading Mechanism

Translation files are loaded by `LanguageConfig` at startup. It uses area filtering to ensure only enabled extensions' translations are loaded. Translation key-value pairs are registered with `LanguageResourceService`, and served at runtime by `TranslationController`.

### Existing Extension Translation Files

Reference existing extension translation configurations:

- **TOTP Extension:** Supports 9 languages (ca, de, en, fr, ja, ko, pt, ru, zh)
- **Duo Extension:** Supports 9 languages
- **OpenID Extension:** Supports 9 languages + HTML interface
- **JSON Extension:** Supports 2 languages (en, zh), no JS/CSS resources
- **Vault KSM:** Supports 1 language (en), no frontend resources
- **History:** Supports 2 languages (en, zh), no frontend resources

---

## Extension Minification (minify-maven-plugin)

### Configuration

Extensions with JS/CSS resources use `minify-maven-plugin` + Google Closure Compiler (consistent with upstream Guacamole). The plugin executes during the `prepare-package` phase.

```xml
<plugin>
    <groupId>com.github.buckelieg</groupId>
    <artifactId>minify-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>default-minify</id>
            <configuration>
                <webappSourceDir>${basedir}/src/main/resources</webappSourceDir>
                <webappTargetDir>${project.build.directory}/classes</webappTargetDir>
                <jsSourceDir>/</jsSourceDir>
                <jsTargetDir>/</jsTargetDir>
                <jsFinalFile>myprovider.js</jsFinalFile>
                <jsSourceIncludes>
                    <jsSourceInclude>**/*.js</jsSourceInclude>
                </jsSourceIncludes>
                <jsEngine>CLOSURE</jsEngine>
            </configuration>
            <goals>
                <goal>minify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### How It Works

1. Plugin's `webappSourceDir` points to `src/main/resources/`
2. Reads source files (e.g., `myprovider.js`)
3. Minifies using Google Closure Compiler
4. Outputs `myprovider.min.js` to `target/classes/` (inside JAR)
5. Manifest references `.min.*` files (minified versions)

### Notes

- **Main application minification:** Main webapp's JS is minified by TerserPlugin in the webpack build; this plugin is **only for extension modules**
- **Shared CSS files:** If multiple extensions share a CSS file (e.g., SSO's `sso-providers.css`), the minification config must be in the module that owns the source file, not the consuming module
- **Engine:** `jsEngine` set to `CLOSURE` (Google Closure Compiler), consistent with the original project

---

## Existing Extensions Quick Reference

The following table is verified against actual code:

| Extension | Module Name | Config Prefix | Frontend Resources | Supported Languages |
|-----------|-------------|--------------|-------------------|-------------------|
| MySQL JDBC | `guacamole-auth-mysql-starter` | `guacamole.auth.mysql` | None | - |
| PostgreSQL JDBC | `guacamole-auth-postgresql-starter` | `guacamole.auth.postgresql` | None | - |
| SQL Server JDBC | `guacamole-auth-sqlserver-starter` | `guacamole.auth.sqlserver` | None | - |
| HTTP Header | `guacamole-auth-header-starter` | `guacamole.auth.header` | None | - |
| JSON | `guacamole-auth-json-starter` | `guacamole.auth.json` | None | en, zh |
| LDAP | `guacamole-auth-ldap-starter` | `guacamole.auth.ldap` | None | - |
| RADIUS | `guacamole-auth-radius-starter` | `guacamole.auth.radius` | None | - |
| TOTP | `guacamole-auth-totp-starter` | `guacamole.auth.totp` | JS + CSS + HTML | 9 languages |
| Duo | `guacamole-auth-duo-starter` | `guacamole.auth.duo` | JS + CSS + HTML | 9 languages |
| QuickConnect | `guacamole-auth-quickconnect-starter` | `guacamole.auth.quickconnect` | None | - |
| SSO CAS | `guacamole-auth-sso-cas-starter` | `guacamole.auth.sso-cas` | (via sso-base) | - |
| SSO OpenID | `guacamole-auth-sso-openid-starter` | `guacamole.auth.sso-openid` | JS + CSS + HTML | 9 languages |
| SSO SAML | `guacamole-auth-sso-saml-starter` | `guacamole.auth.sso-saml` | (via sso-base) | - |
| Vault KSM | `guacamole-vault-ksm-starter` | `guacamole.vault.ksm` | None | en |
| History | `guacamole-history-starter` | `guacamole.history` | None | en, zh |

### Module Mutual Exclusion Rules

**JDBC Mutual Exclusion:** MySQL, PostgreSQL, SQL Server — only one can be enabled, enforced by `EnvironmentConfig.validateModules()` at startup.

**SSO Mutual Exclusion:** CAS, OpenID, SAML — only one can be enabled, also enforced by `EnvironmentConfig.validateModules()`.

---

## LDAP Schema Reference

The LDAP authentication extension supports storing Guacamole connection configurations in an LDAP directory. This requires loading a custom schema on the LDAP server.

### OID Definitions

```
attributetype ( 1.3.6.1.4.1.38971.1.1.1
    NAME 'guacConfigProtocol'
    SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )

attributetype ( 1.3.6.1.4.1.38971.1.1.2
    NAME 'guacConfigParameter'
    SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )

objectClass ( 1.3.6.1.4.1.38971.1.2.1
    NAME 'guacConfigGroup'
    DESC 'Guacamole configuration group'
    SUP groupOfNames
    MUST guacConfigProtocol
    MAY guacConfigParameter )
```

| Attribute/Class | OID | Type | Description |
|----------------|-----|------|-------------|
| `guacConfigProtocol` | 1.3.6.1.4.1.38971.1.1.1 | STRING | Connection protocol (e.g., `vnc`, `rdp`, `ssh`) |
| `guacConfigParameter` | 1.3.6.1.4.1.38971.1.1.2 | STRING | Guacamole connection parameter (`key=value` format) |
| `guacConfigGroup` | 1.3.6.1.4.1.38971.1.2.1 | objectClass | Inherits `groupOfNames`, represents a set of Guacamole connection configurations |

### LDIF Example

```ldif
dn: cn=Example Config,dc=guac-dev,dc=org
objectClass: guacConfigGroup
objectClass: groupOfNames
cn: Example Config
guacConfigProtocol: vnc
guacConfigParameter: hostname=localhost
guacConfigParameter: port=5900
guacConfigParameter: password=secret
member: cn=user1,dc=example,dc=com
member: cn=user2,dc=example,dc=com
seeAlso: cn=admins,ou=groups,dc=example,dc=com
```

The value of `guacConfigParameter` is in `key=value` format. Available parameters depend on the protocol specified by `guacConfigProtocol` — see the corresponding `protocols/*.json` definitions in `guacamole-ext`.
