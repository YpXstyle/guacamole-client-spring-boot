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
    header:
      enabled: true          # HTTP Header 认证
    duo:
      enabled: false         # Duo 双因素认证
    json:
      enabled: true          # JSON Token 认证
    ldap:
      enabled: false         # LDAP 认证
    totp:
      enabled: false         # TOTP 双因素认证
    radius:
      enabled: false         # RADIUS 认证
    quickconnect:
      enabled: true          # 快速连接
    mysql:
      enabled: false         # MySQL 数据库认证
    postgresql:
      enabled: true          # PostgreSQL 数据库认证
    sqlserver:
      enabled: false         # SQL Server 数据库认证

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

### 数据库认证限制

同时只能启用一个数据库认证模块（MySQL、PostgreSQL、SQL Server）。启动时会校验，如果启用多个会报错。

### Header Auth 配置

默认读取 `REMOTE_USER` 头。可通过配置自定义：

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: X-Remote-User  # 自定义头名称
```

### JSON Auth 配置

```yaml
guacamole:
  auth:
    json:
      enabled: true
      json-secret-key: your-secret-key-here
```

## 扩展模块说明

### 已验证通过 ✅

| 模块 | 作用 | 测试状态 | 说明 |
|------|------|---------|------|
| **Header Auth** | 反向代理认证，从 HTTP 头读取用户名 | ✅ 已验证 | 需配置 `REMOTE_USER` 头 |
| **JSON Auth** | 加密 Token 认证，无持久化存储 | ✅ 已验证 | 使用 AES/CBC/PKCS5Padding + HMAC-SHA256 |
| **TOTP** | 基于时间的一次性密码（Google Authenticator 等） | ✅ 已验证 | 需用户自行绑定 |
| **Quick Connect** | 快速创建连接（输入 `rdp://host:3389`） | ✅ 已验证 | 支持 RDP/VNC/SSH/Telnet |
| **History** | 连接历史记录和录像存储 | ✅ 已验证 | 配置 `recording-search-path` |
| **PostgreSQL** | PostgreSQL 数据库认证 | ✅ 已验证 | 需配置 `spring.datasource` |
| **MySQL** | MySQL 数据库认证 | ✅ 已验证 | 需配置 `spring.datasource` |
| **SQL Server** | SQL Server 数据库认证 | ✅ 已验证 | 需配置 `spring.datasource` |

### 启动验证通过 ⏳

| 模块 | 作用 | 测试状态 | 说明 |
|------|------|---------|------|
| **DUO** | Duo Security 双因素认证 | ⏳ 启动验证通过 | 需升级到 Web SDK v4（见下方说明） |
| **LDAP** | LDAP/AD 目录认证 | ⏳ 启动验证通过 | 需配置 LDAP 服务器 |
| **RADIUS** | RADIUS 认证 | ⏳ 启动验证通过 | 需配置 RADIUS 服务器 |

### 待验证

| 模块 | 作用 | 测试状态 | 说明 |
|------|------|---------|------|
| **SSO Base** | SSO 基础模块（供 CAS/OpenID/SAML 使用） | 待验证 | 库模块，无独立功能 |
| **SSO CAS** | CAS 单点登录 | 待验证 | 需 CAS 服务器 |
| **SSO OpenID** | OpenID Connect 认证 | 待验证 | 需 OpenID Provider |
| **SSO SAML** | SAML 单点登录 | 待验证 | 需 SAML IdP |
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

所有扩展模块采用标准 Spring Boot Starter 模式：
- `@ConditionalOnProperty` 控制模块启用/禁用
- `@Bean` 集中在 AutoConfiguration 类中定义
- 通过 `AutoConfiguration.imports` 注册
- 未启用的模块不会加载任何 Bean

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
