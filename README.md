# Guacamole Spring Boot

Apache Guacamole 1.5.5 迁移至 Spring Boot 3.3.5 + JDK 17 的远程桌面网关。

## 项目概述

将 Apache Guacamole 从 Google Guice 迁移到 Spring Boot，采用标准 Starter 模式管理扩展模块。

## 技术栈

- Java 17
- Spring Boot 3.3.5
- MyBatis + MyBatis-Spring-Boot
- PostgreSQL / MySQL / SQL Server
- Jersey (REST API)
- WebSocket

## 项目结构

```
guacamole-spring-boot/
├── guacamole-core/                    # 核心应用模块
├── guacamole-ext/                     # 扩展 API 模块
└── starters/                          # 扩展 Starter 模块
    ├── guacamole-auth-header-starter/ # HTTP Header 认证
    ├── guacamole-auth-json-starter/   # JSON Token 认证
    ├── guacamole-auth-totp-starter/   # TOTP 双因素认证
    ├── guacamole-auth-quickconnect-starter/ # 快速连接
    ├── guacamole-auth-duo-starter/    # Duo 双因素认证
    ├── guacamole-auth-ldap-starter/   # LDAP 认证
    ├── guacamole-auth-radius-starter/ # RADIUS 认证
    ├── guacamole-auth-sso-base-starter/ # SSO 基础模块
    ├── guacamole-auth-sso-cas-starter/ # CAS 单点登录
    ├── guacamole-auth-sso-openid-starter/ # OpenID Connect
    ├── guacamole-auth-sso-saml-starter/ # SAML 单点登录
    ├── guacamole-vault-base-starter/  # Vault 基础模块
    ├── guacamole-vault-ksm-starter/   # Keeper Vault
    ├── guacamole-history-starter/     # 历史记录
    └── guacamole-auth-jdbc/           # JDBC 数据库认证
        ├── guacamole-auth-jdbc-base/  # 共享代码
        ├── guacamole-auth-mysql-starter/
        ├── guacamole-auth-postgresql-starter/
        └── guacamole-auth-sqlserver-starter/
```

## 构建和运行

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar guacamole-core/target/guacamole-core-1.0.0-SNAPSHOT.jar

# 或
mvn spring-boot:run -pl guacamole-core

# Docker
docker-compose up -d
```

## 配置说明

配置文件: `guacamole-core/src/main/resources/application.yml`

### 完整配置示例

```yaml
server:
  port: 8080

guacamole:
  guacd:
    hostname: localhost
    port: 4822
    ssl: false

  auth:
    # 基础认证模块
    header:
      enabled: true
    json:
      enabled: true
    quickconnect:
      enabled: true
    totp:
      enabled: false
    duo:
      enabled: false

    # 数据库认证（同时只能启用一个）
    mysql:
      enabled: false
    postgresql:
      enabled: true
    sqlserver:
      enabled: false

    # LDAP 认证
    ldap:
      enabled: false
      ldap-hostname: ldap.example.com
      ldap-port: 389
      ldap-user-base-dn: ou=users,dc=example,dc=com
      ldap-username-attribute: uid

    # RADIUS 认证
    radius:
      enabled: false
      radius-hostname: localhost
      radius-shared-secret: testing123
      radius-auth-protocol: PAP

    # SSO 单点登录（同时只能启用一个）
    sso-cas:
      enabled: false
      cas-authorization-endpoint: https://cas.example.org/cas
      cas-redirect-uri: http://localhost:8080/

    sso-openid:
      enabled: false
      openid-authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
      openid-jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
      openid-issuer: https://accounts.google.com
      openid-client-id: your-client-id
      openid-redirect-uri: http://localhost:8080/

    sso-saml:
      enabled: false
      saml-callback-url: http://localhost:8080/
      saml-idp-metadata-url: https://idp.example.com/metadata.xml

  vault:
    ksm:
      enabled: false

  history:
    enabled: true
    recording-search-path: /tmp/guacamole/recordings

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: guacamole
    driver-class-name: org.postgresql.Driver
