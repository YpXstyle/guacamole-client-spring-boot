# 迁移指南：Apache Guacamole 1.5.5 至 Spring Boot 3.3.5

本文档记录了从原版 Apache Guacamole 1.5.5（Google Guice + WAR + guacamole.properties）迁移至 Spring Boot 3.3.5 版本（`com.right` groupId）所需的全部变更。

以下所有属性映射已针对本仓库中的实际 Java 源代码（ConfigurationService 和 GuacamoleProperties 类）验证。

---

## 目录

- [迁移了什么](#迁移了什么)
- [迁移状态](#迁移状态)
- [完整属性映射表](#完整属性映射表)
  - [核心 guacd](#核心-guacd)
  - [JDBC PostgreSQL](#jdbc-postgresql)
  - [JDBC MySQL](#jdbc-mysql)
  - [JDBC SQL Server](#jdbc-sql-server)
  - [LDAP](#ldap)
  - [RADIUS](#radius)
  - [TOTP](#totp)
  - [DUO](#duo)
  - [Header HTTP 认证](#header-http-认证)
  - [JSON](#json)
  - [QuickConnect](#quickconnect)
  - [SSO / CAS](#sso--cas)
  - [SSO / OpenID Connect](#sso--openid-connect)
  - [SSO / SAML 2.0](#sso--saml-20)
  - [Vault / KSM](#vault--ksm)
  - [History 录像](#history-录像)
- [扩展加载方式变更](#扩展加载方式变更)
- [数据库迁移](#数据库迁移)
- [部署方式变更](#部署方式变更)
- [API 兼容性](#api-兼容性)
- [破坏性变更](#破坏性变更)
- [自定义扩展迁移](#自定义扩展迁移)

---

## 迁移了什么

| 方面 | 原版（1.5.5） | 迁移后 |
|------|---------------|--------|
| 基础框架 | Apache Guacamole 1.5.5 | Spring Boot 3.3.5 |
| Java 版本 | Java 8 / 11 | JDK 17 |
| 依赖注入 | Google Guice（`@Inject`） | Spring IoC（`@Autowired`） |
| Servlet API | `javax.servlet.*` | `jakarta.servlet.*`（Jakarta EE 9+） |
| 配置文件 | `guacamole.properties` | `application.yml` |
| 部署产物 | WAR（部署到 Tomcat/Jetty） | Spring Boot Fat JAR（内嵌 Tomcat） |
| 扩展加载 | `GUACAMOLE_HOME/extensions/` 目录 JAR | Maven 依赖在 classpath |
| Maven Group ID | `org.apache.guacamole` | `com.right` |
| 构建系统 | Maven（原版 parent POM） | Spring Boot parent POM（`spring-boot-starter-parent`） |

### 关键技术升级

- **Jakarta EE**：所有 `javax.*` 导入替换为 `jakarta.*`（JAX-RS 3.1.0、Servlet 6.0、Annotation 2.1）
- **Guava**：升级至 32.1.3-jre
- **Jackson**：升级至 2.17.2
- **MyBatis**：升级至 MyBatis Spring Boot Starter 3.0.3
- **Jose4j**：0.9.6 用于 JWT 处理
- **OneLogin SAML**：2.9.0
- **CAS Client**：3.6.4
- **Keeper KSM SDK**：16.6.3 含 Kotlin 1.9.23 支持
- **Bouncy Castle FIPS**：1.0.2.4

---

## 迁移状态

| 模块 | 状态 | 说明 |
|------|------|------|
| guacamole-common | 完成 | 核心协议库，无迁移问题 |
| guacamole-common-js | 完成 | JavaScript 客户端，通过 frontend-maven-plugin 构建 |
| guacamole-ext | 完成 | 扩展 API 移植为 Spring 友好 SPI |
| guacamole（webapp） | 完成 | 主 Spring Boot 应用，内嵌 Tomcat |
| guacamole-auth-jdbc-base | 完成 | 共享 JDBC 认证基础设施 |
| guacamole-auth-mysql-starter | 完成 | MySQL JDBC 认证 |
| guacamole-auth-postgresql-starter | 完成 | PostgreSQL JDBC 认证 |
| guacamole-auth-sqlserver-starter | 完成 | SQL Server JDBC 认证 |
| guacamole-auth-header-starter | 完成 | HTTP 请求头认证 |
| guacamole-auth-json-starter | 完成 | JSON/API 令牌认证 |
| guacamole-auth-ldap-starter | 完成 | LDAP/Active Directory 认证 |
| guacamole-auth-totp-starter | 完成 | TOTP 多因素认证 |
| guacamole-auth-radius-starter | 完成 | RADIUS 多因素认证 |
| guacamole-auth-quickconnect-starter | 完成 | QuickConnect 功能 |
| guacamole-auth-duo-starter | 需更新 | 使用已弃用的 Duo SDK v2，需 v4 SDK 升级 |
| guacamole-auth-sso-cas-starter | 完成 | CAS 单点登录 |
| guacamole-auth-sso-openid-starter | 完成 | OpenID Connect 单点登录 |
| guacamole-auth-sso-saml-starter | 完成 | SAML 2.0 单点登录 |
| guacamole-vault-ksm-starter | 待测试 | Keeper Secrets Manager 集成，需 KSM 账户测试 |
| guacamole-history-starter | 完成 | 连接历史和录像搜索 |

### 状态含义

- **完成**：功能完整，已编译验证
- **需更新**：编译通过但存在问题。DUO 使用已弃用的 Duo SDK v2（`DuoWeb`），需升级至 Duo SDK v4（Universal Prompt）
- **待测试**：代码完整可编译，端到端测试需外部凭据（KSM 账户）尚不可用

---

## 完整属性映射表

以下每个属性已对照对应的 `*GuacamoleProperties.java` 和 `*Environment.java` 源文件验证。标记为**必填**的属性缺失时将导致启动错误。

`guacamole.properties` 中无法识别的属性会被静默忽略。迁移后的 `application.yml` 使用作用域命名空间，因此模块间无键名冲突风险。

### 核心 guacd

**源文件：** `guacamole-ext/.../environment/Environment.java` 和 `LocalEnvironment.java`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `guacd-hostname` | `guacamole.guacd.hostname` | `localhost` | 否 |
| `guacd-port` | `guacamole.guacd.port` | `4822` | 否 |
| `guacd-ssl` | `guacamole.guacd.ssl` | `false` | 否 |

```yaml
guacamole:
  guacd:
    hostname: localhost
    port: 4822
    ssl: false
```

---

### JDBC / PostgreSQL

**源文件：** `guacamole-auth-postgresql-starter/.../conf/PostgreSQLGuacamoleProperties.java`、`PostgreSQLEnvironment.java`

**前缀：** `guacamole.auth.postgresql.`

#### 活跃属性（仍用于业务逻辑）

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `postgresql-user-required` | `guacamole.auth.postgresql.user-required` | `false` | 否 |
| `postgresql-absolute-max-connections` | `guacamole.auth.postgresql.absolute-max-connections` | `0` | 否 |
| `postgresql-default-max-connections` | `guacamole.auth.postgresql.default-max-connections` | `0` | 否 |
| `postgresql-default-max-group-connections` | `guacamole.auth.postgresql.default-max-group-connections` | `0` | 否 |
| `postgresql-default-max-connections-per-user` | `guacamole.auth.postgresql.default-max-connections-per-user` | `0` | 否 |
| `postgresql-default-max-group-connections-per-user` | `guacamole.auth.postgresql.default-max-group-connections-per-user` | `1` | 否 |
| `postgresql-batch-size` | `guacamole.auth.postgresql.batch-size` | `5000` | 否 |
| `postgresql-auto-create-accounts` | `guacamole.auth.postgresql.auto-create-accounts` | `false` | 否 |

#### 僵尸属性（不再用于数据库连接）

以下属性**在源代码中定义但不再被任何业务代码调用**。它们仅存于 `PostgreSQLEnvironment` 类中。实际数据库连接完全由 `spring.datasource.*` 管理。

| guacamole.properties | 状态 | 迁移方案 |
|---------------------|------|----------|
| `postgresql-hostname` | 僵尸 | 使用 `spring.datasource.url` |
| `postgresql-port` | 僵尸 | 使用 `spring.datasource.url` |
| `postgresql-database` | 僵尸 | 使用 `spring.datasource.url` |
| `postgresql-username` | 僵尸 | 使用 `spring.datasource.username` |
| `postgresql-password` | 僵尸 | 使用 `spring.datasource.password` |
| `postgresql-ssl-mode` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `postgresql-ssl-cert-file` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `postgresql-ssl-key-file` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `postgresql-ssl-root-cert-file` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `postgresql-ssl-key-password` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `postgresql-default-statement-timeout` | 僵尸 | 使用 DataSource 配置 |
| `postgresql-socket-timeout` | 僵尸 | 使用 DataSource 配置 |

```yaml
guacamole:
  auth:
    postgresql:
      enabled: true
      user-required: false
      batch-size: 5000
      auto-create-accounts: false

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${PG_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

---

### JDBC / MySQL

**源文件：** `guacamole-auth-mysql-starter/.../conf/MySQLGuacamoleProperties.java`、`MySQLEnvironment.java`

**前缀：** `guacamole.auth.mysql.`

#### 活跃属性（仍用于业务逻辑）

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `mysql-user-required` | `guacamole.auth.mysql.user-required` | `false` | 否 |
| `mysql-absolute-max-connections` | `guacamole.auth.mysql.absolute-max-connections` | `0` | 否 |
| `mysql-default-max-connections` | `guacamole.auth.mysql.default-max-connections` | `0` | 否 |
| `mysql-default-max-group-connections` | `guacamole.auth.mysql.default-max-group-connections` | `0` | 否 |
| `mysql-default-max-connections-per-user` | `guacamole.auth.mysql.default-max-connections-per-user` | `0` | 否 |
| `mysql-default-max-group-connections-per-user` | `guacamole.auth.mysql.default-max-group-connections-per-user` | `1` | 否 |
| `mysql-batch-size` | `guacamole.auth.mysql.batch-size` | `1000` | 否 |
| `mysql-auto-create-accounts` | `guacamole.auth.mysql.auto-create-accounts` | `false` | 否 |

#### 僵尸属性（不再用于数据库连接）

| guacamole.properties | 状态 | 迁移方案 |
|---------------------|------|----------|
| `mysql-hostname` | 僵尸 | 使用 `spring.datasource.url` |
| `mysql-port` | 僵尸 | 使用 `spring.datasource.url` |
| `mysql-database` | 僵尸 | 使用 `spring.datasource.url` |
| `mysql-username` | 僵尸 | 使用 `spring.datasource.username` |
| `mysql-password` | 僵尸 | 使用 `spring.datasource.password` |
| `mysql-driver` | 僵尸 | 使用 `spring.datasource.driver-class-name` |
| `mysql-ssl-mode` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `mysql-ssl-trust-store` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `mysql-ssl-trust-password` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `mysql-ssl-client-store` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `mysql-ssl-client-password` | 僵尸 | 使用 `spring.datasource.url` 参数 |
| `mysql-server-timezone` | 僵尸 | 使用 `spring.datasource.url` 参数 |

```yaml
guacamole:
  auth:
    mysql:
      enabled: true
      user-required: false
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

### JDBC / SQL Server

**源文件：** `guacamole-auth-sqlserver-starter/.../conf/SQLServerGuacamoleProperties.java`、`SQLServerEnvironment.java`

**前缀：** `guacamole.auth.sqlserver.`

#### 活跃属性（仍用于业务逻辑）

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `sqlserver-user-required` | `guacamole.auth.sqlserver.user-required` | `false` | 否 |
| `sqlserver-absolute-max-connections` | `guacamole.auth.sqlserver.absolute-max-connections` | `0` | 否 |
| `sqlserver-default-max-connections` | `guacamole.auth.sqlserver.default-max-connections` | `0` | 否 |
| `sqlserver-default-max-group-connections` | `guacamole.auth.sqlserver.default-max-group-connections` | `0` | 否 |
| `sqlserver-default-max-connections-per-user` | `guacamole.auth.sqlserver.default-max-connections-per-user` | `0` | 否 |
| `sqlserver-default-max-group-connections-per-user` | `guacamole.auth.sqlserver.default-max-group-connections-per-user` | `1` | 否 |
| `sqlserver-batch-size` | `guacamole.auth.sqlserver.batch-size` | `500` | 否 |
| `sqlserver-auto-create-accounts` | `guacamole.auth.sqlserver.auto-create-accounts` | `false` | 否 |

#### 僵尸属性（不再用于数据库连接）

| guacamole.properties | 状态 | 迁移方案 |
|---------------------|------|----------|
| `sqlserver-hostname` | 僵尸 | 使用 `spring.datasource.url` |
| `sqlserver-port` | 僵尸 | 使用 `spring.datasource.url` |
| `sqlserver-database` | 僵尸 | 使用 `spring.datasource.url` |
| `sqlserver-username` | 僵尸 | 使用 `spring.datasource.username` |
| `sqlserver-password` | 僵尸 | 使用 `spring.datasource.password` |
| `sqlserver-driver` | 僵尸 | 使用 `spring.datasource.driver-class-name` |
| `sqlserver-instance` | 僵尸 | 使用 `spring.datasource.url` 参数 |

```yaml
guacamole:
  auth:
    sqlserver:
      enabled: true
      user-required: false
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

### LDAP

**源文件：** `guacamole-auth-ldap-starter/.../conf/LDAPGuacamoleProperties.java`、`EnvironmentLDAPConfiguration.java`、`DefaultLDAPConfiguration.java`

**前缀：** `guacamole.auth.ldap.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `ldap-hostname` | `guacamole.auth.ldap.ldap-hostname` | `localhost` | 否 |
| `ldap-port` | `guacamole.auth.ldap.ldap-port` | 取决于加密方法 | 否 |
| `ldap-encryption-method` | `guacamole.auth.ldap.ldap-encryption-method` | `none` | 否 |
| `ldap-user-base-dn` | `guacamole.auth.ldap.ldap-user-base-dn` | -- | **是** |
| `ldap-username-attribute` | `guacamole.auth.ldap.ldap-username-attribute` | `uid` | 否 |
| `ldap-search-bind-dn` | `guacamole.auth.ldap.ldap-search-bind-dn` | -- | 否 |
| `ldap-search-bind-password` | `guacamole.auth.ldap.ldap-search-bind-password` | -- | 否 |
| `ldap-user-search-filter` | `guacamole.auth.ldap.ldap-user-search-filter` | `(objectClass=*)` | 否 |
| `ldap-config-base-dn` | `guacamole.auth.ldap.ldap-config-base-dn` | -- | 否 |
| `ldap-group-base-dn` | `guacamole.auth.ldap.ldap-group-base-dn` | -- | 否 |
| `ldap-group-name-attribute` | `guacamole.auth.ldap.ldap-group-name-attribute` | `cn` | 否 |
| `ldap-group-search-filter` | `guacamole.auth.ldap.ldap-group-search-filter` | `(objectClass=*)` | 否 |
| `ldap-member-attribute` | `guacamole.auth.ldap.ldap-member-attribute` | `member` | 否 |
| `ldap-member-attribute-type` | `guacamole.auth.ldap.ldap-member-attribute-type` | `dn` | 否 |
| `ldap-max-search-results` | `guacamole.auth.ldap.ldap-max-search-results` | `1000` | 否 |
| `ldap-operation-timeout` | `guacamole.auth.ldap.ldap-operation-timeout` | `30`（秒） | 否 |
| `ldap-network-timeout` | `guacamole.auth.ldap.ldap-network-timeout` | `30000`（毫秒） | 否 |
| `ldap-follow-referrals` | `guacamole.auth.ldap.ldap-follow-referrals` | `false` | 否 |
| `ldap-max-referral-hops` | `guacamole.auth.ldap.ldap-max-referral-hops` | `5` | 否 |
| `ldap-dereference-aliases` | `guacamole.auth.ldap.ldap-dereference-aliases` | `never` | 否 |
| `ldap-user-attributes` | `guacamole.auth.ldap.ldap-user-attributes` | （空） | 否 |

**加密方法与默认端口：** `none` 时默认端口 `389`，`ssl`（LDAPS）时默认端口 `636`，`starttls` 时默认端口 `389`。

```yaml
guacamole:
  auth:
    ldap:
      enabled: true
      ldap-hostname: ldap.example.com
      ldap-port: 389
      ldap-encryption-method: starttls
      ldap-user-base-dn: dc=example,dc=com
      ldap-username-attribute: sAMAccountName
      ldap-search-bind-dn: cn=admin,dc=example,dc=com
      ldap-search-bind-password: ${LDAP_BIND_PASSWORD}
      ldap-user-search-filter: (objectClass=person)
      ldap-group-base-dn: ou=Groups,dc=example,dc=com
      ldap-member-attribute: member
```

---

### RADIUS

**源文件：** `guacamole-auth-radius-starter/.../conf/RadiusGuacamoleProperties.java`、`ConfigurationService.java`

**前缀：** `guacamole.auth.radius.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `radius-hostname` | `guacamole.auth.radius.radius-hostname` | `localhost` | 否 |
| `radius-auth-port` | `guacamole.auth.radius.radius-auth-port` | `1812` | 否 |
| `radius-acct-port` | `guacamole.auth.radius.radius-acct-port` | `1813` | 否 |
| `radius-shared-secret` | `guacamole.auth.radius.radius-shared-secret` | -- | **是** |
| `radius-auth-protocol` | `guacamole.auth.radius.radius-auth-protocol` | -- | **是** |
| `radius-max-retries` | `guacamole.auth.radius.radius-max-retries` | `5` | 否 |
| `radius-timeout` | `guacamole.auth.radius.radius-timeout` | `60`（秒） | 否 |
| `radius-nas-ip` | `guacamole.auth.radius.radius-nas-ip` | （自动检测） | 否 |
| `radius-trust-all` | `guacamole.auth.radius.radius-trust-all` | `false` | 否 |
| `radius-ca-file` | `guacamole.auth.radius.radius-ca-file` | `GUACAMOLE_HOME/ca.crt` | 否 |
| `radius-ca-type` | `guacamole.auth.radius.radius-ca-type` | `pem` | 否 |
| `radius-ca-password` | `guacamole.auth.radius.radius-ca-password` | -- | 否 |
| `radius-key-file` | `guacamole.auth.radius.radius-key-file` | `GUACAMOLE_HOME/radius.key` | 否 |
| `radius-key-type` | `guacamole.auth.radius.radius-key-type` | `pem` | 否 |
| `radius-key-password` | `guacamole.auth.radius.radius-key-password` | -- | 否 |
| `radius-eap-ttls-inner-protocol` | `guacamole.auth.radius.radius-eap-ttls-inner-protocol` | -- | 仅 EAP-TTLS |

```yaml
guacamole:
  auth:
    radius:
      enabled: true
      radius-hostname: radius.example.com
      radius-auth-port: 1812
      radius-shared-secret: ${RADIUS_SECRET}
      radius-auth-protocol: PAP
      radius-timeout: 30
      radius-nas-ip: 192.168.1.100
```

---

### TOTP

**源文件：** `guacamole-auth-totp-starter/.../conf/ConfigurationService.java`

**前缀：** `guacamole.auth.totp.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `totp-issuer` | `guacamole.auth.totp.totp-issuer` | `Apache Guacamole` | 否 |
| `totp-digits` | `guacamole.auth.totp.totp-digits` | `6` | 否 |
| `totp-period` | `guacamole.auth.totp.totp-period` | `30`（秒） | 否 |
| `totp-mode` | `guacamole.auth.totp.totp-mode` | `sha1` | 否 |

`totp-mode` 接受：`sha1`、`sha256`、`sha512`。
`totp-digits` 必须在 6 至 8 之间（含）。

```yaml
guacamole:
  auth:
    totp:
      enabled: true
      totp-issuer: My Company
      totp-period: 30
      totp-mode: sha256
      totp-digits: 6
```

---

### DUO

**源文件：** `guacamole-auth-duo-starter/.../conf/ConfigurationService.java`

**前缀：** `guacamole.auth.duo.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `duo-api-hostname` | `guacamole.auth.duo.duo-api-hostname` | -- | **是** |
| `duo-integration-key` | `guacamole.auth.duo.duo-integration-key` | -- | **是** |
| `duo-secret-key` | `guacamole.auth.duo.duo-secret-key` | -- | **是** |
| `duo-application-key` | `guacamole.auth.duo.duo-application-key` | -- | **是** |

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

**警告：** 迁移后的 DUO 模块使用已弃用的 Duo SDK v2（`DuoWeb` 类），Duo Security 已将其退役。Duo 关闭 v2 API 端点后该模块将停止工作。生产环境需升级至 Duo SDK v4（Universal Prompt）。

---

### Header（HTTP 认证）

**源文件：** `guacamole-auth-header-starter/.../conf/HTTPHeaderGuacamoleProperties.java`、`ConfigurationService.java`

**前缀：** `guacamole.auth.header.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `http-auth-header` | `guacamole.auth.header.http-auth-header` | `REMOTE_USER` | 否 |

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: REMOTE_USER
```

---

### JSON

**源文件：** `guacamole-auth-json-starter/.../conf/ConfigurationService.java`

**前缀：** `guacamole.auth.json.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `json-secret-key` | `guacamole.auth.json.json-secret-key` | -- | **是** |
| `json-trusted-networks` | `guacamole.auth.json.json-trusted-networks` | （全部地址） | 否 |

```yaml
guacamole:
  auth:
    json:
      enabled: true
      json-secret-key: ${JSON_SECRET_KEY_BASE64}
      json-trusted-networks: 10.0.0.0/8,172.16.0.0/12
```

---

### QuickConnect

**源文件：** `guacamole-auth-quickconnect-starter/.../conf/ConfigurationService.java`

**前缀：** `guacamole.auth.quickconnect.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `quickconnect-allowed-parameters` | `guacamole.auth.quickconnect.quickconnect-allowed-parameters` | （全部允许） | 否 |
| `quickconnect-denied-parameters` | `guacamole.auth.quickconnect.quickconnect-denied-parameters` | （无禁止） | 否 |

```yaml
guacamole:
  auth:
    quickconnect:
      enabled: true
      quickconnect-allowed-parameters: hostname,port,protocol
      quickconnect-denied-parameters: password,private-key
```

---

### SSO / CAS

**源文件：** `guacamole-auth-sso-cas-starter/.../conf/CASGuacamoleProperties.java`、`ConfigurationService.java`

**前缀：** `guacamole.auth.sso-cas.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `cas-authorization-endpoint` | `guacamole.auth.sso-cas.cas-authorization-endpoint` | -- | **是** |
| `cas-redirect-uri` | `guacamole.auth.sso-cas.cas-redirect-uri` | -- | **是** |
| `cas-clearpass-key` | `guacamole.auth.sso-cas.cas-clearpass-key` | -- | 否 |
| `cas-group-attribute` | `guacamole.auth.sso-cas.cas-group-attribute` | -- | 否 |
| `cas-group-format` | `guacamole.auth.sso-cas.cas-group-format` | `plain` | 否 |
| `cas-group-ldap-base-dn` | `guacamole.auth.sso-cas.cas-group-ldap-base-dn` | -- | 否 |
| `cas-group-ldap-attribute` | `guacamole.auth.sso-cas.cas-group-ldap-attribute` | -- | 否 |

```yaml
guacamole:
  auth:
    sso-cas:
      enabled: true
      cas-authorization-endpoint: https://cas.example.org/cas
      cas-redirect-uri: https://guacamole.example.com/
```

---

### SSO / OpenID Connect

**源文件：** `guacamole-auth-sso-openid-starter/.../conf/ConfigurationService.java`

**前缀：** `guacamole.auth.sso-openid.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `openid-authorization-endpoint` | `guacamole.auth.sso-openid.openid-authorization-endpoint` | -- | **是** |
| `openid-jwks-endpoint` | `guacamole.auth.sso-openid.openid-jwks-endpoint` | -- | **是** |
| `openid-issuer` | `guacamole.auth.sso-openid.openid-issuer` | -- | **是** |
| `openid-client-id` | `guacamole.auth.sso-openid.openid-client-id` | -- | **是** |
| `openid-redirect-uri` | `guacamole.auth.sso-openid.openid-redirect-uri` | -- | **是** |
| `openid-scope` | `guacamole.auth.sso-openid.openid-scope` | `openid email profile` | 否 |
| `openid-username-claim-type` | `guacamole.auth.sso-openid.openid-username-claim-type` | `email` | 否 |
| `openid-groups-claim-type` | `guacamole.auth.sso-openid.openid-groups-claim-type` | `groups` | 否 |
| `openid-allowed-clock-skew` | `guacamole.auth.sso-openid.openid-allowed-clock-skew` | `30`（秒） | 否 |
| `openid-max-token-validity` | `guacamole.auth.sso-openid.openid-max-token-validity` | `300`（分钟） | 否 |
| `openid-max-nonce-validity` | `guacamole.auth.sso-openid.openid-max-nonce-validity` | `10`（分钟） | 否 |

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

---

### SSO / SAML 2.0

**源文件：** `guacamole-auth-sso-saml-starter/.../conf/ConfigurationService.java`

**前缀：** `guacamole.auth.sso-saml.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `saml-idp-metadata-url` | `guacamole.auth.sso-saml.saml-idp-metadata-url` | -- | 条件必填 |
| `saml-idp-url` | `guacamole.auth.sso-saml.saml-idp-url` | -- | 条件必填 |
| `saml-entity-id` | `guacamole.auth.sso-saml.saml-entity-id` | -- | 条件必填 |
| `saml-callback-url` | `guacamole.auth.sso-saml.saml-callback-url` | -- | **是** |
| `saml-strict` | `guacamole.auth.sso-saml.saml-strict` | `true` | 否 |
| `saml-debug` | `guacamole.auth.sso-saml.saml-debug` | `false` | 否 |
| `saml-compress-request` | `guacamole.auth.sso-saml.saml-compress-request` | `true` | 否 |
| `saml-compress-response` | `guacamole.auth.sso-saml.saml-compress-response` | `true` | 否 |
| `saml-group-attribute` | `guacamole.auth.sso-saml.saml-group-attribute` | `groups` | 否 |
| `saml-auth-timeout` | `guacamole.auth.sso-saml.saml-auth-timeout` | `5`（分钟） | 否 |

```yaml
guacamole:
  auth:
    sso-saml:
      enabled: true
      saml-callback-url: https://guacamole.example.com/
      saml-idp-url: https://idp.example.com/sso
      saml-entity-id: https://guacamole.example.com/
      saml-strict: true
```

---

### Vault / KSM

**源文件：** `guacamole-vault-ksm-starter/.../conf/KsmConfigurationService.java`

**前缀：** `guacamole.vault.ksm.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `ksm-config` | `guacamole.vault.ksm.ksm-config` | -- | **是** |
| `ksm-allow-unverified-cert` | `guacamole.vault.ksm.ksm-allow-unverified-cert` | `false` | 否 |

KSM 模块还读取 `GUACAMOLE_HOME/ksm-token-mapping.yml` 和 `GUACAMOLE_HOME/guacamole.properties.ksm`。

```yaml
guacamole:
  vault:
    ksm:
      enabled: true
      ksm-config: ${KSM_CONFIG_BASE64}
      ksm-allow-unverified-cert: false
```

---

### History（录像）

**源文件：** `guacamole-history-starter/.../HistoryAutoConfiguration.java`

**前缀：** `guacamole.history.`

| guacamole.properties | application.yml 路径 | 默认值 | 必填 |
|---------------------|----------------------|--------|------|
| `recording-search-path` | `guacamole.history.recording-search-path` | `/var/lib/guacamole/recordings` | 否 |

```yaml
guacamole:
  history:
    enabled: true
    recording-search-path: ${GUACAMOLE_HISTORY_RECORDING_PATH:/tmp/guacamole/recordings}
```

---

## 扩展加载方式变更

### 原版（Apache Guacamole 1.5.5）

1. 每个扩展编译为独立 `.jar` 文件
2. 将 `.jar` 放入 `GUACAMOLE_HOME/extensions/`
3. 每个 `.jar` 包含 `guac-manifest.json` 声明身份
4. Guacamole 启动时通过 `GuacamoleExtensionLoader` 扫描 `GUACAMOLE_HOME/extensions/` 发现扩展
5. 依赖注入由 Google Guice 模块管理（`AbstractModule`、`FactoryModuleBuilder`）
6. 扩展 JAR 存在即自动激活

### 迁移后（Spring Boot 3.3.5）

1. 扩展为标准 Spring Boot Starter 模块，含自动配置
2. 在 `guacamole/pom.xml` 中添加扩展的 Maven 依赖
3. 每个 starter 提供 `spring.factories` 或 `AutoConfiguration.imports` 条目供 Spring Boot 自动发现
4. `@ConditionalOnProperty(prefix = "...", name = "enabled", havingValue = "true")` 控制激活
5. **所有扩展默认禁用** -- 必须在 `application.yml` 中显式设置 `enabled: true`
6. 扩展 JAR 打包在 Fat JAR 内部；无需外部目录扫描
7. Spring 扫描 `classpath*:guac-manifest.json` 注册前端资源

### 对比

| 操作 | 原版 | 迁移后 |
|------|------|--------|
| 安装扩展 | 复制 JAR 到 `GUACAMOLE_HOME/extensions/` | 在 `guacamole/pom.xml` 添加 Maven 依赖 |
| 启用扩展 | 自动（JAR 存在即启用） | 依赖存在 + `enabled: true` 在 YAML 中 |
| 禁用扩展 | 删除 JAR 文件 | 设置 `enabled: false` 或移除 Maven 依赖 |
| 配置扩展 | `guacamole.properties` | `application.yml` 中对应模块命名空间下 |

---

## 数据库迁移

### Schema 兼容性

数据库 Schema **100% 不变**。原版项目的所有 `schema/` SQL 脚本原样保留：

```
extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/
  +-- 001-create-schema.sql
  +-- 002-create-admin-user.sql
  +-- upgrade/

extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/
  +-- 001-create-schema.sql
  +-- 002-create-admin-user.sql
  +-- upgrade/

extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/
  +-- 001-create-schema.sql
  +-- 002-create-admin-user.sql
  +-- upgrade/
```

### 数据兼容性

所有表、列、约束和索引与 Apache Guacamole 1.5.5 创建的完全相同。迁移后的应用可直接连接您的现有数据库，无需数据迁移或 Schema 变更。

### Schema 版本检测

数据库 Schema 版本必须与应用期望的版本匹配（`guacamole.version` = `1.5.5`）。如果从更早的 Guacamole 版本升级数据库，请在启动迁移应用前运行合适的 `upgrade/` 脚本。

---

## 部署方式变更

| 方面 | 原版（WAR） | 迁移后（Fat JAR） |
|------|------------|-------------------|
| 打包 | `guacamole.war`（部署到 `webapps/`） | `guacamole-{version}.jar`（独立运行） |
| Servlet 容器 | 外部 Tomcat / Jetty 8/9/10 | 内嵌 Tomcat（Spring Boot） |
| 启动 | 容器启动，部署 WAR | `java -jar guacamole-*.jar` |
| 配置文件位置 | `GUACAMOLE_HOME/guacamole.properties` | classpath `application.yml`（或 `--spring.config.location`） |
| 扩展位置 | `GUACAMOLE_HOME/extensions/*.jar` | Fat JAR 内部（classpath） |
| 环境变量 | `GUACAMOLE_HOME` | Spring Boot 标准环境变量 |
| HTTP 端口 | Tomcat 的 `server.xml` | `server.port: 8080` 在 `application.yml` |
| 日志 | 容器日志 / `GUACAMOLE_HOME/logs/` | `logging.*` 在 `application.yml`（Spring Boot / Logback） |

### 快速启动

```bash
# 构建整个项目
mvn clean package -DskipTests

# 运行 Fat JAR
java -jar guacamole/target/guacamole-1.5.5.jar

# 覆盖配置位置
java -jar guacamole/target/guacamole-1.5.5.jar \
  --spring.config.location=/etc/guacamole/application.yml

# 覆盖单个属性
java -jar guacamole/target/guacamole-1.5.5.jar \
  --server.port=8443 \
  --guacamole.guacd.hostname=guacd.example.com
```

### GUACAMOLE_HOME 在迁移版本中

`GUACAMOLE_HOME` 仍然被 `Environment` 实现用于某些运行时查找（如 `ldap-servers.yml`、`ksm-token-mapping.yml`、SSL 证书文件、`guacamole.properties.ksm`）。但所有主要配置移至 `application.yml`。GUACAMOLE_HOME 目录现在仅用于传统上放置在那里的补充文件。

---

## API 兼容性

所有 REST API 端点和 WebSocket 路径与 Apache Guacamole 1.5.5 **完全相同**。公共 HTTP API 表面无变更。

### REST 端点

| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/tokens` | POST | 创建认证令牌 |
| `/api/tokens/{token}` | DELETE | 使令牌失效（注销） |
| `/api/session/data/{dataSource}/connections` | GET | 列出连接 |
| `/api/session/data/{dataSource}/connections/{id}` | GET | 获取连接详情 |
| `/api/session/data/{dataSource}/connections/{id}` | PUT | 更新连接 |
| `/api/session/data/{dataSource}/connections/{id}` | DELETE | 删除连接 |
| `/api/session/data/{dataSource}/connections/{id}/parameters` | GET | 获取连接参数 |
| `/api/session/data/{dataSource}/users` | GET/POST | 列出/创建用户 |
| `/api/session/data/{dataSource}/users/{username}` | GET/PUT/DELETE | 用户 CRUD |
| `/api/session/data/{dataSource}/userGroups` | GET/POST | 组管理 |
| `/api/session/data/{dataSource}/activeConnections` | GET | 活跃连接 |
| `/api/session/data/{dataSource}/connectionGroups` | GET | 连接组 |
| `/api/session/tunnels` | GET/POST | 隧道管理 |
| `/api/patches` | GET | 前端补丁（扩展注入） |

### WebSocket

| 路径 | 协议 | 用途 |
|------|------|------|
| `/websocket-tunnel` | Guacamole 协议 over WebSocket | 主要隧道传输 |

### 静态资源

所有前端资源路径已保留。

### SSO 回调端点

| SSO 类型 | 回调路径 |
|----------|----------|
| CAS | `/api/ext/cas/callback` |
| OpenID Connect | `/api/ext/openid/callback` |
| SAML | `/api/ext/saml/callback` |

---

## 破坏性变更

### 1. DUO SDK v2 已弃用

DUO 认证模块仍使用已弃用的 Duo SDK v2（`DuoWeb` 类）。Duo 已宣布此 SDK 的生命周期结束。Duo 关闭其 v2 API 后此模块将停止工作。**需升级至 Duo SDK v4（Universal Prompt）**后才能在生成环境中使用 DUO。

### 2. WebSocket 容器：仅支持 Tomcat，Jetty 已移除

原版 Apache Guacamole 支持多种 WebSocket 容器适配器：Jetty 8、Jetty 9 和 Tomcat（`tunnel/websocket/jetty8/`、`jetty9/`、`tomcat/`）。所有三种适配器均已在迁移版中移除，替换为单一的 JSR 356 标准端点（`GuacamoleWebSocketEndpoint.java`），由 Spring Boot 内嵌的 Tomcat 容器运行。

> **注意区分：** 本项目仍然使用 **Jersey**（JAX-RS 实现，处理 REST API，通过 `spring-boot-starter-jersey` 引入）。Jersey 和 Jetty 是**两个完全不同的组件**：Jersey 是 REST 框架，Jetty 是 Servlet 容器。移除的是 Jetty（容器），Jersey（REST 框架）不受影响。

如果您之前在 Jetty 容器下运行原版 Guacamole，需切换到 Spring Boot 内嵌的 Tomcat。

### 3. Maven Group ID 变更

```
旧版：org.apache.guacamole
新版：com.right
```

所有内部 Maven 构件坐标已变更。如果您的自定义扩展依赖于 `org.apache.guacamole:guacamole-ext`，必须将依赖更新为 `com.right:guacamole-ext`。

### 4. Java 包名保留

尽管 groupId 变更，所有 Java 包名仍保留在 `org.apache.guacamole.*` 下。仅 Maven 坐标变更。

### 5. 扩展自动发现方式变更

原版项目中，将 JAR 放入 `GUACAMOLE_HOME/extensions/` 即可。扩展是被动的 -- 仅当 `guacamole.properties` 中存在对应条目时才激活。

迁移版本中：
- 扩展必须是 Maven 依赖（打包在 Fat JAR 中）
- 扩展**默认禁用**，需在 YAML 中显式设置 `enabled: true`
- 多个 JDBC 或 SSO 模块不能同时启用（启动时校验）

### 6. javax.* 到 jakarta.* 重命名

所有 `javax.servlet.*`、`javax.ws.rs.*`、`javax.annotation.*`、`javax.inject.*` 和 `javax.xml.bind.*` 导入已迁移至 `jakarta.*`。任何自定义扩展代码需要相同的迁移。

### 7. Google Guice 移除

所有 `@Inject` 注解已替换为 Spring `@Autowired`。Guice `AbstractModule` 类已替换为 Spring `@Configuration` 类。Guice `FactoryModuleBuilder` 生成的工厂已替换为直接的 Spring bean 装配。

### 8. 构建标识符行为

`${guacamole.build.identifier}` 属性仍在 `index.html` 和 `verifyCachedVersion.js` 中使用，生成基于时间戳的构建标识符，与原版项目完全相同。Maven 资源插件分隔符已从 `@`（Spring Boot 默认）恢复为 `${}` 以保持此行为。

---

## 自定义扩展迁移

如果您维护自定义 Guacamole 认证扩展，以下是迁移至 Spring Boot 版本的步骤清单。

### 步骤 1：更新依赖

```xml
<!-- 旧版 -->
<dependency>
    <groupId>org.apache.guacamole</groupId>
    <artifactId>guacamole-ext</artifactId>
    <version>1.5.5</version>
</dependency>

<!-- 新版 -->
<dependency>
    <groupId>com.right</groupId>
    <artifactId>guacamole-ext</artifactId>
    <version>1.5.5</version>
</dependency>
```

### 步骤 2：替换导入

| 旧版 | 新版 |
|-----|------|
| `javax.inject.Inject` | `org.springframework.beans.factory.annotation.Autowired` |
| `javax.inject.Singleton` | `org.springframework.stereotype.Component` |
| `javax.servlet.*` | `jakarta.servlet.*` |
| `javax.ws.rs.*` | `jakarta.ws.rs.*` |
| `javax.xml.bind.*` | `jakarta.xml.bind.*` |

### 步骤 3：替换依赖注入配置

```java
// 旧版（Guice）
public class MyModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AuthenticationProvider.class)
            .to(MyAuthProvider.class);
    }
}

// 新版（Spring）
@Configuration
public class MyAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "guacamole.auth.my", name = "enabled",
                           havingValue = "true")
    public MyAuthProvider myAuthProvider() {
        return new MyAuthProvider();
    }
}
```

### 步骤 4：注册自动配置

创建 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
com.example.MyAutoConfiguration
```

### 步骤 5：更新 guac-manifest.json

添加 `configProperty` 字段，将 YAML 命名空间与扩展的属性定义关联：

```json
{
    "guacamole-version": "1.5.0",
    "namespace": "guacamole-auth-my",
    "configProperty": "guacamole.auth.my.enabled"
}
```

### 步骤 6：定义 YAML 属性

如果您的扩展读取 `Environment`，属性将自动从 `application.yml` 中使用模块命名空间下的原始属性名解析。例如，`guacamole.properties` 中名为 `my-secret-key` 的属性将在 YAML 中从 `guacamole.auth.my.my-secret-key` 读取。

详见 [EXTENSIONS.md](EXTENSIONS.md)。

---

## 参考

- [CONFIGURATION.md](CONFIGURATION.md) -- 完整配置参考
- [EXTENSIONS.md](EXTENSIONS.md) -- 扩展开发指南
- [ARCHITECTURE.md](ARCHITECTURE.md) -- 系统架构说明
