# 扩展开发指南

[← 返回文档索引](../README_zh.md#documentation-index)

Guacamole Spring Boot 扩展系统的完整开发指南。涵盖扩展架构、创建新扩展的详细步骤、清单文件参考和最佳实践。

## 目录

- [扩展架构](#扩展架构)
- [扩展发现机制](#扩展发现机制)
- [创建新扩展（8 步完整教程）](#创建新扩展8-步完整教程)
- [guac-manifest.json 完整字段参考](#guac-manifestjson-完整字段参考)
- [AutoConfiguration 模式](#autoconfiguration-模式)
- [AuthenticationProvider 模式](#authenticationprovider-模式)
- [前端资源](#前端资源)
- [翻译文件](#翻译文件)
- [扩展压缩（minify-maven-plugin）](#扩展压缩minify-maven-plugin)
- [现有扩展快速参考](#现有扩展快速参考)
- [LDAP Schema 参考](#ldap-schema-参考)

---

## 扩展架构

### 概述

每个扩展是一个标准的 **Spring Boot Starter**——一个自包含的 JAR，当 Maven 依赖存在于 classpath 上且配置属性 `enabled` 为 `true` 时自动配置。

扩展不再需要放入 `GUACAMOLE_HOME/extensions/` 目录。它们通过 Maven 依赖引入，Spring Boot 的 `AutoConfiguration` 机制自动发现和加载。

### 扩展结构

```
┌──────────────────────────────────────────────┐
│             扩展 Starter JAR                  │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  guac-manifest.json                    │  │  ← 声明认证提供者、资源
│  └────────────────────────────────────────┘  │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  AutoConfiguration 类                   │  │  ← @Configuration + @Bean
│  └────────────────────────────────────────┘  │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  AutoConfiguration.imports             │  │  ← Spring Boot 注册文件
│  └────────────────────────────────────────┘  │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  AuthenticationProvider 实现            │  │  ← 认证逻辑
│  └────────────────────────────────────────┘  │
│                                                │
│  ┌────────────────────────────────────────┐  │
│  │  JS / CSS / HTML / 翻译文件            │  │  ← 前端资源
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

### 与原始项目的差异

| 方面 | 原始项目（Guice） | Spring Boot（本项目） |
|------|-------------------|---------------------|
| 注册方式 | `META-INF/services/org.apache.guacamole.GuacamoleExtension` | `AutoConfiguration.imports` |
| 加载方式 | `GuacamoleExtensionLoader`（ServiceLoader） | Spring Bean 自动扫描 |
| DI 方式 | Guice `@Inject` / `FactoryModuleBuilder` | Spring `@Autowired` / `@Bean` |
| 配置来源 | `guacamole.properties` | `application.yml` + `@ConditionalOnProperty` |
| 部署方式 | 扩展 `.jar` 放入 `GUACAMOLE_HOME/extensions/` | Maven 依赖在 classpath 上 |
| 模块发现 | 显式 ServiceLoader 迭代 | Spring 自动收集 `List<AuthenticationProvider>` |

---

## 扩展发现机制

### 4 步加载流程

#### 第 1 步：AutoConfiguration 注册

Spring Boot 读取 classpath 上的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件，发现所有 AutoConfiguration 类。

文件内容示例（`guacamole-auth-header-starter`）：
```
org.apache.guacamole.auth.header.HTTPHeaderAuthenticationAutoConfiguration
```

#### 第 2 步：条件激活

每个 AutoConfiguration 类使用 `@ConditionalOnProperty` 检查扩展是否启用：

```java
@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.header", name = "enabled", havingValue = "true")
public class HTTPHeaderAuthenticationAutoConfiguration { ... }
```

对应的 `application.yml` 配置：
```yaml
guacamole:
  auth:
    header:
      enabled: true
```

#### 第 3 步：Bean 自动收集

Spring 自动将所有 `AuthenticationProvider` Bean 收集到 `List<AuthenticationProvider>` 中。`CoreServicesConfig` 中明确说明了不要为这些列表定义显式 `@Bean`，否则会覆盖自动收集。

```java
// CoreServicesConfig 中的注释：
// List<AuthenticationProvider> and List<Listener> are auto-collected by Spring
// from all AuthenticationProvider/Listener beans in the application context.
// Do NOT define explicit @Bean for these lists - it would override auto-collection.
```

#### 第 4 步：扩展资源加载

`ExtensionResourceConfig` 扫描 classpath 上的 `guac-manifest.json`：

```java
resolver.getResources("classpath*:guac-manifest.json");
```

每个 manifest 被解析后，按 `configProperty` 字段过滤。启用的扩展用于构建：
- 串联的 JS（`/app.js`）和 CSS（`/app.css`）
- 静态资源缓存（`/app/ext/{namespace}/**`）
- HTML 补丁列表（`/api/patches`）
- 翻译文件（`/translations/{lang}.json`）
- 图标覆盖（`smallIcon`、`largeIcon`）

---

## 创建新扩展（8 步完整教程）

本教程创建一个名为 "My Provider" 的假设认证扩展，展示完整的扩展开发流程。

### 第 1 步：创建模块目录结构

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

### 第 2 步：创建 pom.xml

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

关键点：
- `parent` 必须是根 POM（继承插件和依赖管理）
- 核心依赖是 `guacamole-ext`（扩展 API）
- 版本号无需显式指定（继承自根 POM 的 `<dependencyManagement>`）

### 第 3 步：创建 guac-manifest.json

`src/main/resources/guac-manifest.json`：

```json
{
    "guacamoleVersion": "1.5.5",
    "name": "My Provider 认证扩展",
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

### 第 4 步：创建 AuthenticationProvider

`MyProviderAuthenticationProvider.java`：

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

关键点：
- 继承 `AbstractAuthenticationProvider`（零抽象方法）
- 提供唯一的 `getIdentifier()` 返回值（用于 REST 路由和数据源标识）
- 使用 `@Autowired` 注入服务依赖
- 将认证逻辑委托给 `AuthenticationProviderService`

### 第 5 步：创建 AutoConfiguration

`MyProviderAuthenticationAutoConfiguration.java`：

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
        logger.info("My Provider 认证扩展已启用。");
        return new MyProviderAuthenticationProvider();
    }
}
```

关键点：
- `@Configuration` + `@ConditionalOnProperty` —— 仅在配置启用时加载
- 为共享服务使用限定名称（如 `@Bean("myProviderService")`）避免模块间冲突

### 第 6 步：注册 AutoConfiguration

创建文件 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
org.apache.guacamole.auth.myprovider.MyProviderAuthenticationAutoConfiguration
```

这是 Spring Boot 3.x 使用的发现文件。在 Spring Boot 2.x 中，此文件位于 `META-INF/spring.factories`。

### 第 7 步：添加到根 POM

**在根 `pom.xml` 的 `<modules>` 中添加：**
```xml
<module>extensions/guacamole-auth-myprovider-starter</module>
```

**在根 `pom.xml` 的 `<dependencyManagement>` 中添加：**
```xml
<dependency>
    <groupId>com.right</groupId>
    <artifactId>guacamole-auth-myprovider-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 第 8 步：添加到 Web 应用

**在 `guacamole/pom.xml` 中添加依赖：**
```xml
<dependency>
    <groupId>com.right</groupId>
    <artifactId>guacamole-auth-myprovider-starter</artifactId>
</dependency>
```

**在 `application.yml` 中添加配置：**
```yaml
guacamole:
  auth:
    myprovider:
      enabled: false
      myprovider-url: https://myprovider.example.com
```

---

## guac-manifest.json 完整字段参考

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `guacamoleVersion` | String | 是 | 扩展 API 版本（应与 `guacamole.version` 一致，当前 `1.5.5`） |
| `name` | String | 是 | 可读的扩展名称 |
| `namespace` | String | 是 | 唯一命名空间，用于静态资源（`/app/ext/{namespace}/`） |
| `configProperty` | String | 否 | Spring 配置属性键，值为 `true` 时扩展才加载 |
| `authProviders` | String[] | 否 | `AuthenticationProvider` 实现的完全限定类名数组 |
| `js` | String[] | 否 | JavaScript 文件路径数组（相对于 classpath 根） |
| `css` | String[] | 否 | CSS 文件路径数组（相对于 classpath 根） |
| `html` | String[] | 否 | HTML 补丁文件路径数组 |
| `translations` | String[] | 否 | 翻译 JSON 文件路径数组 |
| `resources` | Object | 否 | 静态资源路径到 MIME 类型的映射 |
| `smallIcon` | String | 否 | 64x64 图标路径（覆盖默认 logo-64.png） |
| `largeIcon` | String | 否 | 144x144 图标路径（覆盖默认 logo-144.png） |

### 字段详解

**guacamoleVersion：** 必须与运行时使用的 `guacamole-ext` API 版本匹配。当前项目版本为 `1.5.5`。

**namespace：** 必须是唯一的。用于构建静态资源 URL（`/app/ext/{namespace}/...`）。同时也是翻译加载器识别模块区域的依据。

**configProperty：** 启用/禁用扩展的控制属性。当属性值为 `true` 时扩展加载：

```json
{ "configProperty": "guacamole.auth.ldap.enabled" }
```

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
```

如果省略此字段，扩展始终加载（无条件）。

**resources：** 键是资源路径，值是 MIME 类型。这些资源通过 `/app/ext/{namespace}/{path}` 提供服务，并在启动时预加载到内存缓存中。

```json
{
    "resources": {
        "templates/authenticationCodeField.html": "text/html",
        "images/logo.png": "image/png"
    }
}
```

---

## AutoConfiguration 模式

### 核心注解

```java
@Configuration                              // Spring 配置类
@ConditionalOnProperty(                     // 仅在...时激活
    prefix = "guacamole.auth.myprovider",   // ...此前缀...
    name = "enabled",                       // ...此属性...
    havingValue = "true")                   // ...值为 "true"
public class MyAutoConfiguration {
    // ...
}
```

### Bean 命名策略

为共享服务使用限定 Bean 名称，避免模块间冲突：

```java
@Bean("myProviderConfigurationService")
public ConfigurationService configService() { ... }

@Bean("headerConfigurationService")
public ConfigurationService headerConfigService() { ... }
```

此模式在现有扩展中广泛应用（Header Auth：`headerConfigurationService`；LDAP：`ldapConfigurationService`）。

### Prototype 作用域 Bean

对于需要每次请求新实例的 Bean（如 `AuthenticatedUser`、`UserContext` 子类）：

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

### 现有扩展的 AutoConfiguration 示例

**HTTP Header Auth**（`HTTPHeaderAuthenticationAutoConfiguration`）：
- 注册 `headerConfigurationService`（`ConfigurationService`）
- 注册 `headerAuthenticationProviderService`（`AuthenticationProviderService`）
- 注册 `@Scope("prototype")` 的 `headerAuthenticatedUser`（`AuthenticatedUser`）
- 注册 `headerAuthenticationProvider`（`HTTPHeaderAuthenticationProvider`）

**LDAP Auth**（`LDAPAuthenticationAutoConfiguration`）：
- 注册 7 个 `@Bean`：`ldapConfigurationService`、`ldapConnectionService`、`ldapLDAPConnectionService`、`ldapObjectQueryService`、`ldapUserGroupService`、`ldapUserService`、`ldapAuthenticationProviderService`
- 注册 `ldapAuthenticationProvider`
- 注册 `@Scope("prototype")` 的 `ldapAuthenticatedUser` 和 `ldapUserContext`

---

## AuthenticationProvider 模式

### 简单 Provider

无复杂构造依赖的 Provider 直接使用 `@Autowired`：

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

### 构造注入 Provider

当 Provider 需要在创建时接收依赖，使用构造注入（如 `HTTPHeaderAuthenticationProvider`）：

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

对应的 AutoConfiguration：

```java
@Bean("headerAuthenticationProvider")
public HTTPHeaderAuthenticationProvider headerAuthenticationProvider(
        AuthenticationProviderService authProviderService) {
    return new HTTPHeaderAuthenticationProvider(authProviderService);
}
```

### AuthenticationProvider → Service 委托模式

推荐将 `AuthenticationProvider`（身份标识）与 `AuthenticationProviderService`（行为逻辑）分离：

```
AuthenticationProvider
  → getIdentifier() 返回唯一标识符
  → 将 authenticateUser/getUserContext/updateUserContext 委托给 Service

AuthenticationProviderService
  → 实际认证逻辑
  → UserContext 创建和配置
  → 可被多个 Provider 共享（不同标识符）
```

### Provider 认证链行为

所有 AuthenticationProvider Bean 被自动收集到 `List<AuthenticationProvider>`。在登录时：
1. 按顺序遍历每个 Provider
2. 调用 `provider.authenticateUser(credentials)`
3. 第一个返回非 null 的 Provider 成功
4. 若所有 Provider 都返回 null，返回凭据异常

---

## 前端资源

### JavaScript 结构

扩展 JS 应遵循 AngularJS 模块模式：

```javascript
angular.module('myExtension', ['rest', 'auth'])
    .controller('myExtensionController', ['$scope', function($scope) {
        // 扩展逻辑
    }]);
```

AngularJS 模块依赖链：
```
index → auth → rest → myExtension
```

### CSS 结构

扩展 CSS 在 webapp CSS **之后**加载，可以覆盖样式：

```css
/* 覆盖 Guacamole 样式 */
.my-extension-class {
    background: #f0f0f0;
}
```

### HTML 补丁

Manifest 中列出的 HTML 文件通过 `PatchResourceService` 注入到 DOM 中：

```json
{
    "html": ["html/myprovider-config.html"]
}
```

可以用于：
- 添加菜单项
- 添加设置面板
- 添加连接编辑器标签页
- 添加认证字段（如 TOTP、Duo）

### 静态资源

Manifest 中 `resources` 字段定义的资源通过 `/app/ext/{namespace}/{path}` 提供服务。所有资源在启动时被预加载到内存缓存中，支持高效的运行时访问：

```json
{
    "resources": {
        "templates/authenticationCodeField.html": "text/html",
        "styles/extra.css": "text/css"
    }
}
```

### 扩展资源缓存

所有扩展静态资源在启动时预加载到 `Map<String, CachedResourceData>` 中。缓存的键是 `namespace/resourcePath`，值是字节数组和 MIME 类型。运行时通过 `CachedExtensionResourceResolver` 从缓存中查找并返回，无需读取类路径。

---

## 翻译文件

### 格式

```json
{
    "MY_MODULE": {
        "SECTION_HEADER": "我的模块",
        "ACTION_SAVE": "保存配置"
    }
}
```

### 命名规范

| 前缀 | 用途 | 示例 |
|------|------|------|
| `SECTION_HEADER_*` | 区域标题 | `SECTION_HEADER_MYPROVIDER` |
| `FIELD_HEADER_*` | 表单字段标签 | `FIELD_HEADER_MYPROVIDER_URL` |
| `ACTION_*` | 按钮标签 | `ACTION_CONNECT` |
| `HELP_*` | 帮助文本 | `HELP_MYPROVIDER_URL` |
| `INFO_*` | 信息提示 | `INFO_CONNECTED` |
| `ERROR_*` | 错误信息 | `ERROR_AUTH_FAILED` |
| `TEXT_*` | 普通文本 | `TEXT_WELCOME` |

### 添加新语言

1. 创建 `translations/{lang}.json`
2. 添加到 `guac-manifest.json` 的 `translations` 数组
3. 确保 `TranslationController` 已支持该语言

### 翻译加载机制

翻译文件由 `LanguageConfig` 在启动时加载。它通过区域过滤确保只有启用扩展的翻译被加载。翻译键值对注册到 `LanguageResourceService`，运行时由 `TranslationController` 服务。

### 现有扩展的翻译文件

参考现有扩展的翻译配置：

- **TOTP 扩展：** 支持 9 种语言（ca、de、en、fr、ja、ko、pt、ru、zh）
- **Duo 扩展：** 支持 9 种语言
- **OpenID 扩展：** 支持 9 种语言 + HTML 界面
- **JSON 扩展：** 支持 2 种语言（en、zh），无 JS/CSS 资源
- **Vault KSM：** 支持 1 种语言（en），无前端资源
- **History：** 支持 2 种语言（en、zh），无前端资源

---

## 扩展压缩（minify-maven-plugin）

### 配置

含有 JS/CSS 资源的扩展使用 `minify-maven-plugin` + Google Closure Compiler（与上游 Guacamole 一致）。插件在 `prepare-package` 阶段执行。

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

### 工作原理

1. 插件的 `webappSourceDir` 指向 `src/main/resources/`
2. 读取源文件（如 `myprovider.js`）
3. 使用 Google Closure Compiler 压缩
4. 输出 `myprovider.min.js` 到 `target/classes/`（JAR 包中）
5. manifest 引用 `.min.*` 文件（压缩后的版本）

### 注意事项

- **主应用压缩：** 主 webapp 的 JS 在 webpack 构建中由 TerserPlugin 压缩，此插件**仅用于扩展模块**
- **共享 CSS 文件：** 如果多个扩展共享一个 CSS 文件（如 SSO 的 `sso-providers.css`），压缩配置必须放在拥有源文件的模块中，而不是消费模块中
- **引擎：** `jsEngine` 设为 `CLOSURE`（Google Closure Compiler），与原始项目一致

---

## 现有扩展快速参考

以下表格基于实际代码验证：

| 扩展 | 模块名 | 配置前缀 | 前端资源 | 支持语言 |
|------|--------|----------|----------|----------|
| MySQL JDBC | `guacamole-auth-mysql-starter` | `guacamole.auth.mysql` | 无 | - |
| PostgreSQL JDBC | `guacamole-auth-postgresql-starter` | `guacamole.auth.postgresql` | 无 | - |
| SQL Server JDBC | `guacamole-auth-sqlserver-starter` | `guacamole.auth.sqlserver` | 无 | - |
| HTTP Header | `guacamole-auth-header-starter` | `guacamole.auth.header` | 无 | - |
| JSON | `guacamole-auth-json-starter` | `guacamole.auth.json` | 无 | en, zh |
| LDAP | `guacamole-auth-ldap-starter` | `guacamole.auth.ldap` | 无 | - |
| RADIUS | `guacamole-auth-radius-starter` | `guacamole.auth.radius` | 无 | - |
| TOTP | `guacamole-auth-totp-starter` | `guacamole.auth.totp` | JS + CSS + HTML | 9 种 |
| Duo | `guacamole-auth-duo-starter` | `guacamole.auth.duo` | JS + CSS + HTML | 9 种 |
| QuickConnect | `guacamole-auth-quickconnect-starter` | `guacamole.auth.quickconnect` | 无 | - |
| SSO CAS | `guacamole-auth-sso-cas-starter` | `guacamole.auth.sso-cas` | (通过 sso-base) | - |
| SSO OpenID | `guacamole-auth-sso-openid-starter` | `guacamole.auth.sso-openid` | JS + CSS + HTML | 9 种 |
| SSO SAML | `guacamole-auth-sso-saml-starter` | `guacamole.auth.sso-saml` | (通过 sso-base) | - |
| Vault KSM | `guacamole-vault-ksm-starter` | `guacamole.vault.ksm` | 无 | en |
| History | `guacamole-history-starter` | `guacamole.history` | 无 | en, zh |

### 模块互斥规则

**JDBC 互斥：** MySQL、PostgreSQL、SQL Server 三者只能启用一个，由 `EnvironmentConfig.validateModules()` 在启动时强制执行。

**SSO 互斥：** CAS、OpenID、SAML 三者只能启用一个，同样由 `EnvironmentConfig.validateModules()` 强制执行。

---

## LDAP Schema 参考

LDAP 认证扩展支持通过 LDAP 目录存储 Guacamole 连接配置。这需要在 LDAP 服务器上加载自定义 schema。

### OID 定义

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

| 属性/类 | OID | 类型 | 说明 |
|---------|-----|------|------|
| `guacConfigProtocol` | 1.3.6.1.4.1.38971.1.1.1 | STRING | 连接协议（如 `vnc`、`rdp`、`ssh`） |
| `guacConfigParameter` | 1.3.6.1.4.1.38971.1.1.2 | STRING | Guacamole 连接参数（`key=value` 格式） |
| `guacConfigGroup` | 1.3.6.1.4.1.38971.1.2.1 | objectClass | 继承 `groupOfNames`，表示一组 Guacamole 连接配置 |

### LDIF 示例

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

`guacConfigParameter` 的值是 `key=value` 格式。可用的参数取决于 `guacConfigProtocol` 指定的协议，参见 `guacamole-ext` 中各协议对应的 `protocols/*.json` 定义。