```

### 互斥限制

- **数据库认证**：同时只能启用一个（MySQL、PostgreSQL、SQL Server），启动时校验
- **SSO 单点登录**：同时只能启用一个（CAS、OpenID Connect、SAML），启动时校验

### Header Auth 配置

默认读取 `REMOTE_USER` 头。可通过配置自定义：

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: X-Remote-User  # 自定义头名称
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `http-auth-header` | - | `REMOTE_USER` | 用于传递认证用户名的 HTTP 头名称 |

### JSON Auth 配置

```yaml
guacamole:
  auth:
    json:
      enabled: true
      json-secret-key: your-base64-encoded-secret-key
      json-trusted-networks: 192.168.1.0/24,10.0.0.0/8
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `json-secret-key` | ✅ | - | Base64 编码的对称密钥（至少 32 字节），用于加密和签名 |
| `json-trusted-networks` | - | 允许所有 | 允许认证的 IP 地址或 CIDR 子网（逗号分隔） |

### LDAP 配置

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ldap.example.com
      ldap-port: 389
      ldap-user-base-dn: ou=users,dc=example,dc=com
      ldap-username-attribute: uid
      # 以下为可选配置
      ldap-group-base-dn: ou=groups,dc=example,dc=com
      ldap-group-name-attribute: cn
      ldap-member-attribute: member
      ldap-member-attribute-type: dn
      ldap-search-bind-dn: cn=admin,dc=example,dc=com
      ldap-search-bind-password: password
      ldap-encryption-method: starttls   # none、ssl、starttls
      ldap-max-search-results: 1000
      ldap-operation-timeout: 30
      ldap-network-timeout: 30
      ldap-user-search-filter: (&(objectClass=person)(uid={0}))
      ldap-group-search-filter: (&(objectClass=groupOfNames)(member={0}))
      ldap-follow-referrals: false
      ldap-max-referral-hops: 5
      ldap-dereference-aliases: never
      ldap-user-attributes: cn,mail
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `ldap-hostname` | ✅ | - | LDAP 服务器地址 |
| `ldap-user-base-dn` | ✅ | - | 用户搜索基础 DN |
| `ldap-username-attribute` | ✅ | - | 用户名属性（如 `uid`、`sAMAccountName`） |
| `ldap-port` | - | `389` | LDAP 端口 |
| `ldap-group-base-dn` | - | - | 组搜索基础 DN |
| `ldap-group-name-attribute` | - | `cn` | 组名属性 |
| `ldap-member-attribute` | - | `member` | 组成员属性 |
| `ldap-member-attribute-type` | - | `dn` | 成员属性类型 |
| `ldap-search-bind-dn` | - | - | 搜索绑定 DN（用于搜索用户） |
| `ldap-search-bind-password` | - | - | 搜索绑定密码 |
| `ldap-encryption-method` | - | `none` | 加密方式：`none`、`ssl`、`starttls` |
| `ldap-max-search-results` | - | `1000` | 最大搜索结果数 |
| `ldap-operation-timeout` | - | - | 操作超时（秒） |
| `ldap-network-timeout` | - | - | 网络超时（秒） |
| `ldap-user-search-filter` | - | - | 用户搜索过滤器（`{0}` 会被替换为用户名） |
| `ldap-group-search-filter` | - | - | 组搜索过滤器 |
| `ldap-follow-referrals` | - | `false` | 是否跟随 LDAP 转介 |
| `ldap-max-referral-hops` | - | `5` | 最大转介跳数 |
| `ldap-dereference-aliases` | - | `never` | 别名解引用策略 |
| `ldap-user-attributes` | - | - | 额外用户属性（逗号分隔） |

### RADIUS 配置

