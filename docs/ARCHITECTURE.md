# 系统架构

Guacamole Spring Boot 系统架构深入分析。

## 目录

- [总体架构](#总体架构)
- [请求流程](#请求流程)
- [核心组件](#核心组件)
- [扩展系统](#扩展系统)
- [资源服务](#资源服务)
- [WebSocket 隧道](#websocket-隧道)
- [REST API 层](#rest-api-层)
- [认证流程](#认证流程)
- [属性解析](#属性解析)
- [从 Guice 迁移](#从-guice-迁移)

---

## 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         浏览器                                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────────┐  │
│  │ REST API │  │WebSocket │  │ 静态资源  │  │ 扩展资源        │  │
│  │(/api/*)  │  │(/ws-tunnel)│ │(/app/*) │  │(/app/ext/*)    │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └───────┬────────┘  │
└───────┼─────────────┼─────────────┼────────────────┼────────────┘
        │             │             │                │
┌───────▼─────────────▼─────────────▼────────────────▼────────────┐
│                    Spring Boot 应用                              │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Jersey Servlet 容器 (/api/*)                               │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐   │ │
│  │  │SessionResource│ │TunnelResource│ │UserContextResource│  │ │
│  │  └─────────────┘  └─────────────┘  └──────────────────┘   │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Resource Servlet 资源服务                                   │ │
│  │  /app.js  /app.css  /images/logo-*.png                     │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ 核心服务 (Spring Bean)                                      │ │
│  │  Environment  TokenSessionMap  AuthTokenGenerator          │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ 扩展 AutoConfiguration 类                                   │ │
│  │  HeaderAuth  JSONAuth  JdbcAuth  LdapAuth  SSO  ...       │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ WebSocket 端点 (/websocket-tunnel)                          │ │
│  │  GuacamoleWebSocketEndpoint → GuacamoleTunnel               │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────┬───────────────────────────────────────┘
                           │ Guacamole 协议 (TCP)
┌──────────────────────────▼───────────────────────────────────────┐
│                      guacd (C 守护进程)                          │
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────────┐         │
│  │ RDP  │  │ VNC  │  │ SSH  │  │Telnet│  │Kubernetes│         │
│  └──────┘  └──────┘  └──────┘  └──────┘  └──────────┘         │
└──────────────────────────────────────────────────────────────────┘
```

---

## 请求流程

### REST API 请求

```
浏览器 → HTTP GET /api/session/data/mysql/connections
  → Jersey 容器
    → SessionResource.getUserContextResource()
      → UserContextResource.getConnectionDirectoryResource()
        → ConnectionDirectoryResource.getObjectResource("5")
          → DirectoryResource → ConnectionResource
            → JSON 序列化 → HTTP 200
```

### 隧道请求

```
浏览器 → HTTP POST /api/session/tunnels {connection: "5"}
  → TunnelCollectionResource.createTunnel()
    → TunnelRequestService.createTunnel()
      → guacd 连接 (TCP :4822)
        → TunnelResource (含 UUID)
          → 浏览器连接 WebSocket /websocket-tunnel?uuid=...
            → GuacamoleWebSocketEndpoint
              → 双向 guacamole 协议流
```

### 静态资源请求

```
浏览器 → GET /app.js
  → ResourceServlet
    → ByteArrayResource（启动时预拼接）
      → HTTP 200 + ETag → 浏览器缓存（304 支持）
```

### 扩展资源请求

```
浏览器 → GET /app/ext/totp/templates/authenticationCodeField.html
  → CachedExtensionResourceResolver
    → 预加载的缓存 Map<namespace/path, byte[]>
      → HTTP 200
```

---

## 核心组件

### GuacamoleSpringBootApplication

应用入口。配置组件扫描时**排除**扩展包（扩展由其自身的 AutoConfiguration 加载）：

```java
@ComponentScan(
    basePackages = "org.apache.guacamole",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "org\\.apache\\.guacamole\\.(auth\\..*|vault\\..*|history\\..*)"
    )
)
```

### EnvironmentConfig

将 Spring `Environment` 桥接到 Guacamole `LocalEnvironment`：

```
application.yml
  → Spring Environment (PropertySource)
    → EnvironmentConfig.guacamoleEnvironment()
      → GuacamoleProperties 适配器
        → LocalEnvironment.getInstance()
          → JDBCEnvironment.getProperty("mysql-hostname")
```

桥接层同时处理扁平的 Guacamole 属性名和带命名空间的 Spring 属性路径：

```
"mysql-hostname"
  → 精确匹配：null
  → 尝试 "guacamole.auth.mysql.mysql-hostname" → 找到！
```

### JerseyConfig

配置 Jersey JAX-RS 容器，映射路径 `/api/*`：

```java
@Configuration
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {
    // 扫描的包：
    packages("org.apache.guacamole.rest");     // 核心 REST
    packages("org.apache.guacamole.auth.sso"); // SSO REST 资源
    // 条件扫描：packages("org.apache.guacamole.auth.saml");
}
```

### CoreServicesConfig

注册核心单例服务：
- `TokenSessionMap` — 认证 Token 到 `GuacamoleSession` 的映射
- `AuthTokenGenerator` — 生成加密安全的认证 Token
- `FileAuthenticationProvider` — 默认基于文件的认证（回退方案）

### ResourceFactoryConfig

用 Spring 风格的 Lambda 工厂替代 Guice 的 `FactoryModuleBuilder`：

```java
@Bean
public DirectoryObjectResourceFactory<Connection, APIConnection>
        connectionResourceFactory(ConnectionObjectTranslator translator) {
    return (parent, userContext, directory) ->
        autowire(new ConnectionResource(parent, userContext, directory, translator));
}
```

`autowire()` 辅助方法调用 `AutowireCapableBeanFactory.autowireBean()`，为通过 `new` 创建的对象注入 `@Autowired` 字段。

---

## 扩展系统

### 加载机制

```
1. Spring Boot 启动
2. 在 classpath 上发现 AutoConfiguration.imports 文件
3. 加载每个 AutoConfiguration 类
4. @ConditionalOnProperty 检查 → enabled?
5. @Bean 方法执行 → 创建 Provider/Service 实例
6. Spring 自动收集 AuthenticationProvider Bean → List<AuthenticationProvider>
7. ProviderFactory 将每个 Provider 包装为 AuthenticationProviderFacade
8. ExtensionResourceConfig 扫描 classpath 中的 guac-manifest.json
9. 加载静态资源、翻译文件、HTML 补丁
```

### guac-manifest.json 处理

`ExtensionResourceConfig.extensionManifests()` 扫描 classpath：

```java
resolver.getResources("classpath*:guac-manifest.json");
```

每个 manifest 被解析，按 `configProperty` 过滤，然后用于：
- 构建拼接的 `/app.js`（全部扩展 JS 合并）
- 构建拼接的 `/app.css`（全部扩展 CSS 合并）
- 预加载静态资源（`/app/ext/{namespace}/*`）
- 收集 HTML 补丁（`/api/patches`）
- 识别图标覆盖

### 认证链组装

```java
// ProviderFactory 中（REST API 处理时调用）：
List<AuthenticationProviderFacade> providers = new ArrayList<>();
for (AuthenticationProvider provider : authProviders) {  // 由 Spring 自动收集
    providers.add(new AuthenticationProviderFacade(provider));
}
```

---

## 资源服务

### /app.js 和 /app.css

由 `ExtensionResourceConfig` 在启动时构建：
1. 从 classpath 读取 `verifyCachedVersion.js`
2. 读取每个 manifest 的 `js` 数组
3. 全部拼接为一个 `String` → `ByteArrayResource`
4. 注册 `ResourceServlet`（支持 304 / If-None-Match / ETag）

### /app/ext/{namespace}/{path}

启动时预加载到内存缓存：
```
Map<String, byte[]> cache:
  "totp/templates/authenticationCodeField.html" → byte[]
  "duo/styles/duo.css" → byte[]
  ...
```

通过 `CachedExtensionResourceResolver`（Spring ResourceResolver）提供服务，拦截 `/app/ext/**` 请求并从预加载缓存中返回。

### /translations/{lang}.json

由 `TranslationController`（Spring `@RestController`）提供服务，委托给 `LanguageResourceService`。

### /images/logo-*.png

由 `ResourceServlet` 提供服务，支持扩展覆盖。最后一个扩展的 `smallIcon`/`largeIcon` 生效。

---

## WebSocket 隧道

### 端点注册

`WebSocketConfig` 在 Tomcat 的 `WsServerContainer` 上注册端点：

```java
ServerEndpointConfig config = ServerEndpointConfig.Builder
    .create(GuacamoleWebSocketEndpoint.class, "/websocket-tunnel")
    .subprotocols(Collections.singletonList("guacamole"))
    .build();
container.addEndpoint(config);
```

### 数据流

```
浏览器 WebSocket
  ↔ GuacamoleWebSocketEndpoint (JSR-356 @ServerEndpoint)
    ↔ GuacamoleTunnel (guacamole-common)
      ↔ TunnelRequestService
        ↔ guacd TCP 套接字 (端口 4822)
          ↔ 远程桌面协议
```

`TunnelRequestService` 在 `@PostConstruct` 中通过静态注入设置，因为 JSR-356 的 `@ServerEndpoint` 类由容器实例化，不由 Spring 管理。

---

## REST API 层

### 资源层次

```
/api/
├── session/                                  SessionResource
│   ├── data/{source}/                        UserContextResource
│   │   ├── connections/                      ConnectionDirectoryResource
│   │   │   └── {id}/                         ConnectionResource
│   │   │       ├── parameters                GET 连接参数
│   │   │       ├── history                   ConnectionHistoryResource
│   │   │       └── sharingProfiles/          SharingProfileDirectoryResource
│   │   │           └── {id}/                 SharingProfileResource
│   │   ├── connectionGroups/                 ConnectionGroupDirectoryResource
│   │   │   └── {id}/                         ConnectionGroupResource
│   │   │       └── tree                      GET 连接树
│   │   ├── users/                            UserDirectoryResource
│   │   │   └── {id}/                         UserResource
│   │   │       ├── password                   PUT 修改密码
│   │   │       ├── permissions               PermissionSetResource
│   │   │       ├── effectivePermissions      GET 有效权限
│   │   │       └── userGroups                RelatedObjectSetResource
│   │   ├── userGroups/                       UserGroupDirectoryResource
│   │   ├── sharingProfiles/                  SharingProfileDirectoryResource
│   │   ├── activeConnections/                ActiveConnectionDirectoryResource
│   │   ├── history/                          HistoryResource
│   │   └── schema/                           SchemaResource
│   ├── tunnels/                              TunnelCollectionResource
│   │   └── {uuid}/                           TunnelResource
│   │       ├── activeConnection/             ActiveConnectionResource
│   │       │   └── connection/               ConnectionResource
│   │       │       └── sharingProfiles/      ← Share 按钮数据源
│   │       ├── protocol                      GET 隧道协议
│   │       └── streams/{index}/{filename}    GET 流数据
│   └── ext/{source}/                         扩展专属 REST
└── patches/                                  PatchRESTService
```

### 子资源定位器模式

Jersey 子资源定位器模式在无需显式注册的情况下串接资源：

```java
@Path("connection")
public ConnectionResource getConnection() {
    return (ConnectionResource) factory.create(...).getObjectResource(id);
}
// Jersey 自动发现 ConnectionResource 的 @Path("sharingProfiles") 方法
```

工厂创建的资源通过 `autowireBean()` 进行 Spring DI。Jersey 使用运行时类型（`getClass()`）进行子资源方法发现。

---

## 认证流程

### 登录

```
1. POST /api/tokens {username, password}
2. AuthTokenGenerator → 生成 Token
3. 遍历每个 AuthenticationProvider：
   a. provider.authenticateUser(credentials)
   b. 成功 → 创建 GuacamoleSession → 存入 TokenSessionMap
   c. 失败 → 尝试下一个 Provider
4. 返回 {authToken, username, availableDataSources}
```

### 已认证请求

```
1. 请求携带 ?token=xxx 参数
2. TokenSessionMap.get(token) → GuacamoleSession
3. Session 绑定到 Jersey 请求上下文
4. 以已认证用户上下文处理请求
```

### 共享密钥认证

```
1. GET /#/?token=share-key-xxx
2. verifyCachedVersion.js 校验构建标识符
3. index.html 加载，Angular 应用启动
4. 遍历每个 AuthenticationProvider：
   a. 如果是 SharedAuthenticationProvider → 尝试验证 share key
   b. SharedAuthenticationProviderService.retrieveSharedConnectionUser()
   c. 有效 → SharedUserContext（限制只能访问共享连接）
```

---

## 属性解析

### 解析链

```
1. Spring Environment (application.yml, 环境变量, -D 属性)
     ↓
2. EnvironmentConfig.guacamoleEnvironment()
   → GuacamoleProperties 适配器
   → LocalEnvironment
     ↓
3. JDBCEnvironment（JDBC 扩展使用）
   → DelegatingEnvironment
   → getProperty("mysql-hostname")
     → LocalEnvironment.getProperty()
       → GuacamoleProperties 适配器
         → Spring Environment.getProperty("mysql-hostname") → null
         → Spring Environment.getProperty("guacamole.auth.mysql.mysql-hostname") → 找到！
```

### 支持的属性命名空间

`EnvironmentConfig` 桥接检查以下前缀：

```
guacamole.auth.mysql
guacamole.auth.postgresql
guacamole.auth.sqlserver
guacamole.auth.header
guacamole.auth.duo
guacamole.auth.json
guacamole.auth.ldap
guacamole.auth.totp
guacamole.auth.radius
guacamole.auth.quickconnect
guacamole.auth.sso-cas
guacamole.auth.sso-openid
guacamole.auth.sso-saml
guacamole.vault.ksm
guacamole.history
```

---

## 从 Guice 迁移

### 变更项

| 层次 | Guice | Spring Boot |
|------|-------|-------------|
| 应用入口 | `GuacamoleServletContextListener` | `@SpringBootApplication` |
| 模块加载 | `AbstractModule` 子类 | `@Configuration` + `@Bean` |
| DI 注解 | `@Inject`、`@Singleton` | `@Autowired`，默认单例 |
| 工厂模式 | `FactoryModuleBuilder` | Lambda `DirectoryResourceFactory` |
| REST 注册 | Guice-HK2 桥接 | Jersey 自动扫描（`packages()`） |
| Filter 注册 | Guice `filter()` | `FilterRegistrationBean` / `@Bean` |
| WebSocket | 每种容器一个 `WebSocketTunnelModule` | `WebSocketConfig` + JSR 356 |
| Servlet 上下文 | `ServletContext` 监听器 | Spring `ServletContextInitializer` |
| 扩展加载 | `GuacamoleExtensionLoader`（ServiceLoader） | `ResourcePatternResolver` classpath 扫描 |
| 配置 | `GUACAMOLE_HOME/guacamole.properties` | `application.yml` + Spring Environment |

### 不变项

| 层次 | 保持不变 |
|------|---------|
| 协议层 | `guacamole-common`（56 个文件）— 零改动 |
| 扩展 API | `guacamole-ext`（160 个文件）— 仅 javax → jakarta |
| JavaScript API | `guacamole-common-js`（37 个文件）— 零改动 |
| REST 注解 | 全部 `@Path`、`@GET`、`@POST` 保留 |
| URL 结构 | `/api/*`、`/websocket-tunnel`、`/app.js`、`/app.css` 不变 |
| 前端 | AngularJS 1.8.3 webapp — 除标识符格式外零改动 |
| 资源服务 | `ResourceServlet` + 304 缓存 — 完全一致 |
| MyBatis 映射器 | 全部 SQL 保留，按数据库类型组织到独立目录 |
| 建表脚本 | 与上游完全一致 |
