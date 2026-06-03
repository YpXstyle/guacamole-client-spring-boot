# 配置参考手册

[← 返回文档索引](../README_zh.md#documentation-index)

Guacamole Spring Boot 全部配置参考。

## 目录

- [引言](#引言)
- [核心配置](#核心配置)
- [数据库连接配置](#数据库连接配置)
- [认证扩展模块配置](#认证扩展模块配置)
  - [JDBC PostgreSQL](#jdbc-postgresql)
  - [JDBC MySQL](#jdbc-mysql)
  - [JDBC SQL Server](#jdbc-sql-server)
  - [Header 认证](#header-认证)
  - [JSON 认证](#json-认证)
  - [LDAP 认证](#ldap-认证)
  - [RADIUS 认证](#radius-认证)
  - [TOTP 双因素认证](#totp-双因素认证)
  - [DUO 双因素认证](#duo-双因素认证)
  - [QuickConnect](#quickconnect)
  - [CAS 单点登录](#cas-单点登录)
  - [OpenID Connect 单点登录](#openid-connect-单点登录)
  - [SAML 2.0 单点登录](#saml-20-单点登录)
- [功能扩展模块配置](#功能扩展模块配置)
  - [History 会话录像](#history-会话录像)
  - [Vault KSM 密钥管理](#vault-ksm-密钥管理)
- [系统配置模块（运行时可改）](#系统配置模块运行时可改)
  - [品牌定制（branding）](#品牌定制branding)
  - [主题配色（theme）](#主题配色theme)
  - [安全策略（security）](#安全策略security)
  - [系统公告（announcement）](#系统公告announcement)
  - [配置优先级](#配置优先级)
- [多认证链配置](#多认证链配置)
- [环境变量覆盖](#环境变量覆盖)
- [常见配置场景](#常见配置场景)

---

## 引言

Guacamole Spring Boot 使用**两层属性系统**：

### 第一层：Spring Boot 原生属性

这些属性由 Spring Boot 框架直接处理，遵循 Spring Boot 标准绑定规则。典型示例：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}

logging:
  level:
    org.apache.guacamole: info
```

### 第二层：Guacamole 桥接器属性

这些属性以 `guacamole.*` 为前缀，通过 Guacamole 的 `Environment` 桥接器读取。`Environment` 实现将 `application.yml` 中的值映射到传统 `guacamole.properties` 属性名，使所有扩展模块代码无需修改即可工作：

```
application.yml 路径                              guacamole.properties 旧键名
guacamole.guacd.hostname                 -->     guacd-hostname
guacamole.auth.ldap.ldap-hostname        -->     ldap-hostname
guacamole.auth.postgresql.batch-size     -->     postgresql-batch-size
```

桥接规则：`guacamole.*` 下的 YAML 路径中，最后一段（如 `ldap-hostname`）直接对应 `guacamole.properties` 中的键名。

### 扩展启用规则

所有认证和功能扩展模块遵循统一的启用模式：

```yaml
guacamole:
  auth:
    <模块名>:
      enabled: true    # true = 启用，false = 禁用
```

两个条件必须同时满足模块才能激活：
1. Maven 依赖存在于 classpath（在 `guacamole/pom.xml` 中添加 starter）
2. `enabled: true` 在 `application.yml` 中设置

**所有扩展模块默认禁用。** 旧版 Guacamole 中扩展 JAR 存在于 `GUACAMOLE_HOME/extensions/` 即可自动加载的行为已被取代。

---

## 核心配置

### 服务端口与 guacd

```yaml
server:
  port: 8080

guacamole:
  guacd:
    hostname: localhost     # guacd 守护进程主机名
    port: 4822              # guacd 端口（默认 4822）
    ssl: false              # 是否使用 SSL/TLS 连接 guacd
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `server.port` | int | 否 | `8080` | HTTP 服务端口 |
| `guacamole.guacd.hostname` | string | 否 | `localhost` | guacd 代理守护进程主机名或 IP |
| `guacamole.guacd.port` | int | 否 | `4822` | guacd 端口 |
| `guacamole.guacd.ssl` | boolean | 否 | `false` | 启用 SSL/TLS 连接 guacd |

### 时区

通过 JVM 系统属性启动时设置：

```bash
java -Duser.timezone=Asia/Shanghai -jar guacamole/target/guacamole-*.jar
```

Docker 环境：

```yaml
environment:
  JAVA_TOOL_OPTIONS: "-Duser.timezone=Asia/Shanghai"
```

---

## 数据库连接配置

### 重要说明

**DataSource 由 Spring Boot 的 `spring.datasource.*` 管理。** 传统 `guacamole.properties` 中的 JDBC 连接属性（`<db>-hostname`、`<db>-port`、`<db>-database`、`<db>-username`、`<db>-password` 等）**不再用于建立数据库连接**。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

### 各数据库驱动类名

| 数据库 | 驱动类 |
|--------|--------|
| PostgreSQL | `org.postgresql.Driver` |
| MySQL | `com.mysql.cj.jdbc.Driver` |
| SQL Server | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |

### 连接池调优

Spring Boot DataSource 支持 HikariCP 连接池调优：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 旧 JDBC 属性状态

下表中的旧属性**在代码中定义**但**不再用于数据库连接**。它们仅存在于 `Environment` 类中，实际业务代码未调用。此处列出仅为识别历史配置时参考：

| 僵尸属性（全局） | 说明 |
|------------------|------|
| `<db>-hostname` | 不再使用 |
| `<db>-port` | 不再使用 |
| `<db>-database` | 不再使用 |
| `<db>-username` | 不再使用 |
| `<db>-password` | 不再使用 |
| 所有 `<db>-ssl-*` 属性 | 不再使用 |
| `<db>-driver` | 不再使用 |
| `mysql-server-timezone` | 不再使用 |
| `postgresql-socket-timeout` | 不再使用 |
| `postgresql-default-statement-timeout` | 不再使用 |
| `sqlserver-instance` | 不再使用 |

---

## 认证扩展模块配置

### JDBC PostgreSQL

PostgreSQL 数据库认证。使用 MyBatis 访问数据库中的用户、连接和权限表。

```yaml
guacamole:
  auth:
    postgresql:
      enabled: true
```

**必须同时配置 DataSource：**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.postgresql.enabled` | boolean | 是 | `false` | 启用 PostgreSQL 认证 |
| `guacamole.auth.postgresql.user-required` | boolean | 否 | `false` | 是否要求数据库中存在用户账户 |
| `guacamole.auth.postgresql.absolute-max-connections` | int | 否 | `0` | 全局并发连接限制（0=不限） |
| `guacamole.auth.postgresql.default-max-connections` | int | 否 | `0` | 单连接默认并发限制 |
| `guacamole.auth.postgresql.default-max-group-connections` | int | 否 | `0` | 单连接组默认并发限制 |
| `guacamole.auth.postgresql.default-max-connections-per-user` | int | 否 | `0` | 每用户每连接默认限制 |
| `guacamole.auth.postgresql.default-max-group-connections-per-user` | int | 否 | `1` | 每用户每连接组默认限制 |
| `guacamole.auth.postgresql.batch-size` | int | 否 | `5000` | SQL 批处理大小 |
| `guacamole.auth.postgresql.auto-create-accounts` | boolean | 否 | `false` | 自动为其他认证源认证的用户创建数据库账户 |

**完整示例：**

```yaml
guacamole:
  auth:
    postgresql:
      enabled: true
      user-required: false
      absolute-max-connections: 0
      default-max-connections: 0
      default-max-group-connections: 0
      default-max-connections-per-user: 0
      default-max-group-connections-per-user: 1
      batch-size: 5000
      auto-create-accounts: false

spring:
  datasource:
    url: jdbc:postgresql://192.168.1.100:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

---

### JDBC MySQL

MySQL / MariaDB 数据库认证。

```yaml
guacamole:
  auth:
    mysql:
      enabled: true

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/guacamole?useSSL=false&serverTimezone=UTC
    username: guacamole
    password: ${MYSQL_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.mysql.enabled` | boolean | 是 | `false` | 启用 MySQL 认证 |
| `guacamole.auth.mysql.user-required` | boolean | 否 | `false` | 是否要求数据库中存在用户账户 |
| `guacamole.auth.mysql.absolute-max-connections` | int | 否 | `0` | 全局并发连接限制（0=不限） |
| `guacamole.auth.mysql.default-max-connections` | int | 否 | `0` | 单连接默认并发限制 |
| `guacamole.auth.mysql.default-max-group-connections` | int | 否 | `0` | 单连接组默认并发限制 |
| `guacamole.auth.mysql.default-max-connections-per-user` | int | 否 | `0` | 每用户每连接默认限制 |
| `guacamole.auth.mysql.default-max-group-connections-per-user` | int | 否 | `1` | 每用户每连接组默认限制 |
| `guacamole.auth.mysql.batch-size` | int | 否 | `1000` | SQL 批处理大小 |
| `guacamole.auth.mysql.auto-create-accounts` | boolean | 否 | `false` | 自动为其他认证源认证的用户创建数据库账户 |

**完整示例：**

```yaml
guacamole:
  auth:
    mysql:
      enabled: true
      user-required: false
      absolute-max-connections: 0
      default-max-connections: 0
      default-max-group-connections: 0
      default-max-connections-per-user: 0
      default-max-group-connections-per-user: 1
      batch-size: 1000
      auto-create-accounts: false

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/guacamole?useSSL=false&serverTimezone=UTC
    username: guacamole
    password: ${MYSQL_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

---

### JDBC SQL Server

Microsoft SQL Server 数据库认证。

```yaml
guacamole:
  auth:
    sqlserver:
      enabled: true

spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=guacamole
    username: guacamole
    password: ${MSSQL_PASSWORD}
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.sqlserver.enabled` | boolean | 是 | `false` | 启用 SQL Server 认证 |
| `guacamole.auth.sqlserver.user-required` | boolean | 否 | `false` | 是否要求数据库中存在用户账户 |
| `guacamole.auth.sqlserver.absolute-max-connections` | int | 否 | `0` | 全局并发连接限制（0=不限） |
| `guacamole.auth.sqlserver.default-max-connections` | int | 否 | `0` | 单连接默认并发限制 |
| `guacamole.auth.sqlserver.default-max-group-connections` | int | 否 | `0` | 单连接组默认并发限制 |
| `guacamole.auth.sqlserver.default-max-connections-per-user` | int | 否 | `0` | 每用户每连接默认限制 |
| `guacamole.auth.sqlserver.default-max-group-connections-per-user` | int | 否 | `1` | 每用户每连接组默认限制 |
| `guacamole.auth.sqlserver.batch-size` | int | 否 | `500` | SQL 批处理大小 |
| `guacamole.auth.sqlserver.auto-create-accounts` | boolean | 否 | `false` | 自动为其他认证源认证的用户创建数据库账户 |

**完整示例：**

```yaml
guacamole:
  auth:
    sqlserver:
      enabled: true
      user-required: false
      absolute-max-connections: 0
      default-max-connections: 0
      default-max-group-connections: 0
      default-max-connections-per-user: 0
      default-max-group-connections-per-user: 1
      batch-size: 500
      auto-create-accounts: false

spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=guacamole
    username: guacamole
    password: ${MSSQL_PASSWORD}
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
```

---

### Header 认证

反向代理 SSO -- 基于 HTTP 请求头认证用户（通常由 Nginx、Apache 或 IdP 代理设置）。

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: REMOTE_USER    # 可选，默认 REMOTE_USER
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.header.enabled` | boolean | 是 | `false` | 启用 Header 认证 |
| `guacamole.auth.header.http-auth-header` | string | 否 | `REMOTE_USER` | 包含认证用户名的 HTTP 请求头 |

**Nginx 配置示例：**

```nginx
location / {
    proxy_pass http://localhost:8080;
    proxy_set_header REMOTE_USER $remote_user;
    auth_basic "Guacamole";
    auth_basic_user_file /etc/nginx/.htpasswd;
}
```

---

### JSON 认证

加密令牌认证，无需数据库。所有连接和用户数据加密在客户端提交的 JSON 载荷中，服务端解密并验证签名。

```yaml
guacamole:
  auth:
    json:
      enabled: true
      json-secret-key: <base64-encoded-256-bit-key>
      json-trusted-networks: 192.168.1.0/24,10.0.0.0/8   # 可选
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.json.enabled` | boolean | 是 | `false` | 启用 JSON 认证 |
| `guacamole.auth.json.json-secret-key` | string（base64） | **是** | -- | Base64 编码的 256 位 AES 密钥，用于加密和 HMAC 签名 |
| `guacamole.auth.json.json-trusted-networks` | string（逗号分隔） | 否 | 允许所有 | CIDR 网络列表（逗号分隔），仅允许来自这些源的请求 |

**生成密钥：**

```bash
openssl rand -base64 32
```

**注意：** 客户端生成令牌时必须使用相同的密钥。令牌使用 AES-256-CBC 加密 + HMAC-SHA256 签名。

---

### LDAP 认证

支持 LDAP 目录和 Microsoft Active Directory 认证。

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ldap.example.com
      ldap-user-base-dn: ou=users,dc=example,dc=com
      ldap-username-attribute: uid
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.ldap.enabled` | boolean | 是 | `false` | 启用 LDAP 认证 |
| `guacamole.auth.ldap.ldap-hostname` | string | 否 | `localhost` | LDAP 服务器主机名 |
| `guacamole.auth.ldap.ldap-port` | int | 否 | `389`（或 `636`，SSL） | LDAP 服务器端口 |
| `guacamole.auth.ldap.ldap-user-base-dn` | string | **是** | -- | 用户搜索基础 DN |
| `guacamole.auth.ldap.ldap-username-attribute` | string | 否 | `uid` | 用户名字段（如 `uid`、`sAMAccountName`） |
| `guacamole.auth.ldap.ldap-config-base-dn` | string | 否 | -- | Guacamole 配置的基础 DN |
| `guacamole.auth.ldap.ldap-group-base-dn` | string | 否 | -- | 组搜索基础 DN |
| `guacamole.auth.ldap.ldap-group-name-attribute` | string | 否 | `cn` | 组名字段 |
| `guacamole.auth.ldap.ldap-search-bind-dn` | string | 否 | -- | 搜索绑定 DN（匿名搜索时留空） |
| `guacamole.auth.ldap.ldap-search-bind-password` | string | 否 | -- | 搜索绑定密码 |
| `guacamole.auth.ldap.ldap-encryption-method` | enum | 否 | `none` | 加密方法：`none`、`ssl`、`starttls` |
| `guacamole.auth.ldap.ldap-max-search-results` | int | 否 | `1000` | LDAP 查询最大结果数 |
| `guacamole.auth.ldap.ldap-dereference-aliases` | enum | 否 | `never` | 别名解除引用：`never`、`always`、`finding`、`searching` |
| `guacamole.auth.ldap.ldap-user-search-filter` | string | 否 | `(objectClass=*)` | 用户搜索 LDAP 过滤器（`{0}` 被替换为用户名） |
| `guacamole.auth.ldap.ldap-group-search-filter` | string | 否 | `(objectClass=*)` | 组搜索 LDAP 过滤器 |
| `guacamole.auth.ldap.ldap-follow-referrals` | boolean | 否 | `false` | 是否跟踪 LDAP 引用 |
| `guacamole.auth.ldap.ldap-max-referral-hops` | int | 否 | `5` | 最大引用跳数 |
| `guacamole.auth.ldap.ldap-operation-timeout` | int | 否 | `30` | LDAP 操作超时（秒） |
| `guacamole.auth.ldap.ldap-network-timeout` | int | 否 | `30000` | LDAP 网络超时（毫秒） |
| `guacamole.auth.ldap.ldap-user-attributes` | string（逗号分隔） | 否 | -- | 额外 LDAP 属性（逗号分隔），暴露给连接使用 |
| `guacamole.auth.ldap.ldap-member-attribute` | string | 否 | `member` | 组成员枚举属性 |
| `guacamole.auth.ldap.ldap-member-attribute-type` | enum | 否 | `dn` | 成员属性类型：`dn`（值为 DN）、`uid`（值为用户名） |

**Active Directory 配置示例：**

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ad.example.com
      ldap-port: 389
      ldap-username-attribute: sAMAccountName
      ldap-user-base-dn: ou=Users,dc=example,dc=com
      ldap-user-search-filter: (&(objectCategory=person)(objectClass=user)(sAMAccountName={0}))
      ldap-group-base-dn: ou=Groups,dc=example,dc=com
      ldap-group-search-filter: (&(objectClass=group)(member={0}))
      ldap-member-attribute: member
      ldap-member-attribute-type: dn
```

**多 LDAP 服务器：** 可在 `GUACAMOLE_HOME/ldap-servers.yml` 中配置多服务器，该文件存在时优先于以上属性。

---

### RADIUS 认证

基于 RADIUS 协议的双因素认证。

```yaml
guacamole:
  auth:
    radius:
      enabled: true
      radius-hostname: radius.example.com
      radius-shared-secret: ${RADIUS_SECRET}
      radius-auth-protocol: pap
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.radius.enabled` | boolean | 是 | `false` | 启用 RADIUS 认证 |
| `guacamole.auth.radius.radius-hostname` | string | 否 | `localhost` | RADIUS 服务器主机名 |
| `guacamole.auth.radius.radius-auth-port` | int | 否 | `1812` | RADIUS 认证端口 |
| `guacamole.auth.radius.radius-acct-port` | int | 否 | `1813` | RADIUS 计费端口 |
| `guacamole.auth.radius.radius-shared-secret` | string | **是** | -- | RADIUS 共享密钥 |
| `guacamole.auth.radius.radius-auth-protocol` | enum | **是** | -- | 认证协议（小写）：`pap`、`chap`、`mschapv1`、`mschapv2`、`eap-md5`、`eap-tls`、`eap-ttls` |
| `guacamole.auth.radius.radius-max-retries` | int | 否 | `5` | 最大重试次数 |
| `guacamole.auth.radius.radius-timeout` | int | 否 | `60` | 超时（秒） |
| `guacamole.auth.radius.radius-ca-file` | string | 否 | `{guacamoleHome}/ca.crt` | CA 证书文件路径 |
| `guacamole.auth.radius.radius-ca-type` | string | 否 | `pem` | CA 文件类型：`pem`、`pkcs12`、`der` |
| `guacamole.auth.radius.radius-ca-password` | string | 否 | -- | CA 文件密码 |
| `guacamole.auth.radius.radius-key-file` | string | 否 | `{guacamoleHome}/radius.key` | 客户端密钥文件路径 |
| `guacamole.auth.radius.radius-key-type` | string | 否 | `pem` | 密钥文件类型：`pem`、`pkcs12`、`der` |
| `guacamole.auth.radius.radius-key-password` | string | 否 | -- | 密钥文件密码 |
| `guacamole.auth.radius.radius-trust-all` | boolean | 否 | `false` | 信任所有服务器证书 |
| `guacamole.auth.radius.radius-eap-ttls-inner-protocol` | enum | 仅 EAP-TTLS | -- | EAP-TTLS 的内部协议 |
| `guacamole.auth.radius.radius-nas-ip` | string | 否 | 自动检测 | 发送给 RADIUS 服务器的 NAS IP 地址 |

> **注意：** RADIUS 是外部认证源，不管理 Guacamole 数据库中的用户和权限。RADIUS 认证成功后，需配合 JDBC 扩展的 `auto-create-accounts: true` 自动创建数据库账户。详见[多认证链配置](#多认证链配置)。

---

### TOTP 双因素认证

基于时间的一次性密码（RFC 6238）。兼容 Google Authenticator、Authy、FreeOTP 等。

```yaml
guacamole:
  auth:
    totp:
      enabled: true
      totp-issuer: Apache Guacamole
      totp-period: 30
      totp-mode: sha1
      totp-digits: 6
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.totp.enabled` | boolean | 是 | `false` | 启用 TOTP 认证 |
| `guacamole.auth.totp.totp-issuer` | string | 否 | `Apache Guacamole` | 认证器应用中显示的签发者名称 |
| `guacamole.auth.totp.totp-digits` | int | 否 | `6` | 验证码位数（6 或 8） |
| `guacamole.auth.totp.totp-period` | int | 否 | `30` | 验证码有效期（秒） |
| `guacamole.auth.totp.totp-mode` | enum | 否 | `sha1` | 哈希算法：`sha1`、`sha256`、`sha512` |

**注意：** TOTP 必须与其他认证提供者（如 JDBC 或 LDAP）组合使用。首次登录时用户需完成 TOTP 密钥注册。

---

### DUO 双因素认证

```yaml
guacamole:
  auth:
    duo:
      enabled: true
      duo-api-hostname: api-XXXXXXXX.duosecurity.com
      duo-integration-key: ${DUO_IKEY}
      duo-secret-key: ${DUO_SKEY}
      duo-application-key: ${DUO_AKEY}
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.duo.enabled` | boolean | 是 | `false` | 启用 Duo 认证 |
| `guacamole.auth.duo.duo-api-hostname` | string | **是** | -- | Duo API 主机名（如 `api-XXXXXXXX.duosecurity.com`） |
| `guacamole.auth.duo.duo-integration-key` | string | **是** | -- | Duo 集成密钥（恰好 20 字符） |
| `guacamole.auth.duo.duo-secret-key` | string | **是** | -- | Duo 密钥（恰好 40 字符） |
| `guacamole.auth.duo.duo-application-key` | string | **是** | -- | 任意随机密钥（至少 40 字符） |

**重要：** 此扩展使用 Duo Web SDK v2，Duo 已于 2024 年 3 月弃用此 SDK。生产环境需升级至 Web SDK v4（Universal Prompt）。

---

### QuickConnect

通过 Guacamole 界面中的 URI 创建临时即时连接。

```yaml
guacamole:
  auth:
    quickconnect:
      enabled: true
```

**支持的 URI 格式：**

```
rdp://hostname:3389
vnc://hostname:5900
ssh://hostname:22?username=user&password=pass
telnet://hostname:23
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.quickconnect.enabled` | boolean | 是 | `false` | 启用 QuickConnect |
| `guacamole.auth.quickconnect.quickconnect-allowed-parameters` | string（逗号分隔） | 否 | 全部允许 | 允许的连接参数列表 |
| `guacamole.auth.quickconnect.quickconnect-denied-parameters` | string（逗号分隔） | 否 | 无禁止 | 禁止的连接参数列表 |

**参数过滤示例：**

```yaml
guacamole:
  auth:
    quickconnect:
      enabled: true
      quickconnect-allowed-parameters: hostname,port,protocol
      quickconnect-denied-parameters: password,private-key
```

---

### CAS 单点登录

CAS 单点登录认证。

```yaml
guacamole:
  auth:
    sso-cas:
      enabled: true
      cas-authorization-endpoint: https://cas.example.org/cas
      cas-redirect-uri: http://localhost:8080/
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.sso-cas.enabled` | boolean | 是 | `false` | 启用 CAS 认证 |
| `guacamole.auth.sso-cas.cas-authorization-endpoint` | URI | **是** | -- | CAS 服务器认证端点 URL |
| `guacamole.auth.sso-cas.cas-redirect-uri` | URI | **是** | -- | CAS 认证后的回调 URL（Guacamole 访问地址） |
| `guacamole.auth.sso-cas.cas-clearpass-key` | string | 否 | -- | ClearPass 密码解密私钥文件路径 |
| `guacamole.auth.sso-cas.cas-group-attribute` | string | 否 | -- | 组成员身份的 CAS 属性名 |
| `guacamole.auth.sso-cas.cas-group-format` | enum | 否 | `plain` | 组名格式：`plain` 或 `ldap` |
| `guacamole.auth.sso-cas.cas-group-ldap-base-dn` | string | 否 | -- | LDAP 格式组的 Base DN |
| `guacamole.auth.sso-cas.cas-group-ldap-attribute` | string | 否 | `cn` | LDAP 组名属性 |

---

### OpenID Connect 单点登录

OpenID Connect 认证（支持 Google、Okta、Keycloak、Auth0、Azure AD 等）。

```yaml
guacamole:
  auth:
    sso-openid:
      enabled: true
      openid-authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
      openid-jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
      openid-issuer: https://accounts.google.com
      openid-client-id: ${OPENID_CLIENT_ID}
      openid-redirect-uri: http://localhost:8080/
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.sso-openid.enabled` | boolean | 是 | `false` | 启用 OpenID Connect 认证 |
| `guacamole.auth.sso-openid.openid-authorization-endpoint` | URI | **是** | -- | OpenID 认证端点 URL |
| `guacamole.auth.sso-openid.openid-jwks-endpoint` | URI | **是** | -- | JWKS 端点 URL，用于 JWT 校验 |
| `guacamole.auth.sso-openid.openid-issuer` | URI | **是** | -- | JWT 签发者 |
| `guacamole.auth.sso-openid.openid-client-id` | string | **是** | -- | OpenID 客户端 ID |
| `guacamole.auth.sso-openid.openid-redirect-uri` | URI | **是** | -- | OpenID 认证后回调 URL |
| `guacamole.auth.sso-openid.openid-scope` | string | 否 | `openid email profile` | 空格分隔的 OpenID 作用域 |
| `guacamole.auth.sso-openid.openid-username-claim-type` | string | 否 | `email` | JWT 中用户名字段的声明类型 |
| `guacamole.auth.sso-openid.openid-groups-claim-type` | string | 否 | `groups` | JWT 中组成员声明类型 |
| `guacamole.auth.sso-openid.openid-allowed-clock-skew` | int | 否 | `30` | 允许的时钟偏差（秒） |
| `guacamole.auth.sso-openid.openid-max-token-validity` | int | 否 | `300` | 最大令牌有效期（分钟，默认 5 小时） |
| `guacamole.auth.sso-openid.openid-max-nonce-validity` | int | 否 | `10` | 最大 nonce 有效期（分钟） |

**各提供商配置示例：**

Google：

```yaml
openid-authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
openid-jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
openid-issuer: https://accounts.google.com
openid-username-claim-type: email
```

Okta：

```yaml
openid-authorization-endpoint: https://dev-XXXX.okta.com/oauth2/v1/authorize
openid-jwks-endpoint: https://dev-XXXX.okta.com/oauth2/v1/keys
openid-issuer: https://dev-XXXX.okta.com
openid-username-claim-type: preferred_username
```

Keycloak：

```yaml
openid-authorization-endpoint: https://keycloak.example.com/realms/myrealm/protocol/openid-connect/auth
openid-jwks-endpoint: https://keycloak.example.com/realms/myrealm/protocol/openid-connect/certs
openid-issuer: https://keycloak.example.com/realms/myrealm
```

---

### SAML 2.0 单点登录

SAML 2.0 单点登录，支持 IdP 元数据 URL 和手动配置两种方式。

```yaml
guacamole:
  auth:
    sso-saml:
      enabled: true
      saml-callback-url: http://localhost:8080/
      # 方式 A：IdP 元数据 URL（推荐）
      saml-idp-metadata-url: https://idp.example.com/metadata.xml
      # 方式 B：手动配置（无元数据时）
      saml-idp-url: https://idp.example.com/sso
      saml-entity-id: http://localhost:8080/
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.auth.sso-saml.enabled` | boolean | 是 | `false` | 启用 SAML 认证 |
| `guacamole.auth.sso-saml.saml-callback-url` | URI | **是** | -- | SAML 回调基础 URL（Guacamole 访问地址） |
| `guacamole.auth.sso-saml.saml-idp-metadata-url` | URI | 条件必填 | -- | IdP 元数据 XML URL（方式 A） |
| `guacamole.auth.sso-saml.saml-idp-url` | URI | 条件必填 | -- | IdP SSO 登录 URL（方式 B） |
| `guacamole.auth.sso-saml.saml-entity-id` | URI | 条件必填 | -- | SP 实体 ID（元数据中未提供时需要） |
| `guacamole.auth.sso-saml.saml-strict` | boolean | 否 | `true` | 强制严格安全检查 |
| `guacamole.auth.sso-saml.saml-debug` | boolean | 否 | `false` | 启用 SAML 调试日志 |
| `guacamole.auth.sso-saml.saml-compress-request` | boolean | 否 | `true` | 压缩 SAML 认证请求 |
| `guacamole.auth.sso-saml.saml-compress-response` | boolean | 否 | `true` | 请求压缩 SAML 响应 |
| `guacamole.auth.sso-saml.saml-group-attribute` | string | 否 | `groups` | IdP 响应中组成员属性名 |
| `guacamole.auth.sso-saml.saml-auth-timeout` | int | 否 | `5` | SAML 认证超时（分钟） |

---

## 功能扩展模块配置

### History 会话录像

存储会话录像和连接历史记录。

```yaml
guacamole:
  history:
    enabled: true
    recording-search-path: /var/lib/guacamole/recordings
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.history.enabled` | boolean | 是 | `false` | 启用 History 录像 |
| `guacamole.history.recording-search-path` | string | 否 | `/var/lib/guacamole/recordings` | 录像文件搜索路径 |

**默认值说明：** 代码层面默认值为 `/var/lib/guacamole/recordings`。默认 `application.yml` 中设置为 `${GUACAMOLE_HISTORY_RECORDING_PATH:/tmp/guacamole/recordings}`，因此有效默认值为 `/tmp/guacamole/recordings`（可通过 `GUACAMOLE_HISTORY_RECORDING_PATH` 环境变量覆盖）。

**注意：** 会话录像由 guacd 生成。`recording-search-path` 必须指向 guacd 写入录像文件的目录，Guacamole Web 应用需对此目录有读权限。

**Docker 共享卷：**

```yaml
volumes:
  - /host/path/recordings:/tmp/guacamole/recordings
```

---

### Vault KSM 密钥管理

Keeper Secrets Manager 凭据注入集成。

```yaml
guacamole:
  vault:
    ksm:
      enabled: true
      ksm-config: "keeper://<base64-encoded-config>"
      ksm-allow-unverified-cert: false
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `guacamole.vault.ksm.enabled` | boolean | 是 | `false` | 启用 KSM Vault |
| `guacamole.vault.ksm.ksm-config` | string | **是** | -- | Keeper Commander CLI 生成的 base64 编码配置 |
| `guacamole.vault.ksm.ksm-allow-unverified-cert` | boolean | 否 | `false` | 接受未验证的服务器证书 |

KSM 模块还读取以下文件：

- **令牌映射文件：** `<GUACAMOLE_HOME>/ksm-token-mapping.yml` -- 将连接参数令牌映射到 Keeper 密钥名
- **属性文件：** `<GUACAMOLE_HOME>/guacamole.properties.ksm` -- 将 Guacamole 属性名映射到 Keeper 密钥名

详见 [vault-module.md](vault-module.md)。

---

## 系统配置模块（运行时可改）

系统配置模块将品牌定制、主题配色、安全策略和公告管理存入数据库，支持管理员通过 Web 界面实时修改，无需重启应用。需要启用至少一个 JDBC 扩展（PostgreSQL/MySQL/SQL Server）。

### 前置条件

1. 执行 `003-create-system-config.sql` DDL 创建 `guacamole_system_config` 和 `guacamole_system_file` 表
2. `application.yml` 中启用 JDBC 扩展（如 `guacamole.auth.postgresql.enabled: true`）

### 品牌定制（branding）

| config_key | 类型 | 默认值 | 说明 |
|------------|------|--------|------|
| `branding.site_name` | string | Apache Guacamole | 应用全称（显示在登录页和浏览器标题） |
| `branding.site_name_short` | string | -- | 应用短称 |
| `branding.logo` | file | -- | 登录页 Logo（亮色） |
| `branding.logo_dark` | file | -- | 登录页 Logo（暗色） |
| `branding.favicon` | file | -- | 浏览器标签页图标 |
| `branding.login_background` | file | -- | 登录页背景图 |
| `branding.copyright` | string | -- | 页面底部版权信息 |
| `branding.support_url` | url | -- | 技术支持链接 |
| `branding.help_url` | url | -- | 帮助文档链接 |

### 主题配色（theme）

| config_key | 类型 | 默认值 | 说明 |
|------------|------|--------|------|
| `theme.primary_color` | string | #1a56db | 品牌主色（#RRGGBB） |
| `theme.accent_color` | string | #0694a2 | 品牌辅色 |
| `theme.success_color` | string | #057a55 | 成功色 |
| `theme.warning_color` | string | #f59e0b | 警告色 |
| `theme.danger_color` | string | #e02424 | 危险色 |
| `theme.mode` | enum | light | 亮色模式：`light`；暗色模式：`dark`；跟随系统：`auto` |

主题配色通过 CSS 变量驱动，主色推导出 35 个变量，覆盖按钮、导航、背景、文字、边框、阴影等所有视觉元素。

### 安全策略（security）— 已接入后端 PasswordPolicy

| config_key | 类型 | 默认值 | 说明 |
|------------|------|--------|------|
| `security.password_min_length` | integer | 8 | 密码最小长度 |
| `security.password_require_uppercase` | boolean | true | 要求包含大写字母 |
| `security.password_require_number` | boolean | true | 要求数字 |
| `security.password_require_special` | boolean | false | 要求特殊字符 |

以上 4 项已接入三数据库（PostgreSQL/MySQL/SQL Server）的 `PasswordPolicy` 实现。修改后**即时生效**——下次用户创建或修改密码时即按新规则校验。规则：

- DB 有值 → 使用 DB 值
- DB 无值 → fallback 到 `application.yml` 中 `postgresql-user-password-min-length` 等旧属性
- 旧属性也无值 → 不限制

### 系统公告（announcement）

| config_key | 类型 | 默认值 | 说明 |
|------------|------|--------|------|
| `announcement.message` | text | -- | 公告内容 |
| `announcement.level` | enum | info | `info`（蓝）/ `warning`（橙）/ `error`（红） |
| `announcement.enabled` | boolean | false | 是否启用 |
| `announcement.start_time` | datetime | -- | 生效开始时间（UTC，不显示则始终有效） |
| `announcement.end_time` | datetime | -- | 生效结束时间（UTC，不显示则永远有效） |
| `announcement.closable` | boolean | true | 是否允许用户关闭公告 |

公告显示为页面顶部单行横幅。`closable=true` 时用户可关闭，关闭状态保存在 `localStorage` 中，刷新不重显；管理员修改公告内容后自动重新显示。`start_time`/`end_time` 控制公告仅在指定时间窗口内显示。

### 文件存储

品牌定制中的 Logo、Favicon、背景图等通过文件上传组件管理，文件存储在服务器文件系统，元数据记录在 `guacamole_system_file` 表中。

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `guacamole.system.file-storage-path` | `${user.dir}/files` | 文件存储根目录，支持环境变量 |

支持格式：SVG、PNG、JPG、ICO、GIF，单文件最大 2MB。

### 配置优先级

```
数据库 guacamole_system_config 表（运行时修改）
    ↓ 无值时 fallback
application.yml 的 guacamole.system.defaults 段（构建时配置）
    ↓ 无值时 fallback
Guacamole 原生 guacamole.properties 属性
```

---

## 多认证链配置

可以同时启用多个认证提供者。Guacamole 按顺序依次尝试每个提供者：

```
登录请求
  -> 提供者 1（如 LDAP）-> 成功 -> 用户登录
  -> 提供者 1 失败
  -> 提供者 2（如 PostgreSQL）-> 成功 -> 用户登录
  -> 提供者 2 失败
  -> 提供者 3（如 JSON）-> 成功 -> 用户登录
  -> 全部失败 -> 登录拒绝
```

### 互斥规则

**启动时强制校验**（违规将导致应用启动失败）：

| 互斥组 | 规则 |
|--------|------|
| JDBC 后端 | `mysql`、`postgresql`、`sqlserver` 最多只能启用一个 |
| SSO 提供者 | `sso-cas`、`sso-openid`、`sso-saml` 最多只能启用一个 |

**允许的组合示例：**

- JDBC + LDAP + TOTP -- 允许
- LDAP + JSON + QuickConnect -- 允许
- PostgreSQL + CAS -- 允许
- PostgreSQL + RADIUS -- 允许（RADIUS 作为外部认证源，需配合 `auto-create-accounts: true` 自动创建数据库账户）
- MySQL + PostgreSQL -- **拒绝**（JDBC 冲突）
- CAS + OpenID -- **拒绝**（SSO 冲突）

### 外部认证源与自动创建账户

RADIUS、LDAP、Header、SSO 等外部认证源只负责**验证密码**，不管理用户授权。认证成功后，JDBC 模块的 `getUserContext()` 会检查用户是否在数据库中存在：

| `auto-create-accounts` | 外部认证成功后 | 结果 |
|-----------------------|-------------|------|
| `false`（默认） | 用户不存在于数据库 | 能登录但无连接权限（白屏） |
| `true` | 用户不存在于数据库 | 自动在 `guacamole_user` 表中创建账号，登录成功 |
| `true` | 用户已存在于数据库 | 直接使用现有账号（保留已有权限） |

> **注意：** 自动创建的账号没有任何连接权限，管理员需在 Guacamole 管理界面手动分配权限。

### 示例：LDAP + PostgreSQL + TOTP

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ldap.example.com
      ldap-user-base-dn: ou=users,dc=example,dc=com
      ldap-username-attribute: uid
    postgresql:
      enabled: true
    totp:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

此配置效果：
1. LDAP 用户先通过 LDAP 认证，然后完成 TOTP 验证
2. 非 LDAP 用户回退到 PostgreSQL 认证
3. TOTP 在所有用户完成主认证后强制执行

### 示例：Header 认证 + JDBC

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: REMOTE_USER
    postgresql:
      enabled: true
      postgresql-auto-create-accounts: true

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

反向代理认证的用户通过 `auto-create-accounts: true` 自动创建数据库账户，权限通过数据库管理。

### 示例：纯 SSO

```yaml
guacamole:
  auth:
    sso-openid:
      enabled: true
      openid-authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
      openid-jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
      openid-issuer: https://accounts.google.com
      openid-client-id: ${OPENID_CLIENT_ID}
      openid-redirect-uri: https://guacamole.example.com/
```

纯 SSO 模式下，连接和权限需通过 JDBC 后端管理，或通过 SSO 提供者内置的连接支持。

---

## 环境变量覆盖

应用支持 Spring Boot 约定方式的环境变量覆盖。

| 环境变量 | 映射属性 | 默认值 |
|---------|---------|--------|
| `SERVER_PORT` | `server.port` | `8080` |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | （来自 `application.yml`） |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | （来自 `application.yml`） |
| `GUACAMOLE_DB_PASSWORD` | `spring.datasource.password` | `guacamole` |
| `GUACAMOLE_HISTORY_RECORDING_PATH` | `guacamole.history.recording-search-path` | `/tmp/guacamole/recordings` |
| `OPENID_CLIENT_ID` | `guacamole.auth.sso-openid.openid-client-id` | -- |
| `DUO_IKEY` | `guacamole.auth.duo.duo-integration-key` | -- |
| `DUO_SKEY` | `guacamole.auth.duo.duo-secret-key` | -- |
| `DUO_AKEY` | `guacamole.auth.duo.duo-application-key` | -- |
| `RADIUS_SECRET` | `guacamole.auth.radius.radius-shared-secret` | -- |
| `GUACAMOLE_HOME` | 运行时环境变量 | `~/.guacamole` |

任何 Spring Boot 属性都可以通过大写下划线标记法覆盖（如 `GUACAMOLE_AUTH_LDAP_LDAP_HOSTNAME=ldap.example.com`、`GUACAMOLE_AUTH_LDAP_ENABLED=true`）。

**Docker 环境：**

```bash
docker run -e GUACAMOLE_DB_PASSWORD=secret123 \
           -e SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/guacamole \
           -p 8080:8080 \
           guacamole:latest
```

---

## 常见配置场景

### 场景 1：LDAP + PostgreSQL 组合认证

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ad.company.com
      ldap-port: 389
      ldap-username-attribute: sAMAccountName
      ldap-user-base-dn: ou=Users,dc=company,dc=com
      ldap-search-bind-dn: cn=guacadmin,cn=Users,dc=company,dc=com
      ldap-search-bind-password: ${LDAP_BIND_PASSWORD}
    postgresql:
      enabled: true
      auto-create-accounts: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

效果：用户通过 AD 认证，数据库自动创建账户，权限在数据库中管理。

### 场景 2：纯 SSO + JDBC 后端

```yaml
guacamole:
  auth:
    sso-openid:
      enabled: true
      openid-authorization-endpoint: https://keycloak.company.com/realms/company/protocol/openid-connect/auth
      openid-jwks-endpoint: https://keycloak.company.com/realms/company/protocol/openid-connect/certs
      openid-issuer: https://keycloak.company.com/realms/company
      openid-client-id: ${OPENID_CLIENT_ID}
      openid-redirect-uri: https://guacamole.company.com/
    postgresql:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
```

效果：用户通过 Keycloak SSO 登录，数据库管理连接配置和权限。

### 场景 3：多因素认证（LDAP + TOTP）

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ldap.company.com
      ldap-user-base-dn: ou=users,dc=company,dc=com
    totp:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
```

效果：用户通过 LDAP 认证密码后，还需输入 TOTP 验证码。

### 场景 4：API 令牌认证（JSON + JDBC）

```yaml
guacamole:
  auth:
    json:
      enabled: true
      json-secret-key: ${JSON_SECRET_KEY}
      json-trusted-networks: 10.0.0.0/8,172.16.0.0/12
    postgresql:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
```

效果：允许来自内网的 API 令牌认证，同时支持数据库用户登录。

### 场景 5：反向代理 SSO（Header + JDBC）

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: X-Forwarded-User
    postgresql:
      enabled: true
      auto-create-accounts: true

spring:
  datasource:
    url: jdbc:postgresql://db.company.com:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
```

效果：Nginx/Apache 认证后传递用户头信息，Guacamole 自动创建数据库账户。

### 场景 6：Docker Compose 完整部署

```yaml
version: "3.8"
services:
  guacamole:
    image: guacamole:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/guacamole
      SPRING_DATASOURCE_USERNAME: guacamole
      GUACAMOLE_DB_PASSWORD: secret123
      GUACAMOLE_AUTH_LDAP_ENABLED: "true"
      GUACAMOLE_AUTH_LDAP_LDAP_HOSTNAME: ldap.company.com
      GUACAMOLE_AUTH_LDAP_LDAP_USER_BASE_DN: ou=users,dc=company,dc=com
      GUACAMOLE_AUTH_TOTP_ENABLED: "true"
    volumes:
      - recordings:/tmp/guacamole/recordings

  guacd:
    image: guacd:latest
    ports:
      - "4822:4822"

  db:
    image: postgres:15
    environment:
      POSTGRES_DB: guacamole
      POSTGRES_USER: guacamole
      POSTGRES_PASSWORD: secret123
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
  recordings:
```

此配置部署了 Guacamole + guacd + PostgreSQL，启用了 LDAP 认证和 TOTP 双因素认证。