```yaml
guacamole:
  auth:
    radius:
      enabled: true
      radius-hostname: localhost
      radius-auth-port: 1812
      radius-shared-secret: testing123
      radius-auth-protocol: PAP
      # 以下为可选配置
      radius-acct-port: 1813
      radius-max-retries: 3
      radius-timeout: 30
      radius-nas-ip: 192.168.1.1
      radius-trust-all: false
      radius-ca-file: /path/to/ca.pem
      radius-key-file: /path/to/key.pem
      radius-key-password: keypass
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `radius-hostname` | ✅ | - | RADIUS 服务器地址 |
| `radius-shared-secret` | ✅ | - | RADIUS 共享密钥 |
| `radius-auth-protocol` | ✅ | - | 认证协议（PAP、CHAP、MSCHAPv1、MSCHAPv2、EAP-TLS、EAP-TTLS） |
| `radius-auth-port` | - | `1812` | RADIUS 认证端口 |
| `radius-acct-port` | - | `1813` | RADIUS 计费端口 |
| `radius-max-retries` | - | `3` | 最大重试次数 |
| `radius-timeout` | - | `30` | 超时时间（秒） |
| `radius-nas-ip` | - | - | NAS IP 地址 |
| `radius-trust-all` | - | `false` | 是否信任所有服务器证书 |
| `radius-ca-file` | - | - | CA 证书文件路径 |
| `radius-key-file` | - | - | 客户端密钥文件路径 |
| `radius-key-password` | - | - | 客户端密钥密码 |

### SSO 配置

同时只能启用一个 SSO 模块（CAS、OpenID Connect、SAML），启动时会校验，如果启用多个会报错。

#### CAS 单点登录

```yaml
guacamole:
  auth:
    sso-cas:
      enabled: true
      cas-authorization-endpoint: https://cas-server.com/cas
      cas-redirect-uri: http://localhost:8080/
      # 以下为可选配置
      cas-clearpass-key: /path/to/private.key
      cas-group-attribute: memberOf
      cas-group-format: plain            # plain 或 ldap
      cas-group-ldap-base-dn: ou=groups,dc=example,dc=com
      cas-group-ldap-attribute: cn
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `cas-authorization-endpoint` | ✅ | - | CAS 服务的授权端点 URL |
| `cas-redirect-uri` | ✅ | - | 认证完成后 CAS 重定向回的 URL（Guacamole 访问地址） |
| `cas-clearpass-key` | - | - | ClearPass 私钥文件路径，用于解密 CAS 返回的密码 |
| `cas-group-attribute` | - | - | CAS 属性中表示组成员的属性名（如 `memberOf`） |
| `cas-group-format` | - | `plain` | 组名格式：`plain`（纯文本）或 `ldap`（LDAP DN） |
| `cas-group-ldap-base-dn` | - | - | LDAP 格式组的基础 DN |
| `cas-group-ldap-attribute` | - | - | LDAP 格式组的属性名 |

#### OpenID Connect

