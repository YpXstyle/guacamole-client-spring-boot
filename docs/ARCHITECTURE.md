# 系统架构

Guacamole Spring Boot 系统的深入架构分析。本文档涵盖系统组件、请求流程、认证机制和关键设计模式。

## 目录

- [总体架构](#总体架构)
- [请求流程](#请求流程)
- [核心组件详解](#核心组件详解)
- [扩展系统加载机制](#扩展系统加载机制)
- [资源服务](#资源服务)
- [WebSocket 隧道](#websocket-隧道)
- [REST API 层](#rest-api-层)
- [系统配置模块](#系统配置模块)
- [认证流程](#认证流程)
- [属性解析机制](#属性解析机制)
- [Guice 到 Spring 迁移对照](#guice-到-spring-迁移对照)

---

## 总体架构

```
+---------------------------------------------------------------------+
|                          浏览器                                       |
|  +------------+  +------------+  +------------+  +----------------+  |
|  | REST API   |  | WebSocket  |  | 静态资源    |  | 扩展资源        |  |
|  | (/api/*)   |  |(/websocket-|  | (/app.js,   |  |(/app/ext/*)    |  |
|  |            |  | /tunnel)   |  | /app.css)   |  |                |  |
|  +-----+------+  +------+-----+  +-----+------+  +-------+--------+  |
+--------+-------------+---------------+-------------------+-----------+
         |             |               |                   |
+--------v-------------v---------------v-------------------v-----------+
|                      Spring Boot 应用 (Tomcat 容器)                   |
|                                                                      |
|  +---------------------------------------------------------------+  |
|  | Jersey Servlet 容器 (/api/*)   | JacksonFeature (JSON)         |  |
|  |  +----------------+  +------------------+  +---------------+  |  |
|  |  | SessionREST    |  | TokenRESTService  |  | PatchREST     |  |  |
|  |  | Service        |  | (/api/tokens)     |  | Service       |  |  |
|  |  +-------+--------+  +------------------+  | (/api/patches) |  |  |
|  |          |                                   +---------------+  |  |
|  |  +-------v--------+                                              |  |
|  |  | SessionResource|—— sub-resource locator 模式                  |  |
|  |  |  @Path("data/  |  → UserContextResource                       |  |
|  |  |    {source}")  |    → ConnectionDirectoryResource             |  |
|  |  |                |      → ConnectionResource (CRUD + 参数 +     |  |
|  |  |                |         历史 + sharingProfiles)               |  |
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
|  | Resource Servlet (资源服务)                                       |  |
|  |  /app.js   —— 扩展 JavaScript 串联（+ verifyCachedVersion.js）   |  |
|  |  /app.css  —— 扩展 CSS 串联                                       |  |
|  |  /images/logo-64.png  —— 小型图标（扩展可覆盖）                   |  |
|  |  /images/logo-144.png —— 大型图标（扩展可覆盖）                   |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  +---------------------------------------------------------------+  |
|  | 核心服务 (Spring Beans)                                           |  |
|  |  EnvironmentConfig: 属性桥接器（16 + 1 个命名空间前缀）          |  |
|  |  CoreServicesConfig: TokenSessionMap / AuthTokenGenerator         |  |
|  |  ResourceFactoryConfig: 工厂 Bean（替换 Guice FactoryModuleBuilder）|  |
|  |  JerseyConfig: Jersey 容器配置 (/api/*)                           |  |
|  |  WebSocketConfig: JSR 356 WebSocket 端点注册                      |  |
|  |  FilterConfig: CacheRevalidationFilter + HTTP Tunnel 回退          |  |
|  |  ExtensionResourceConfig: 扩展资源加载（guac-manifest.json）       |  |
|  |  LanguageConfig: 翻译文件加载（基于区域的过滤）                     |  |
|  |  LogConfig: JUL 到 SLF4J 桥接                                      |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  +---------------------------------------------------------------+  |
|  | 扩展模块 (AutoConfiguration)                                      |  |
|  |  HeaderAuth | JSONAuth | JdbcAuth | LdapAuth | RadiusAuth       |  |
|  |  TOTP | Duo | QuickConnect | SSO (CAS/OpenID/SAML)              |  |
|  |  Vault KSM | History Recording                                   |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  +---------------------------------------------------------------+  |
|  | WebSocket 端点                                                    |  |
|  |  /websocket-tunnel (JSR-356 @ServerEndpoint)                      |  |
|  |  /tunnel (HTTP 回退, RestrictedGuacamoleHTTPTunnelServlet)       |  |
|  +---------------------------------------------------------------+  |
+----------------------------+----------------------------------------+
                             | Guacamole 协议 (TCP)
+----------------------------v----------------------------------------+
|                     guacd (C 守护进程, 端口 4822)                    |
|  +------+  +------+  +------+  +--------+  +------------+           |
|  | RDP  |  | VNC  |  | SSH  |  | Telnet |  | Kubernetes |           |
|  +------+  +------+  +------+  +--------+  +------------+           |
+----------------------------------------------------------------------+
```

---

## 请求流程

### REST API 请求

客户端通过四种方式之一提供认证 Token：`Guacamole-Token` HTTP 头、`token` 查询参数、`Authorization: Basic` 头，或首次登录时的 `POST /api/tokens` 表单参数。

```
浏览器 → HTTP GET /api/session/data/mysql/connections?token=xxx
  → Jersey 容器 (/api/* 映射)
    → TokenParamProvider 解析 Token
      → SessionRESTService.getSessionResource(token)
        → SessionResource.getUserContextResource("mysql")
          → UserContextResource.getConnectionDirectoryResource()
            → ConnectionDirectoryResource.getObjectResource("5")
              → ConnectionResource.get() → JSON 序列化 → HTTP 200
```

### 隧道请求

隧道创建分为两步：先通过 REST API 创建隧道，再建立 WebSocket 连接。

```
Step 1: REST API 创建隧道
浏览器 → HTTP POST /api/session/tunnels {"connectionIdentifier": "5"}
  → TunnelCollectionResource.createTunnel()
    → TunnelRequestService.createTunnel()
      → 用户认证 → 获取 UserContext
        → Connectable.connect(info, tokens) → guacd 连接 (TCP :4822)
          → 返回 GuacamoleTunnel (含 UUID)
            → 隧道存储在 GuacamoleSession 中

Step 2: WebSocket 连接
浏览器 → WebSocket /websocket-tunnel?uuid=...&token=...
  → GuacamoleWebSocketEndpoint.onOpen()
    → WebSocketTunnelRequest 从查询参数提取 UUID 和 Token
      → TunnelRequestService.createTunnel() 获取已有隧道
        → 读线程启动: 隧道 UUID → 从 guacd 读取指令 → 发送到 WebSocket
        → 写处理: WebSocket 消息 → 过滤器 → 写入 guacd
```

### 静态资源请求

```
浏览器 → GET /app.js
  → ResourceServlet (ServletRegistrationBean)
    → ByteArrayResource (启动时预拼接)
      → HTTP 200 + ETag → 浏览器缓存 (304 Not Modified 支持)

浏览器 → GET /app.css
  → ResourceServlet
    → ByteArrayResource (启动时预拼接)
      → HTTP 200 + ETag → 304 支持
```

### 扩展资源请求

```
浏览器 → GET /app/ext/totp/templates/authenticationCodeField.html
  → Spring ResourceHandler (优先级高于默认静态资源)
    → CachedExtensionResourceResolver
      → 从 pre-loaded cache (Map<String, CachedResourceData>) 查找
        → "totp/templates/authenticationCodeField.html" → byte[]
          → HTTP 200

浏览器 → GET /translations/zh.json
  → TranslationController (Spring @RestController)
    → LanguageResourceService → byte[] → HTTP 200 + ETag
```

---

## 核心组件详解

### GuacamoleSpringBootApplication

应用入口点。配置组件扫描以**排除**扩展包（扩展由各自的 AutoConfiguration 类加载）：

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

启动时检查 `-Duser.timezone` 系统属性并设置 JVM 默认时区。

### EnvironmentConfig

将 Spring `Environment` 桥接到 Guacamole `LocalEnvironment`。这是整个系统的属性解析中枢。

**核心方法：** `guacamoleEnvironment()` 创建一个 `@Bean`，类型为 `Environment`，标记为 `@Primary`。

**桥接器（GuacamoleProperties 适配器）：**
1. 检查精确名称匹配（如 `"ldap-hostname"`）
2. 检查遗留 guacd 属性映射（`"guacd-hostname"` → `"guacamole.guacd.hostname"`）
3. 遍历 16 个命名空间前缀尝试拼接查找（详见[属性解析机制](#属性解析机制)）

**第二桥接器：** `SystemEnvironmentGuacamoleProperties` 作为后备，允许环境变量覆盖。

**模块验证（@PostConstruct）：**
- **JDBC 互斥：** MySQL、PostgreSQL、SQL Server 三者只能启用一个
- **SSO 互斥：** CAS、OpenID、SAML 三者只能启用一个
- 违反规则时抛出 `IllegalStateException` 并列出已启用的模块

### JerseyConfig

配置 Jersey JAX-RS 容器，映射到 `/api/*`：

```java
@Configuration
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {
    // 扫描包：
    packages("org.apache.guacamole.rest");       // 核心 REST 资源
    packages("org.apache.guacamole.auth.sso");   // SSO REST 资源
    // 条件扫描：
    if (samlEnabled) → packages("org.apache.guacamole.auth.saml");
    
    // 注册 Jackson JSON 序列化
    register(JacksonFeature.class);
}
```

在构造函数中安装 `SLF4JBridgeHandler`，将 Jersey 日志（JUL）桥接到 SLF4J。

### CoreServicesConfig

注册核心单例服务：

- **TokenSessionMap**（`HashTokenSessionMap` 实现）—— 认证 Token 到 `GuacamoleSession` 的映射
- **AuthTokenGenerator**（`SecureRandomAuthTokenGenerator` 实现）—— 加密安全的 Token 生成
- **FileAuthenticationProvider** —— 默认文件认证（回退方案）
- **List\<File\>** 临时文件列表 —— 管理会话级临时文件

`AuthenticationProvider` 和 `Listener` Bean 由 Spring 自动收集，无需显式列表定义。

### ResourceFactoryConfig

替换 Guice 的 `FactoryModuleBuilder`，使用 Spring 风格 Lambda 工厂。核心辅助方法 `autowire()` 使用 `AutowireCapableBeanFactory.autowireBean()` 注入 `@Autowired` 字段。

为以下类型提供工厂 Bean：
- **简单工厂：** SessionResource、UserContextResource、TunnelCollectionResource、TunnelResource
- **目录工厂（Directory + Object）：** ActiveConnection、Connection、ConnectionGroup、SharingProfile、User、UserGroup
- **对象翻译器：** 以上所有类型的翻译器（Translator）

典型模式：

```java
@Bean
public DirectoryObjectResourceFactory<Connection, APIConnection>
        connectionResourceFactory(ConnectionObjectTranslator translator) {
    return (parent, userContext, directory) ->
        autowire(new ConnectionResource(parent, userContext, directory, translator));
}
```

### WebSocketConfig

在 Tomcat `WsServerContainer` 上注册 WebSocket 端点：

```java
ServerEndpointConfig config = ServerEndpointConfig.Builder
    .create(GuacamoleWebSocketEndpoint.class, "/websocket-tunnel")
    .subprotocols(Collections.singletonList("guacamole"))
    .build();
container.addEndpoint(config);
```

通过 `TomcatServletWebServerFactory` 和 `TomcatContextCustomizer` 在上下文启动后注册端点。`TunnelRequestService` 通过 `@PostConstruct` 时调用静态 setter 注入到 `GuacamoleWebSocketEndpoint`，因为 JSR-356 `@ServerEndpoint` 实例由容器创建而非 Spring。

### FilterConfig

注册 Servlet 过滤器：
- **CacheRevalidationFilter** —— 应用于 `/index.html`，防止部署后缓存过期
- **RestrictedGuacamoleHTTPTunnelServlet** —— 注册在 `/tunnel`，提供 WebSocket 不可用时的 HTTP 隧道回退（使用 `applicationContext.getAutowireCapableBeanFactory().autowireBean()` 手动注入依赖）

### ExtensionResourceConfig

实现 `WebMvcConfigurer`。核心职责：
1. 扫描 `classpath*:guac-manifest.json` 获取所有扩展清单
2. 根据 `configProperty` 过滤启用的扩展
3. 预加载扩展 JavaScript/CSS 到串联的字节数组（`/app.js` 和 `/app.css`）
4. 预加载扩展静态资源到内存缓存（`Map<String, CachedResourceData>`）
5. 注册 ResourceServlet Bean（ETag/304 支持）
6. 注册自定义 `CachedExtensionResourceResolver` 处理 `/app/ext/**`
7. 从扩展 HTML 补丁构建 `PatchResourceService`

**图标覆盖逻辑：** `loadExtensionIcon()` 遍历扩展清单的 `smallIcon` 和 `largeIcon` 字段，最后一个扩展的图标胜出，找不到时回退到默认图标。

### LanguageConfig

加载翻译文件（`/translations/*.json`），使用区域（area）过滤机制：

1. 扫描所有 `guac-manifest.json` 提取扩展模块区域
2. 检查 `configProperty` 判断模块是否启用 → 构建 `enabledAreas` 和 `manifestGroupAreas`
3. 从启用的区域和核心区域加载翻译，跳过禁用的扩展区域
4. 处理共享基础模块（如 JDBC base、SSO base）—— 将父区域也加入 enabled 集合

`extractModuleArea()` 方法从 URL 中提取模块区域，支持 fat JAR、嵌套 JAR 和开发目录布局。

### LogConfig

在 `@PostConstruct` 时安装 `SLF4JBridgeHandler`，在 `@PreDestroy` 时卸载。

---

## 扩展系统加载机制

### 加载流程

```
1. Spring Boot 启动
2. AutoConfiguration.imports 文件在 classpath 上被发现
3. 每个 AutoConfiguration 类被加载
4. @ConditionalOnProperty 检查 → 是否启用？
5. @Bean 方法执行 → 创建 Provider/Service 实例
6. Spring 自动收集 AuthenticationProvider Bean → List<AuthenticationProvider>
7. 认证提供者被排列到认证链中
8. ExtensionResourceConfig 扫描 classpath 查找 guac-manifest.json
9. 静态资源、翻译文件和 HTML 补丁被加载
10. SSO 扩展（CAS、OpenID、SAML）的 WebSocket 端点在启用时注册
```

### guac-manifest.json 处理

`ExtensionResourceConfig.extensionManifests()` 使用 Spring 的 `ResourcePatternResolver` 扫描：

```java
resolver.getResources("classpath*:guac-manifest.json");
```

每个清单被解析后，按 `configProperty` 过滤。启用的扩展用于：
- 构建串联的 `/app.js`（所有扩展 JS 合并）
- 构建串联的 `/app.css`（所有扩展 CSS 合并）
- 预加载静态资源（`/app/ext/{namespace}/*`）
- 收集 HTML 补丁（`/api/patches`）
- 识别图标覆盖（`smallIcon`、`largeIcon`）

### 扩展静态资源缓存

所有扩展静态资源在启动时预加载到内存缓存：

```
Map<String, CachedResourceData>:
  "totp/templates/authenticationCodeField.html" → byte[] + MIME类型
  "duo/styles/duo.css" → byte[] + MIME类型
  ...
```

缓存使用 `ClassLoader.getResources()` 填充，正确处理 Spring Boot fat JAR 中的嵌套 JAR。通过自定义的 `CachedExtensionResourceResolver`（继承 `AbstractResourceResolver`）拦截 `/app/ext/**` 请求提供服务。

### 翻译区域过滤

`LanguageConfig` 实现复杂的区域过滤，确保只加载启用扩展的翻译文件。关键点：
- 同一模块组内的翻译共享区域
- 共享基础模块（如 `guacamole-auth-jdbc-base`）通过父区域匹配继承
- `areaMatches()` 使用精确匹配或前缀+斜杠边界匹配防止误匹配

---

## 资源服务

### /app.js 和 /app.css

由 `ExtensionResourceConfig` 在启动时构建：
1. 读取 `verifyCachedVersion.js` 从 classpath（首先预置）
2. 读取每个清单的 `js`/`css` 数组条目
3. 全部拼接为单个 `String` → `ByteArrayResource`
4. 注册 `ResourceServlet`（支持 304 / If-None-Match / ETag）

### /app/ext/{namespace}/{path}

通过预加载缓存经由 `CachedExtensionResourceResolver` 提供服务，注册在 Spring 资源处理器链中，优先级高于默认静态资源处理器。

### /translations/{lang}.json

由 `LanguageConfig` 在 `@PostConstruct` 时加载并注册到 `LanguageResourceService`。`TranslationController`（Spring `@RestController`）在运行时提供翻译文件。

### /images/logo-*.png

由专用的 `ResourceServlet` Bean 提供服务。扩展清单可以通过 `smallIcon` 和 `largeIcon` 字段覆盖（最后一个扩展的图标胜出）。回退到核心 webapp 的 `static/images/logo-64.png` 和 `static/images/logo-144.png`。

---

## WebSocket 隧道

### 端点注册

`WebSocketConfig` 在 Tomcat `WsServerContainer` 上注册 `GuacamoleWebSocketEndpoint` 在 `/websocket-tunnel`。端点支持 `guacamole` 子协议。

### 数据流

```
浏览器 WebSocket
  ↔ GuacamoleWebSocketEndpoint (JSR-356 @ServerEndpoint)
    ↔ GuacamoleTunnel (guacamole-common 库)
      ↔ TunnelRequestService
        ↔ guacd TCP 套接字 (端口 4822)
          ↔ 远程桌面协议 (RDP/VNC/SSH/...)
```

### 读线程

在 WebSocket 打开时启动一个守护读线程：
1. 发送隧道 UUID 作为内部指令（空操作码 + UUID 参数）
2. 从隧道读取 Guacamole 协议指令
3. 输出缓冲，最大 `BUFFER_SIZE`（8192 字符），减少 WebSocket 消息数量
4. 将缓冲数据作为 WebSocket 文本消息发送
5. 连接关闭时通过 Guacamole 状态码优雅处理

### Ping/Pong

`@OnMessage` 处理器实现连接稳定性检测。隧道内部指令（由 `GuacamoleTunnel.INTERNAL_DATA_OPCODE` 标识）被过滤，不会传递给 guacd。`ping` 请求返回带有相同关联 ID 的 `ping` 响应。

### 错误处理

- **@OnError：** 记录错误日志并关闭关联隧道
- **@OnClose：** 关闭关联隧道（清理）
- **连接错误：** Guacamole 状态码映射到 WebSocket 关闭码
- **TunnelRequestService：** 捕获 `GuacamoleUnauthorizedException` 时自动失效对应会话

### HTTP 隧道回退

`RestrictedGuacamoleHTTPTunnelServlet` 注册在 `/tunnel`，当 WebSocket 不可用时（如代理限制）提供 HTTP 长轮询回退方案。

---

## REST API 层

### 资源层次树

```
/api/
+-- tokens/                                       TokenRESTService
|   +-- POST   —— 创建 Token（登录，无需认证）
|   +-- DELETE /{token} —— 失效 Token（注销）
+-- session/                                      SessionRESTService
|   +-- GET    —— 获取 SessionResource
|   +-- DELETE —— 注销当前会话
|   +-- data/{source}/                            SessionResource → UserContextResource
|   |   +-- self/                                 当前用户信息
|   |   +-- connections/                          连接目录
|   |   |   +-- {id}/                             连接资源
|   |   |       +-- parameters                    获取连接参数
|   |   |       +-- history                       连接使用历史
|   |   |       +-- sharingProfiles/              连接关联的共享配置
|   |   +-- connectionGroups/                     连接组目录
|   |   |   +-- {id}/                             连接组资源
|   |   |       +-- tree                          获取连接组树
|   |   +-- users/                                用户目录
|   |   |   +-- {username}/                       用户资源
|   |   |       +-- password                      修改密码
|   |   |       +-- permissions                   获取/更新用户权限
|   |   |       +-- effectivePermissions          获取有效权限
|   |   |       +-- userGroups                    用户所属组管理
|   |   +-- userGroups/                           用户组目录
|   |   |   +-- {id}/                             用户组资源
|   |   |       +-- permissions                   用户组权限
|   |   |       +-- memberUsers                   组成员用户管理
|   |   |       +-- memberUserGroups              组成员组管理
|   |   +-- sharingProfiles/                      共享配置文件目录
|   |   |   +-- {id}/                             共享配置文件资源
|   |   +-- activeConnections/                    活跃连接目录
|   |   |   +-- {id}/                             活跃连接资源
|   |   |       +-- connection                    连接详情
|   |   |       +-- connection/sharingProfiles    共享配置
|   |   +-- history/                              历史记录
|   |   |   +-- connections                       连接历史
|   |   |   +-- users                             用户登录历史
|   |   |   +-- active                            当前活跃会话
|   |   +-- schema/                               属性 Schema
|   |       +-- userAttributes                    用户属性定义
|   |       +-- userGroupAttributes               用户组属性定义
|   |       +-- connectionAttributes              连接属性定义
|   |       +-- connectionGroupAttributes         连接组属性定义
|   |       +-- sharingProfileAttributes           共享配置属性定义
|   |       +-- protocols                         协议信息
|   +-- tunnels/                                  隧道集合
|   |   +-- GET    —— 获取所有活跃隧道 UUID
|   |   +-- POST   —— 创建新隧道
|   |   +-- {uuid}/                               TunnelResource
|   |       +-- GET    —— 获取隧道状态
|   |       +-- activeConnection/                 活跃连接资源
|   |       |   +-- connection                    连接详情
|   |       |   +-- connection/sharingProfiles    共享配置
|   |       +-- protocol                          隧道协议
|   |       +-- streams/{index}/{filename}        流数据传输
|   +-- ext/{source}/                             扩展特定 REST 端点
+-- patches/                                      扩展 HTML 补丁
+-- languages/                                    语言列表
+-- /translations/{lang}.json                     翻译 JSON 文件
```

### 子资源定位器模式

Jersey 的子资源定位器模式允许资源链式访问而无需显式注册。核心机制是 `@Path` 注解方法返回子资源实例，Jersey 的运行时类型发现（`getClass()`）自动发现子资源的 `@Path` 方法。

```java
// SessionResource 中的子资源定位器
@Path("data/{dataSource}")
public UserContextResource getUserContextResource(@PathParam("dataSource") String id) {
    // Jersey 自动发现 UserContextResource 的 @Path("connections") 等方法
    return userContextResourceFactory.create(userContext);
}
```

资源通过工厂创建后，使用 `autowireBean()` 注入 `@Autowired` 字段。

### REST 服务

- **TokenRESTService**（`/api/tokens`）：支持 HTTP Basic Auth 和表单参数认证。`POST` 创建令牌，`DELETE /{token}` 失效令牌。
- **SessionRESTService**（`/api/session`）：入口点，通过 `@TokenParam` 注入 Token，路由到 `SessionResource`。
- **PatchRESTService**（`/api/patches`）：提供扩展 HTML 补丁列表。
- **SettingsRESTService**（`/api/settings`）：系统配置管理，`GET` 返回 25 条配置，`PUT/{key}` 更新单条（需 `SYSTEM_ADMINISTER`）。
- **ConfigRESTService**（`/api/config`）：公开配置端点，无需认证，返回品牌/主题/公告配置。
- **FileRESTService**（`/api/settings/files`）：文件上传/下载/删除，`POST` 上传（multipart），`GET/{id}` 公开获取，`DELETE/{id}` 管理员删除。
- **ExtensionRESTService**（`/api/ext/{identifier}`）：提供扩展自定义 REST 资源。

---

## 系统配置模块

### 数据流

```
管理员在系统配置页面修改品牌/主题/安全/公告
  ↓ $http PUT /api/settings/{key} + Guacamole-Token
SettingsRESTService (Jersey JAX-RS)
  ↓ verifyAdminPermission() → SYSTEM_ADMINISTER
SystemConfigService.updateValue()
  ↓ SystemConfigMapper.update() (纯 UPDATE，不 INSERT)
guacamole_system_config 表
  ↓ configCache.invalidate(key)
下次 /api/config 请求返回新值
```

### 配置优先级（三级 fallback）

```
1. guacamole_system_config 表（运行时修改，管理员通过 Web 界面保存）
   ↓ 无值时 fallback
2. application.yml 的 guacamole.system.defaults 段（构建时配置）
   ↓ 无值时 fallback
3. Guacamole 原生 guacamole.properties 属性（如 postgresql-user-password-min-length）
```

### 主题引擎

```
configService.getConfig() → theme 配置
  ↓
colorEngine.calculate(themeConfig, mode) → 35 个 CSS 变量值
  ↓
themeService.setThemeVariable(name, value)
  ↓ document.documentElement.style.setProperty()
:root 变量生效 → 全站 UI 配色更新
```

- 模式：`light` / `dark` / `auto`（跟随 `prefers-color-scheme`）
- CSS 变量在 `variables.css` 中声明 fallback 默认值，JS 失败时 CSS 默认值兜底

### 安全策略接入

密码复杂度的 4 个配置项（`security.password_min_length` 等）已接入三数据库的 `PasswordPolicy`：

```
PasswordPolicyService.verifyPassword()
  ↓
PostgreSQLPasswordPolicy / MySQLPasswordPolicy / SQLServerPasswordPolicy
  ↓ getDBValue("security.password_min_length")
SystemConfigService.getValue(key)
  ↓ 有值 → Integer.parseInt(value)
  ↓ 无值 → environment.getProperty(MIN_LENGTH, 0)（原有属性 fallback）
```

### 文件上传架构

```
前端 guacFileUpload.js
  ↓ FormData(file + category) + Guacamole-Token
FileRESTService.uploadFile()
  ↓ @FormDataParam → FormDataBodyPart (Jersey multipart)
FileStorageService.upload()
  ↓ validateUpload(MIME, size) + validateFilename(name) + validateCategory(cat)
  ↓ Files.copy() → {file-storage-path}/{category}/{fileId}/{filename}
  ↓ SystemFileMapper.insert() → guacamole_system_file 表
返回 /api/settings/files/{fileId} URL
```

---

## 认证流程

### 登录

```
1. POST /api/tokens {username, password}
   (或 Authorization: Basic base64 编码头)
2. TokenRESTService.createToken()
   → 从请求构建 Credentials
   → 调用 AuthenticationService.authenticate(credentials, token)
3. AuthenticationService.authenticate():
   a. 如果提供了已有 Token → 尝试获取已有 session
   b. 获取 AuthenticatedUser:
      - 已有 session → 对原始 AuthenticationProvider 重新认证
      - 无 session → 遍历每个 AuthenticationProvider:
        provider.authenticateUser(credentials)
        - 成功 → 返回 AuthenticatedUser
        - GuacamoleInsufficientCredentialsException → 记录（优先于无效凭证）
        - GuacamoleCredentialsException → 记录第一个
        - 所有失败 → 抛出 GuacamoleInvalidCredentialsException
   c. 获取 UserContexts:
      - 已有 session → 更新原有 UserContexts（调用 updateUserContext）
      - 无 session → 调用每个 provider.getUserContext(authenticatedUser)
      - 装饰每个 UserContext（应用 SSO、TOTP、vault 装饰器）
   d. 存储/更新 session:
      - 已有 session → 更新 authenticatedUser 和 userContexts
      - 新 session → 生成新 Token → 存入 TokenSessionMap
4. 返回 APIAuthenticationResult:
   {authToken, username, authProviderIdentifier, availableDataSources}
```

### 已认证请求

Token 传递方式（优先级）：
1. `Guacamole-Token` HTTP 头（最高优先级）
2. `token` 查询参数

```java
// AuthenticationService.getAuthenticationToken()：
String token = request.getHeaderString("Guacamole-Token");
if (token != null && !token.isEmpty()) return token;
token = request.getUriInfo().getQueryParameters().getFirst("token");
return token;
```

### Token 失效（注销）

```
1. DELETE /api/tokens/{token}
2. TokenSessionMap.remove(token) → GuacamoleSession
3. session.invalidate() → 关闭所有活跃隧道
4. 或 DELETE /api/session/ → 从当前请求的 Token 中获取 session 并移除
```

### 共享密钥认证

```
1. GET /#/?token=share-key-xxx
2. verifyCachedVersion.js 检查构建标识符，用于缓存失效
3. index.html 加载，AngularJS 应用启动
4. AuthenticationService 用共享密钥作为凭证认证
5. 匹配的 SharedAuthenticationProvider → SharedUserContext（仅限共享连接）
```

### 多因素认证

TokenRESTService 的 `createToken()` 支持多步认证：
- 首次请求提供 `username` + `password`
- 第二步提供 `token`（已有 Token）+ 额外凭证字段
- AuthenticationService 检查已有 session 并更新 activeAuthentication 状态

---

## 属性解析机制

### 两层系统

```
┌────────────────────────────────────────────────┐
│                应用层读取                        │
│  JDBCEnvironment.getProperty("ldap-hostname")  │
│  → DelegatingEnvironment                       │
│    → LocalEnvironment.getInstance()             │
└──────────────────┬─────────────────────────────┘
                   │
┌──────────────────v─────────────────────────────┐
│           EnvironmentConfig (桥接器)             │
│                                                  │
│  GuacamoleProperties 适配器 #1（Spring 优先）   │
│  1. Exact match: "ldap-hostname"                │
│  2. Legacy guacd: "guacd-hostname" →            │
│     "guacamole.guacd.hostname"                  │
│  3. Prefix loop: "guacamole.auth.ldap." + name  │
│                                                  │
│  GuacamoleProperties 适配器 #2（环境变量后备）  │
│  SystemEnvironmentGuacamoleProperties            │
│  → GUACAMOLE_AUTH_LDAP_LDAP_HOSTNAME            │
└──────────────────┬─────────────────────────────┘
                   │
┌──────────────────v─────────────────────────────┐
│            Spring Environment                    │
│  application.yml → 环境变量 → -D 系统属性       │
│  → 命令行参数 --guacamole.auth.ldap.ldap-       │
│    hostname=...                                 │
└────────────────────────────────────────────────┘
```

### 16 个命名空间前缀

`EnvironmentConfig` 遍历这些前缀，当直接属性名未找到时尝试拼接：

| 命名空间 | 扩展 | 示例属性 |
|----------|------|----------|
| `guacamole.auth.mysql` | MySQL JDBC | `mysql-hostname` |
| `guacamole.auth.postgresql` | PostgreSQL JDBC | `postgresql-hostname` |
| `guacamole.auth.sqlserver` | SQL Server JDBC | `sqlserver-hostname` |
| `guacamole.auth.header` | HTTP 头认证 | `header-redirect-url` |
| `guacamole.auth.duo` | Duo 双因素 | `duo-integration-key` |
| `guacamole.auth.json` | JSON 加密认证 | `json-secret-key` |
| `guacamole.auth.ldap` | LDAP 认证 | `ldap-hostname` |
| `guacamole.auth.totp` | TOTP 双因素 | `totp-issuer` |
| `guacamole.auth.radius` | RADIUS 认证 | `radius-hostname` |
| `guacamole.auth.quickconnect` | QuickConnect | `quickconnect-` |
| `guacamole.auth.sso-cas` | SSO CAS | `cas-` |
| `guacamole.auth.sso-openid` | SSO OpenID | `openid-` |
| `guacamole.auth.sso-saml` | SSO SAML | `saml-` |
| `guacamole.vault.ksm` | KSM Vault | `ksm-` |
| `guacamole.history` | 历史记录 | `history-` |
| `guacamole.guacd` | guacd 连接 | `guacd-hostname`、`guacd-port`、`guacd-ssl` |

### 遗留属性的显式映射

桥梁中三个 guacd 属性有显式映射：
- `"guacd-hostname"` → `"guacamole.guacd.hostname"`
- `"guacd-port"` → `"guacamole.guacd.port"`
- `"guacd-ssl"` → `"guacamole.guacd.ssl"`

---

## Guice 到 Spring 迁移对照

### 已变更的项

| 层次 | Guice（原项目） | Spring Boot（本项目） |
|------|----------------|---------------------|
| 应用入口 | `GuacamoleServletContextListener` | `@SpringBootApplication` |
| 模块加载 | `AbstractModule` 子类 | `@Configuration` + `@Bean` |
| DI 注解 | `@Inject`、`@Singleton` | `@Autowired`、默认单例 |
| 工厂模式 | `FactoryModuleBuilder` | Lambda `DirectoryObjectResourceFactory` |
| REST 注册 | Guice-HK2 桥接 | Jersey 自动扫描（`packages()`） |
| 过滤器注册 | Guice `filter()` | `FilterRegistrationBean` / `@Bean` |
| WebSocket | 容器特定 `WebSocketTunnelModule` | `WebSocketConfig` + JSR 356 |
| Servlet 上下文 | `ServletContext` 监听器 | Spring `ServletContextInitializer` |
| 扩展加载 | `GuacamoleExtensionLoader`（ServiceLoader） | `ResourcePatternResolver` classpath 扫描 |
| 配置 | `GUACAMOLE_HOME/guacamole.properties` | `application.yml` + Spring Environment |
| 属性桥接 | 直接文件读取 | `EnvironmentConfig` → `LocalEnvironment` 适配器 |
| 时区设置 | JVM 默认 | 应用启动时 `TimeZone.setDefault()` |

### 未变更的项

| 层次 | 说明 |
|------|------|
| 协议层 | `guacamole-common`（约 51 个 Java 文件）—— 零修改 |
| 扩展 API | `guacamole-ext`（约 136 个 Java 文件）—— 仅 javax 到 jakarta 迁移 |
| JavaScript API | `guacamole-common-js`（约 34 个模块）—— 零修改 |
| REST 注解 | 所有 `@Path`、`@GET`、`@POST` 等保留 |
| URL 结构 | `/api/*`、`/websocket-tunnel`、`/app.js`、`/app.css` 不变 |
| 前端 | AngularJS 1.8 webapp —— 零修改（仅构建标识符格式） |
| 资源服务 | `ResourceServlet` + 304 缓存 —— 行为一致 |
| MyBatis 映射器 | 所有 SQL 保留，组织在按数据库分类的目录中 |
| Schema 脚本 | 与上游 Guacamole 相同 |
