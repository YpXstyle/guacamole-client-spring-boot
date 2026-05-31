# Guacamole Spring Boot

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green.svg)](https://spring.io/projects/spring-boot)
[![Guacamole](https://img.shields.io/badge/Guacamole-1.5.5-orange.svg)](https://guacamole.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

Apache Guacamole 1.5.5 迁移至 **Spring Boot 3.3.5 + JDK 17**，将 Google Guice 替换为 Spring IoC，保留 Jersey JAX-RS 作为 Web 框架。所有扩展模块标准化为 Spring Boot Starter。

## 目录

- [功能特性](#功能特性)
- [快速开始](#快速开始)
- [系统架构](#系统架构)
- [扩展模块](#扩展模块)
- [配置说明](#配置说明)
- [构建与部署](#构建与部署)
- [开发指南](#开发指南)
- [文档](#文档)

## 功能特性

- **Spring Boot 3.3.5 + Java 17** — 现代化、生产级基础框架
- **15+ 认证扩展** — Header、JSON、JDBC（MySQL/PostgreSQL/SQL Server）、LDAP、RADIUS、TOTP、DUO、SSO（CAS/OpenID/SAML）、Quick Connect
- **Guacamole 协议** — 通过 guacd 完整支持 RDP、VNC、SSH、Telnet、Kubernetes
- **会话录像** — 存储和回放连接历史记录
- **连接共享** — 分享活跃会话，支持细粒度权限控制
- **Vault 集成** — Keeper Secrets Manager（KSM）凭据注入
- **标准 Starter 模式** — 通过 `application.yml` 启用/禁用扩展
- **Docker 支持** — 多阶段 Dockerfile + docker-compose（guacd + PostgreSQL）
- **构建时 JS/CSS 压缩** — Google Closure Compiler（与上游项目一致）

## 快速开始

### 环境要求

- **JDK 17** 或更高版本
- **Maven 3.8+**
- **guacd** 1.5.5 运行中（Docker：`docker run -d -p 4822:4822 guacamole/guacd:1.5.5`）
- **PostgreSQL**（或 MySQL / SQL Server），已初始化 Guacamole 数据库表

### 源码运行

```bash
# 克隆并构建
git clone <仓库地址>
cd guacamole-spring-boot
mvn clean package -DskipTests

# 启动
java -jar guacamole/target/guacamole-*.jar
```

### Maven 插件运行

```bash
mvn spring-boot:run -pl guacamole
```

### Docker 运行

```bash
docker-compose up -d
```

### 默认访问信息

| 项目 | 值 |
|------|-----|
| 访问地址 | `http://localhost:8080/` |
| 默认管理员 | `guacadmin` / `guacadmin`（使用 JDBC 认证时） |

### 初始化数据库

使用 JDBC 认证时，需要先执行建表脚本：

**PostgreSQL：**
```bash
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/001-create-schema.sql
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/002-create-admin-user.sql
```

**MySQL：**
```bash
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/001-create-schema.sql
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/002-create-admin-user.sql
```

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    浏览器 (AngularJS)                    │
│   WebSocket (guacamole 协议)  │  REST API (Jersey)      │
└──────────────────┬───────────────┴──────────┬───────────┘
                   │                          │
┌──────────────────▼──────────────────────────▼───────────┐
│               Spring Boot 应用                           │
│  ┌──────────────────────────────────────────────────┐   │
│  │            Jersey JAX-RS (/api/*)               │   │
│  │  SessionResource  UserContextResource  ...      │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │         扩展系统 (Starter 模式)                   │   │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌───────┐ │   │
│  │  │Header│ │JSON  │ │JDBC  │ │LDAP  │ │  SSO  │ │   │
│  │  │Auth  │ │Auth  │ │Auth  │ │Auth  │ │ Auth  │ │   │
│  │  └──────┘ └──────┘ └──────┘ └──────┘ └───────┘ │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │          核心服务                                  │   │
│  │  Environment  TokenSessionMap  TunnelRequest      │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────┬───────────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────────┐
│                    guacd (1.5.5)                         │
│   RDP  │  VNC  │  SSH  │  Telnet  │  Kubernetes         │
└──────────────────────────────────────────────────────────┘
```

### 关键设计决策

| 方面 | 原项目 (Guice) | Spring Boot |
|------|---------------|-------------|
| DI 框架 | Google Guice `@Inject` | Spring `@Autowired` |
| 模块加载 | `AbstractModule.bind()` | `@AutoConfiguration` + `@Bean` |
| REST 框架 | Jersey（不变） | Jersey（不变） |
| WebSocket | Guice 辅助的 JSR 356 | Tomcat 生命周期 + JSR 356 |
| 配置文件 | `guacamole.properties` | `application.yml` |
| 扩展发现 | Guice `FactoryModuleBuilder` | `ResourcePatternResolver` 类路径扫描 |
| 属性解析 | `LocalEnvironment` + 文件 | Spring `Environment` 桥接 |

## 扩展模块

### 认证类

| 扩展 | 构件 | 说明 | 状态 |
|------|------|------|------|
| **Header Auth** | `guacamole-auth-header-starter` | 反向代理 SSO，从 HTTP 头读取用户名（如 `REMOTE_USER`） | ✅ 已验证 |
| **JSON Auth** | `guacamole-auth-json-starter` | 加密 Token 认证，无需数据库 | ✅ 已验证 |
| **JDBC MySQL** | `guacamole-auth-mysql-starter` | MySQL 数据库认证 | ✅ 已验证 |
| **JDBC PostgreSQL** | `guacamole-auth-postgresql-starter` | PostgreSQL 数据库认证 | ✅ 已验证 |
| **JDBC SQL Server** | `guacamole-auth-sqlserver-starter` | SQL Server 数据库认证 | ✅ 已验证 |
| **LDAP** | `guacamole-auth-ldap-starter` | LDAP / Active Directory 认证 | ✅ 已验证 |
| **RADIUS** | `guacamole-auth-radius-starter` | RADIUS 认证（PAP、CHAP、MSCHAPv1/v2、EAP） | ✅ 已验证 |
| **TOTP** | `guacamole-auth-totp-starter` | 基于时间的一次性密码（Google Authenticator 等） | ✅ 已验证 |
| **DUO** | `guacamole-auth-duo-starter` | Duo Security 双因素认证 | ⚠️ 需升级 SDK v4 |
| **SSO CAS** | `guacamole-auth-sso-cas-starter` | CAS 单点登录 | ✅ 已验证 |
| **SSO OpenID** | `guacamole-auth-sso-openid-starter` | OpenID Connect（Google、Okta、Keycloak 等） | ✅ 已验证 |
| **SSO SAML** | `guacamole-auth-sso-saml-starter` | SAML 2.0 单点登录 | ✅ 已验证 |

### 功能类

| 扩展 | 构件 | 说明 | 状态 |
|------|------|------|------|
| **Quick Connect** | `guacamole-auth-quickconnect-starter` | 通过 URI 快速创建连接（如 `rdp://host:3389`） | ✅ 已验证 |
| **History** | `guacamole-history-starter` | 会话录像存储与回放 | ✅ 已验证 |
| **Vault KSM** | `guacamole-vault-ksm-starter` | Keeper Secrets Manager 凭据注入 | ⏳ 待 KSM 账号测试 |

### 互斥限制

- **数据库认证三选一** — 同时启用多个 JDBC Starter 会导致启动报错
- **SSO 三选一** — CAS、OpenID、SAML 互斥
- **DUO 需 SDK v4 升级** — 当前使用 Duo Web SDK v2，已于 2024 年 3 月被 Duo 停用（详见[已知问题](#已知问题)）

## 配置说明

配置文件位于 `guacamole/src/main/resources/application.yml`，完整参考见 **[docs/CONFIGURATION.md](docs/CONFIGURATION.md)**。

### 最小配置（PostgreSQL）

```yaml
server:
  port: 8080

guacamole:
  guacd:
    hostname: localhost
    port: 4822
  auth:
    postgresql:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD:guacamole}
    driver-class-name: org.postgresql.Driver
```

### 启用扩展

```yaml
guacamole:
  auth:
    header:
      enabled: true       # 反向代理 SSO
    json:
      enabled: true       # 加密 Token 认证
    quickconnect:
      enabled: true       # URI 快速连接
    postgresql:
      enabled: true       # 数据库认证（三选一）
    mysql:
      enabled: false
    sqlserver:
      enabled: false
```

扩展只有在 Maven 依赖存在**且** `enabled: true` 时才会加载。

## 构建与部署

### 构建

```bash
# 完整构建（所有模块）
mvn clean package -DskipTests

# 仅构建 Web 应用
mvn clean package -pl guacamole -am -DskipTests

# 含测试构建
mvn clean package
```

### 前端开发

```bash
cd guacamole/frontend
npm ci
npm run build       # 生产构建
npm run dev         # 开发模式（监听文件变化）
```

### Docker

```bash
# 构建并启动所有服务（guacd + PostgreSQL + Guacamole）
docker-compose up -d

# 仅构建 Guacamole 镜像
docker build -t guacamole-spring-boot .

# 覆盖环境变量
GUACAMOLE_DB_PASSWORD=secret123 docker-compose up -d
```

详见 **[docs/BUILD.md](docs/BUILD.md)**。

## 开发指南

### 项目结构

```
guacamole-spring-boot/
├── pom.xml                           # 根 POM（继承 Spring Boot parent）
├── guacamole-common/                 # Guacamole 协议库
├── guacamole-common-js/              # Guacamole JavaScript API
├── guacamole-ext/                    # 扩展 API（AuthenticationProvider 等）
├── guacamole/                        # 主 Web 应用
│   ├── frontend/                     # AngularJS 前端（webpack）
│   └── src/main/java/org/apache/guacamole/
│       ├── config/                   # Spring @Configuration 类
│       ├── extension/                # 扩展加载与 manifest 解析
│       ├── resource/                 # 资源服务（ResourceServlet）
│       ├── rest/                     # Jersey REST 资源
│       └── tunnel/                   # WebSocket 隧道
├── extensions/
│   ├── guacamole-auth-jdbc/          # JDBC 认证（MySQL/PG/MSSQL）
│   ├── guacamole-auth-sso/           # SSO（CAS/OpenID/SAML）
│   ├── guacamole-vault/              # Vault 密钥管理
│   ├── guacamole-history-starter/    # 会话录像
│   └── guacamole-auth-*-starter/     # 各独立认证扩展
└── docs/                             # 文档
```

### 创建新扩展

1. 在 `extensions/` 下创建 Maven 模块
2. 在 `src/main/resources/` 中创建 `guac-manifest.json`
3. 创建 `AutoConfiguration` 类并定义 `@Bean`
4. 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册
5. 在 `guacamole/pom.xml` 中添加依赖

详见 **[docs/EXTENSIONS.md](docs/EXTENSIONS.md)**。

### 迁移关键变更对照

| 原项目 (Guice) | Spring Boot |
|---------------|-------------|
| `@Inject` | `@Autowired` |
| `AbstractModule.bind()` | `@Configuration` 中的 `@Bean` |
| `@Provides` | `@Bean` 方法 |
| `FactoryModuleBuilder` | `DirectoryObjectResourceFactory` Lambda |
| `GuiceFilter` + `GuacamoleServletContextListener` | `@SpringBootApplication` |
| `guacamole.properties` 文件 | `application.yml` |
| 扩展 `.jar` 放入 `GUACAMOLE_HOME/extensions/` | Maven 依赖自动加入 classpath |
| `GuacamoleExtensionLoader`（ServiceLoader） | Spring `ResourcePatternResolver` |

## 已知问题

### DUO SDK v2 已停用

DUO 扩展使用 Web SDK v2（基于 iframe），Duo 已于 2024 年 3 月停止支持传统 Prompt。扩展可以正常启动，但认证会被 Duo 拦截。需要将集成方式从 Web SDK v2 升级到 Web SDK v4：

- 前端：iframe 方式 → 重定向方式
- 后端：HMAC SHA-256 → HMAC SHA-512
- 配置：去掉 `duo-application-key`，`ikey`/`skey` 改名 `client_id`/`client_secret`

**参考：** https://duo.com/docs/duoweb

## 文档

| 文档 | 说明 |
|------|------|
| [CONFIGURATION.md](docs/CONFIGURATION.md) | 全部扩展的完整配置参考 |
| [BUILD.md](docs/BUILD.md) | 构建、Docker、部署指南 |
| [EXTENSIONS.md](docs/EXTENSIONS.md) | 扩展系统架构与开发指南 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 系统架构深入分析 |
| [vault-module.md](docs/vault-module.md) | Vault / KSM 模块详细文档 |

## 许可证

本项目使用 Apache License 2.0，与上游 Apache Guacamole 保持一致。

所有原始 Guacamole 源文件保留其 Apache 2.0 头部声明。迁移代码和 Spring Boot 配置类同样采用 Apache 2.0 许可。