```yaml
guacamole:
  auth:
    sso-openid:
      enabled: true
      openid-authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
      openid-jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
      openid-issuer: https://accounts.google.com
      openid-client-id: your-client-id
      openid-redirect-uri: http://localhost:8080/
      # 以下为可选配置
      openid-scope: openid email profile
      openid-username-claim-type: email
      openid-groups-claim-type: groups
      openid-allowed-clock-skew: 30
      openid-max-token-validity: 300
      openid-max-nonce-validity: 10
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `openid-authorization-endpoint` | ✅ | - | OpenID Provider 的授权端点 URL |
| `openid-jwks-endpoint` | ✅ | - | JWKS 端点 URL，用于验证 JWT 签名 |
| `openid-issuer` | ✅ | - | JWT 中的签发者标识 |
| `openid-client-id` | ✅ | - | OpenID Provider 分配的客户端 ID |
| `openid-redirect-uri` | ✅ | - | 认证完成后重定向回的 URL（Guacamole 访问地址） |
| `openid-scope` | - | `openid email profile` | 请求的 OpenID 范围（空格分隔） |
| `openid-username-claim-type` | - | `email` | JWT 中包含用户名的 claim |
| `openid-groups-claim-type` | - | `groups` | JWT 中包含用户组的 claim |
| `openid-allowed-clock-skew` | - | `30` | 允许的时钟偏差（秒） |
| `openid-max-token-validity` | - | `300` | Token 最大有效期（分钟） |
| `openid-max-nonce-validity` | - | `10` | Nonce 最大有效期（分钟） |

#### SAML 单点登录

```yaml
guacamole:
  auth:
    sso-saml:
      enabled: true
      saml-callback-url: http://localhost:8080/
      # 方式一：使用 IdP 元数据 URL
      saml-idp-metadata-url: https://idp.example.com/metadata.xml
      # 方式二：手动配置 IdP 信息
      saml-idp-url: https://idp.example.com/sso
      saml-entity-id: http://localhost:8080/
      # 以下为可选配置
      saml-strict: true
      saml-debug: false
      saml-compress-request: true
      saml-compress-response: true
      saml-group-attribute: groups
      saml-auth-timeout: 5
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `saml-callback-url` | ✅ | - | SAML 回调基础 URL（Guacamole 访问地址） |
| `saml-idp-metadata-url` | 条件 | - | IdP 元数据 XML 的 URL（与 `saml-idp-url` 二选一） |
| `saml-idp-url` | 条件 | - | IdP 登录 URL（不使用元数据时必填） |
| `saml-entity-id` | 条件 | - | SAML 客户端实体 ID（不使用元数据时必填） |
| `saml-strict` | - | `true` | 是否强制严格安全检查（生产环境建议保持 `true`） |
| `saml-debug` | - | `false` | 是否启用 SAML 调试日志 |
| `saml-compress-request` | - | `true` | 是否压缩 SAML 请求 |
| `saml-compress-response` | - | `true` | 是否压缩 SAML 响应 |
| `saml-group-attribute` | - | `groups` | IdP 返回的组成员属性名 |
| `saml-auth-timeout` | - | `5` | SAML 认证超时时间（分钟） |

### 多认证链

多个认证模块可同时启用，Guacamole 按顺序尝试每个 provider：
1. 第一个成功认证的 provider 决定用户身份
2. 如果所有 provider 都失败，登录失败
3. 每个 provider 独立，互不影响

示例：同时启用 LDAP + PostgreSQL
- 用户 `einstein`（LDAP 用户）→ LDAP 认证成功
- 用户 `admin`（数据库用户）→ LDAP 失败 → PostgreSQL 认证成功
- 用户 `unknown`（两边都没有）→ 所有 provider 失败 → 登录失败

## 扩展模块说明

### 已验证通过 ✅

| 模块 | 作用 | 测试状态 | 说明 |
|------|------|---------|------|
| **Header Auth** | 反向代理认证，从 HTTP 头读取用户名 | ✅ 已验证 | 需配置 `REMOTE_USER` 头，无头时抛异常 |
| **JSON Auth** | 加密 Token 认证，无持久化存储 | ✅ 已验证 | 使用 AES/CBC/PKCS5Padding + HMAC-SHA256 |
| **TOTP** | 基于时间的一次性密码（Google Authenticator 等） | ✅ 已验证 | 需用户自行绑定 |
| **Quick Connect** | 快速创建连接（输入 `rdp://host:3389`） | ✅ 已验证 | 支持 RDP/VNC/SSH/Telnet |
| **History** | 连接历史记录和录像存储 | ✅ 已验证 | 配置 `recording-search-path` |
| **PostgreSQL** | PostgreSQL 数据库认证 | ✅ 已验证 | 需配置 `spring.datasource` |
| **MySQL** | MySQL 数据库认证 | ✅ 已验证 | 需配置 `spring.datasource` |
| **SQL Server** | SQL Server 数据库认证 | ✅ 已验证 | 需配置 `spring.datasource` |
| **LDAP** | LDAP/AD 目录认证 | ✅ 已验证 | 标准 Starter 模式，支持多认证链 |

### 启动验证通过 ⏳

