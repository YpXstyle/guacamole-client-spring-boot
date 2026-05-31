# 扩展开发指南

Guacamole Spring Boot 扩展系统的工作原理以及如何创建新扩展。

## 目录

- [扩展架构](#扩展架构)
- [扩展发现机制](#扩展发现机制)
- [创建新扩展（7 步）](#创建新扩展7-步)
- [guac-manifest.json 参考](#guac-manifestjson-参考)
- [AutoConfiguration 模式](#autoconfiguration-模式)
- [AuthenticationProvider 模式](#authenticationprovider-模式)
- [前端资源](#前端资源)
- [翻译文件](#翻译文件)
- [扩展压缩](#扩展压缩)

---

## 扩展架构

每个扩展是一个标准的 Spring Boot Starter——一个自包含的 JAR，当 Maven 依赖存在且 `enabled` 为 `true` 时自动配置。

```
┌─────────────────────────────────────────┐
│           扩展 Starter JAR              │
│  ┌───────────────────────────────────┐  │
│  │  guac-manifest.json               │  │  ← 声明认证提供者、资源
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │  AutoConfiguration 类             │  │  ← @Bean 工厂方法
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │  AutoConfiguration.imports        │  │  ← Spring Boot 注册
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │  AuthenticationProvider 实现      │  │  ← @Service Bean
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │  JS / CSS / HTML / 翻译文件       │  │  ← 前端资源
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### 与原项目（Guice）的差异

| 方面 | 原项目 (Guice) | Spring Boot |
|------|---------------|-------------|
| 注册方式 | `META-INF/services/org.apache.guacamole.GuacamoleExtension` | `AutoConfiguration.imports` |
| 加载方式 | `GuacamoleExtensionLoader`（ServiceLoader） | Spring Bean 扫描 |
| DI | Guice `@Inject` / `FactoryModuleBuilder` | Spring `@Autowired` / `@Bean` |
| 配置 | `guacamole.properties` | `application.yml` + `@ConditionalOnProperty` |
| 打包 | 扩展 `.jar` 放入 `GUACAMOLE_HOME/extensions/` | Maven 依赖在 classpath 上 |

---

## 扩展发现机制

### 1. Manifest 扫描

`ExtensionResourceConfig` 扫描 classpath 下所有 `guac-manifest.json`：

```java
resolver.getResources("classpath*:guac-manifest.json");
```

### 2. 属性过滤

每个 manifest 声明一个 `configProperty`，仅当对应属性为 `true` 时扩展才激活：

```json
{
    "configProperty": "guacamole.auth.ldap.enabled"
}
```

```yaml
guacamole:
  auth:
    ldap:
      enabled: true    # ← 扩展激活
```

### 3. AutoConfiguration 注册

Spring Boot 加载 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
org.apache.guacamole.auth.ldap.LDAPAuthenticationAutoConfiguration
```

### 4. Bean 自动收集

Spring 自动将所有 `AuthenticationProvider` Bean 收集到 `List<AuthenticationProvider>` 中，Guacamole 的 `ProviderFactory` 用它来构建认证链。

---

## 创建新扩展（7 步）

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

### 第 2 步：创建 guac-manifest.json

```json
{
    "guacamoleVersion": "1.5.5",

    "name": "My Provider 认证",
    "namespace": "myprovider",

    "configProperty": "guacamole.auth.myprovider.enabled",

    "authProviders": [
        "org.apache.guacamole.auth.myprovider.MyProviderAuthenticationProvider"
    ],

    "js": [
        "myprovider.min.js"
    ],

    "css": [
        "myprovider.min.css"
    ],

    "html": [
        "html/myprovider-config.html"
    ],

    "translations": [
        "translations/en.json",
        "translations/zh.json"
    ],

    "resources": {
        "images/logo.png": "image/png"
    }
}
```

### 第 3 步：创建 AuthenticationProvider

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

### 第 4 步：创建 AutoConfiguration

```java
package org.apache.guacamole.auth.myprovider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "guacamole.auth.myprovider", name = "enabled", havingValue = "true")
public class MyProviderAuthenticationAutoConfiguration {

    private static final Logger logger =
        LoggerFactory.getLogger(MyProviderAuthenticationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MyProviderAuthenticationProvider myProviderAuthenticationProvider() {
        logger.info("My Provider 认证扩展已启用。");
        return new MyProviderAuthenticationProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public MyProviderService myProviderService() {
        return new MyProviderService();
    }
}
```

### 第 5 步：注册 AutoConfiguration

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：
```
org.apache.guacamole.auth.myprovider.MyProviderAuthenticationAutoConfiguration
```

### 第 6 步：添加到根 POM

```xml
<!-- 根 pom.xml <modules> 中 -->
<module>extensions/guacamole-auth-myprovider-starter</module>

<!-- 根 pom.xml <dependencyManagement> 中 -->
<dependency>
    <groupId>com.right</groupId>
    <artifactId>guacamole-auth-myprovider-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 第 7 步：添加到 Web 应用依赖

```xml
<!-- guacamole/pom.xml 中 -->
<dependency>
    <groupId>com.right</groupId>
    <artifactId>guacamole-auth-myprovider-starter</artifactId>
</dependency>
```

### 第 8 步：添加配置属性

```yaml
# application.yml 中
guacamole:
  auth:
    myprovider:
      enabled: false
      myprovider-url: https://myprovider.example.com
      myprovider-timeout: 30
```

---

## guac-manifest.json 参考

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `guacamoleVersion` | String | 是 | 扩展 API 版本（应与 `guacamole.version` 属性一致） |
| `name` | String | 是 | 可读的扩展名称 |
| `namespace` | String | 是 | 唯一命名空间，用于静态资源（`/app/ext/{namespace}/`） |
| `configProperty` | String | 否 | Spring 属性键，值为 `true` 时扩展才加载 |
| `authProviders` | String[] | 否 | `AuthenticationProvider` 实现的完全限定类名 |
| `js` | String[] | 否 | JavaScript 文件路径（相对于 classpath 根） |
| `css` | String[] | 否 | CSS 文件路径（相对于 classpath 根） |
| `html` | String[] | 否 | HTML 补丁文件路径 |
| `translations` | String[] | 否 | 翻译 JSON 文件路径 |
| `resources` | Object | 否 | 静态资源路径 → MIME 类型映射 |
| `smallIcon` | String | 否 | 64×64 图标路径（覆盖默认 logo） |
| `largeIcon` | String | 否 | 144×144 图标路径（覆盖默认 logo） |

---

## AutoConfiguration 模式

### 核心注解

```java
@Configuration                              // Spring 配置类
@ConditionalOnProperty(                     // 仅在...时激活
    prefix = "guacamole.auth.myprovider",   // ...此前缀...
    name = "enabled",                       // ...此键...
    havingValue = "true")                   // ...值为 "true"
@ConditionalOnMissingBean                   // 不覆盖用户自定义 Bean
public class MyAutoConfiguration {
    @Bean
    public MyProvider provider() { ... }
}
```

### Bean 命名

对于共享服务，使用限定名称避免模块间冲突：

```java
@Bean("myProviderConfigurationService")
public ConfigurationService configService() { ... }
```

### Prototype 作用域 Bean

对于每次请求需要新实例的 Bean（如 `UserContext` 子类）：

```java
@Bean("myProviderUserContext")
@Scope("prototype")
public MyProviderUserContext userContext() { ... }
```

---

## AuthenticationProvider 模式

### 简单 Provider

无复杂依赖的 Provider 直接使用 `@Autowired`：

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

### 委托到 Service

推荐将 `AuthenticationProvider`（身份）与 `AuthenticationProviderService`（行为）分离：

```
AuthenticationProvider → getIdentifier() + 委托
AuthenticationProviderService → 实际认证逻辑 + UserContext 创建
```

这样多个 Provider 可以共享同一个 Service 实现，仅标识符不同。

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

Manifest 中列出的 HTML 文件通过 `PatchResourceService` 注入到 DOM 中。可以用于添加菜单项、设置面板或连接编辑器标签页。

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

- 键前缀必须匹配已知的模块/命名空间
- `SECTION_HEADER_*`：区域标题
- `FIELD_HEADER_*`：表单字段标签
- `ACTION_*`：按钮标签
- `HELP_*`：帮助文本
- `INFO_*`：信息提示

### 添加语言

1. 创建 `translations/{lang}.json`
2. 添加到 `guac-manifest.json` 的 `translations` 数组中
3. 确认翻译控制器支持该语言

---

## 扩展压缩

含有 JS/CSS 资源的扩展使用 `minify-maven-plugin` + Google Closure Compiler（与上游 Guacamole 一致）：

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

插件在 `target/classes/` 中生成 `myprovider.min.js`。manifest 应引用 `.min.*` 文件。

**注意：** 如果多个扩展共享一个 CSS 文件（如 sso-base 的 `sso-providers.css`），压缩配置必须放在拥有源文件的模块中，而不是消费模块中。