| 模块 | 作用 | 测试状态 | 说明 |
|------|------|---------|------|
| **DUO** | Duo Security 双因素认证 | ⏳ 启动验证通过 | 需升级到 Web SDK v4（见下方说明） |
| **RADIUS** | RADIUS 认证 | ✅ 启动验证通过 | 标准 Starter 模式，需配置 RADIUS 服务器 |
| **SSO CAS** | CAS 单点登录 | ✅ 启动验证通过 | 标准 Starter 模式，需配置 CAS 服务器 |
| **SSO OpenID** | OpenID Connect 认证 | ⏳ 启动验证通过 | 标准 Starter 模式，需配置 OpenID Provider |
| **SSO SAML** | SAML 单点登录 | ⏳ 启动验证通过 | 标准 Starter 模式，需配置 SAML IdP |

**SSO 注意事项：**
- 同时只能启用一个 SSO 模块（CAS、OpenID Connect、SAML），启动时会校验
- 同理，同时只能启用一个数据库认证模块（MySQL、PostgreSQL、SQL Server）

### 待验证

| 模块 | 作用 | 测试状态 | 说明 |
|------|------|---------|------|
| **SSO Base** | SSO 基础模块（供 CAS/OpenID/SAML 使用） | ✅ 已验证 | 库模块，提供共享资源和基类 |
| **Vault Base** | Vault 基础模块 | 待验证 | 库模块 |
| **Vault KSM** | Keeper Secrets Manager | 待验证 | 依赖非 Maven Central，已注释 |

## 已知问题

### DUO 认证需要升级

**问题：** Guacamole 1.5.5 使用 Duo Web SDK v2（iframe 方式），Duo 已于 2024 年 3 月停止支持传统 Prompt，强制要求使用 Universal Prompt（Web SDK v4）。

**影响：** DUO 认证无法实际使用，启动验证通过但认证会被 Duo 拦截。

**解决方案：** 需要将 DUO 集成从 Web SDK v2 升级到 Web SDK v4：
- 前端：iframe 方式改为重定向方式
- 后端：HMAC SHA-256 改为 HMAC SHA-512
- 配置：去掉 `duo-application-key`，`ikey`/`skey` 改名为 `client_id`/`client_secret`
- 现有 Web SDK v2 应用可复用，无需新建

**参考文档：** https://duo.com/docs/duoweb

## 开发说明

### Starter 模式

扩展模块采用标准 Spring Boot Starter 模式：
- `@ConditionalOnProperty` 控制模块启用/禁用
- `@Bean` 集中在 AutoConfiguration 类中定义
- 通过 `AutoConfiguration.imports` 注册
- 未启用的模块不会加载任何 Bean
- Prototype Bean 用于需要每次创建新实例的类（如 `AuthenticatedUser`、`UserContext`）

**已转换为标准模式的模块：**
- Header Auth、JSON Auth、TOTP、QuickConnect、History
- MySQL、PostgreSQL、SQL Server（JDBC）
- LDAP、RADIUS
- SSO 系列：CAS、OpenID、SAML

**待转换的模块：**
- DUO、Vault

### 与原项目的主要差异

1. **依赖注入：** Guice `@Inject` → Spring `@Autowired`
2. **模块加载：** Guice Module → Spring Boot AutoConfiguration
3. **静态资源：** 原项目打包压缩（`.min.js`），Spring Boot 直接使用源文件
4. **属性配置：** `guacamole.properties` → `application.yml`
5. **数据库：** MyBatis mapper XML 按数据库类型隔离到独立目录

### MyBatis 配置

每个数据库 starter 有独立的 `MyBatisConfig`：
- `@MapperScan` 扫描共享的 mapper 接口
- `SqlSessionFactoryBean` 加载对应数据库的 mapper XML
- `SqlSessionTemplate` 提供线程安全的 SqlSession

Mapper XML 目录结构：
```
resources/
└── mappers/
    ├── mysql/          # MySQL 特定 SQL
    ├── postgresql/     # PostgreSQL 特定 SQL
    └── sqlserver/      # SQL Server 特定 SQL
```

### 属性桥接

`EnvironmentConfig` 将 Spring `Environment` 桥接到 Guacamole `LocalEnvironment`，支持：
- 直接属性名（如 `http-auth-header`）
- 带前缀属性名（如 `guacamole.auth.header.http-auth-header`）
