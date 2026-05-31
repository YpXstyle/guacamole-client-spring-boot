# Guacamole Spring Boot

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green.svg)](https://spring.io/projects/spring-boot)
[![Guacamole](https://img.shields.io/badge/Guacamole-1.5.5-orange.svg)](https://guacamole.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

**Apache Guacamole 1.5.5** —— 从 Google Guice 迁移至 **Spring Boot 3.3.5 + Java 17**。

本项目是上游 [Apache Guacamole](https://github.com/apache/guacamole-client) Web 应用程序的完整迁移，将原有的 Google Guice 依赖注入框架替换为 Spring Boot 的自动配置和 Starter 模式。所有扩展模块均已标准化为 Spring Boot Starter，同时保留了 Jersey JAX-RS REST 层、WebSocket 隧道和 AngularJS 前端。

Group ID 已从 `org.apache.guacamole` 变更为 `com.right`。除此之外，所有公开 REST API 均与原始项目完全兼容。

---

## 目录

- [功能特性](#功能特性)
- [系统架构](#系统架构)
- [扩展模块](#扩展模块)
- [快速开始](#快速开始)
- [配置快速参考](#配置快速参考)
- [迁移状态总览](#迁移状态总览)
- [构建与部署](#构建与部署)
- [项目结构](#项目结构)
- [已知问题](#已知问题)
- [文档索引](#文档索引)
- [许可证](#许可证)

---

## 功能特性

- **Spring Boot 3.3.5 + Java 17** —— 生产级基础框架，支持自动配置、Actuator 监控和简化部署
- **17 个扩展模块** —— 全部转换为 Spring Boot Starter 构件，通过 `@ConditionalOnProperty` 控制启用/禁用
- **完整协议支持** —— RDP、VNC、SSH、Telnet、Kubernetes（通过 guacd 1.5.5）
- **会话录制** —— 连接历史记录的存储与回放
- **连接共享** —— 共享活跃会话，支持细粒度权限控制
- **Vault 集成** —— Keeper Secrets Manager (KSM) 凭据注入
- **Docker 支持** —— 多阶段 Dockerfile + docker-compose（guacd + PostgreSQL）
- **构建时 JS/CSS 压缩** —— Google Closure Compiler（与上游行为一致）
- **构建标识符** —— `${guacamole.build.identifier}` 时间戳生成并注入前端资源，匹配原版 Apache Guacamole 构建行为

### 扩展构件列表

| 分类 | Artifact ID |
|------|-------------|
| 认证 | `guacamole-auth-header-starter` |
| 认证 | `guacamole-auth-json-starter` |
| 认证 | `guacamole-auth-ldap-starter` |
| 认证 | `guacamole-auth-radius-starter` |
| 认证 | `guacamole-auth-totp-starter` |
| 认证 | `guacamole-auth-duo-starter` |
| 认证 | `guacamole-auth-mysql-starter` |
| 认证 | `guacamole-auth-postgresql-starter` |
| 认证 | `guacamole-auth-sqlserver-starter` |
| 认证 | `guacamole-auth-quickconnect-starter` |
| SSO | `guacamole-auth-sso-cas-starter` |
| SSO | `guacamole-auth-sso-openid-starter` |
| SSO | `guacamole-auth-sso-saml-starter` |
| Vault | `guacamole-vault-ksm-starter` |
| 历史记录 | `guacamole-history-starter` |

---

## 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                      浏览器 (AngularJS)                       │
│    WebSocket (guacamole 协议)   │  REST API (Jersey)         │
└──────────────────┬────────────────────┬───────────────────────┘
                   │                    │
┌──────────────────▼────────────────────▼───────────────────────┐
│                  Spring Boot 应用 (Fat JAR)                    │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐    │
│  │                Jersey JAX-RS (/api/*)                  │    │
│  │     SessionResource   UserContextResource   ...        │    │
│  └───────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐    │
│  │             扩展系统 (Starter 模式)                     │    │
│  │                                                       │    │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────────┐   │    │
│  │  │Header│ │JSON  │ │JDBC  │ │LDAP  │ │SSO (CAS/ │   │    │
│  │  │Auth  │ │Auth  │ │Auth  │ │Auth  │ │OIDC/SAML)│   │    │
│  │  └──────┘ └──────┘ └──────┘ └──────┘ └──────────┘   │    │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────────┐   │    │
│  │  │TOTP  │ │RADIUS│ │DUO   │ │Quick │ │Vault KSM │   │    │
│  │  │      │ │      │ │      │ │Conn. │ │          │   │    │
│  │  └──────┘ └──────┘ └──────┘ └──────┘ └──────────┘   │    │
│  └───────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐    │
│  │                   核心服务                              │    │
│  │  Environment   TokenSessionMap   TunnelRequest         │    │
│  │  ExtensionLoader   LanguageService   ResourceServlet   │    │
│  └───────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐    │
│  │              Spring Boot 基础设施                      │    │
│  │  Tomcat (内嵌)   Actuator   配置管理                   │    │
│  └───────────────────────────────────────────────────────┘    │
└───────────────────────────┬───────────────────────────────────┘
                            │
┌───────────────────────────▼───────────────────────────────────┐
│                    guacd (1.5.5)                               │
│   RDP  │  VNC  │  SSH  │  Telnet  │  Kubernetes               │
└───────────────────────────────────────────────────────────────┘
```

### 关键设计决策对照

| 方面 | 原始版本 (Guice) | Spring Boot 迁移版 |
|------|-------------------|---------------------|
| DI 框架 | Google Guice `@Inject` | Spring `@Autowired` |
| 模块加载 | `AbstractModule.bind()` | `@AutoConfiguration` + `@Bean` |
| REST 框架 | Jersey（不变） | Jersey（不变） |
| WebSocket | Guice 辅助的 JSR 356 | Tomcat 生命周期 + JSR 356 |
| 配置方式 | `guacamole.properties` | `application.yml` |
| 扩展发现 | `ServiceLoader` + `GUACAMOLE_HOME/extensions/` | `ResourcePatternResolver` 类路径扫描 |
| 属性解析 | `LocalEnvironment` + 文件 | Spring `Environment` 桥接 |
| 打包方式 | WAR（部署到 Tomcat） | Spring Boot Fat JAR（内嵌 Tomcat） |
| 构建工具 | Ant / Maven WAR | Spring Boot Maven 插件 |
| Group ID | `org.apache.guacamole` | `com.right` |

---

## 快速开始

详细的逐步操作指南请参阅 [docs/QUICK-START.md](docs/QUICK-START.md)。以下为快速参考。

### 环境要求

- **JDK 17** 或更高版本
- **Maven 3.8+**
- **guacd** 1.5.5 运行中（Docker: `docker run -d -p 4822:4822 guacamole/guacd:1.5.5`）
- **PostgreSQL**（或 MySQL / SQL Server），已初始化 Guacamole 数据库

### 从源码构建并运行

```bash
# 克隆并构建
git clone <repository-url>
cd guacamole-spring-boot
mvn clean package -DskipTests

# 运行
java -jar guacamole/target/guacamole-*.jar
```

### 使用 Maven 插件运行

```bash
mvn spring-boot:run -pl guacamole
```

### 使用 Docker 运行

```bash
docker-compose up -d
```

这会启动三个服务：
- **guacd** —— Guacamole 代理守护进程（镜像: `guacamole/guacd:1.5.5`）
- **postgres** —— PostgreSQL 16（已初始化 `guacamole` 数据库）
- **guacamole** —— Spring Boot 应用程序（从本地源码构建）

### 默认访问信息

| 项目 | 值 |
|------|------|
| URL | `http://localhost:8080/` |
| 默认管理员 | `guacadmin` / `guacadmin`（启用 JDBC 认证时） |

### 初始化数据库

使用 JDBC 认证时，首次运行前需创建数据库模式：

**PostgreSQL:**
```bash
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/001-create-schema.sql
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/002-create-admin-user.sql
```

**MySQL:**
```bash
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/001-create-schema.sql
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/002-create-admin-user.sql
```

**SQL Server:**
```bash
sqlcmd -S localhost -U guacamole -P guacamole -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/001-create-schema.sql
sqlcmd -S localhost -U guacamole -P guacamole -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/002-create-admin-user.sql
```

---

## 扩展模块

### 认证扩展

| 扩展 | Artifact | 说明 | 状态 |
|------|----------|------|------|
| **Header Auth** | `guacamole-auth-header-starter` | 反向代理 SSO：从 HTTP 头（如 `REMOTE_USER`）读取用户名 | ✅ 已验证 |
| **JSON Auth** | `guacamole-auth-json-starter` | 加密令牌认证，无需数据库 | ✅ 已验证 |
| **JDBC MySQL** | `guacamole-auth-mysql-starter` | MySQL 数据库认证和连接存储 | ✅ 已验证 |
| **JDBC PostgreSQL** | `guacamole-auth-postgresql-starter` | PostgreSQL 数据库认证和连接存储 | ✅ 已验证 |
| **JDBC SQL Server** | `guacamole-auth-sqlserver-starter` | SQL Server 数据库认证和连接存储 | ✅ 已验证 |
| **LDAP** | `guacamole-auth-ldap-starter` | LDAP / Active Directory 认证 | ✅ 已验证 |
| **RADIUS** | `guacamole-auth-radius-starter` | RADIUS 认证（PAP、CHAP、MSCHAPv1/v2、EAP-MD5、EAP-TLS） | ✅ 已验证 |
| **TOTP** | `guacamole-auth-totp-starter` | 基于时间的一次性密码（Google Authenticator、Authy 等） | ✅ 已验证 |
| **DUO** | `guacamole-auth-duo-starter` | Duo Security 双因素认证 | ⚠️ 需要升级 |
| **SSO CAS** | `guacamole-auth-sso-cas-starter` | CAS 单点登录 | ✅ 已验证 |
| **SSO OpenID** | `guacamole-auth-sso-openid-starter` | OpenID Connect（Google、Okta、Keycloak 等） | ✅ 已验证 |
| **SSO SAML** | `guacamole-auth-sso-saml-starter` | SAML 2.0 单点登录 | ✅ 已验证 |

### 功能扩展

| 扩展 | Artifact | 说明 | 状态 |
|------|----------|------|------|
| **Quick Connect** | `guacamole-auth-quickconnect-starter` | 通过 URI 创建临时连接（如 `rdp://host:3389`） | ✅ 已验证 |
| **History** | `guacamole-history-starter` | 会话录制存储和回放 | ✅ 已验证 |
| **Vault KSM** | `guacamole-vault-ksm-starter` | Keeper Secrets Manager 凭据注入 | ⏳ 待测试 |

### 互斥约束

- **JDBC 数据库互斥** —— MySQL、PostgreSQL、SQL Server 同时只能启用一个
- **SSO 策略互斥** —— CAS、OpenID Connect、SAML 不能同时启用
- **DUO 需要 SDK v4 升级** —— 当前实现使用 Duo Web SDK v2，该 SDK 已于 2024 年 3 月被 Duo 弃用（参见[已知问题](#已知问题)）

---

## 配置快速参考

默认配置文件位于 `guacamole/src/main/resources/application.yml`。完整的配置参考文档见 **[docs/CONFIGURATION.md](docs/CONFIGURATION.md)**。

### 最小配置 (PostgreSQL)

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

所有扩展默认禁用。在 `application.yml` 中启用：

```yaml
guacamole:
  auth:
    header:
      enabled: true       # 反向代理 SSO
    json:
      enabled: true       # 加密令牌认证
    quickconnect:
      enabled: true       # URI 快速连接
    postgresql:
      enabled: true       # 数据库认证（JDBC：选一个）
    mysql:
      enabled: false
    sqlserver:
      enabled: false
    ldap:
      enabled: true       # LDAP / AD 认证
    totp:
      enabled: true       # TOTP 双因素认证
    radius:
      enabled: true       # RADIUS 认证
    duo:
      enabled: false      # DUO（需要 SDK v4 升级）
    sso-cas:
      enabled: false      # CAS SSO（与其他 SSO 互斥）
    sso-openid:
      enabled: false      # OpenID Connect SSO
    sso-saml:
      enabled: false      # SAML 2.0 SSO
  vault:
    ksm:
      enabled: false      # Vault KSM
  history:
    enabled: true         # 会话录制
```

扩展仅在同时满足以下两个条件时加载：
1. Maven 依赖存在于 `guacamole/pom.xml` 中
2. `enabled` 属性设置为 `true`

### 属性映射对照

原始 `guacamole.properties` 扁平命名空间已重组为 YAML 层次结构，置于 `guacamole.*` 下。例如：

| 原始属性 | application.yml 路径 |
|----------|----------------------|
| `guacd-hostname` | `guacamole.guacd.hostname` |
| `mysql-hostname` | `guacamole.auth.mysql.mysql-hostname` |
| `ldap-hostname` | `guacamole.auth.ldap.ldap-hostname` |
| `cas-authorization-endpoint` | `guacamole.auth.sso-cas.cas-authorization-endpoint` |
| `openid-issuer` | `guacamole.auth.sso-openid.openid-issuer` |
| `saml-idp-url` | `guacamole.auth.sso-saml.saml-idp-url` |
| `recording-search-path` | `guacamole.history.recording-search-path` |

数据库连接使用 **Spring Boot `spring.datasource`** 标准方式配置，取代了旧的 `<db>-hostname` / `<db>-port` 属性。

完整属性映射表请参阅 **[docs/MIGRATION.md](docs/MIGRATION.md)**。

---

## 迁移状态总览

本项目是对 **Apache Guacamole 1.5.5**（上游：[apache/guacamole-client](https://github.com/apache/guacamole-client)）从原始 Guice 架构到 Spring Boot 3.3.5 的完整迁移。

### 已迁移组件

| 组件 | 状态 | 说明 |
|------|------|------|
| `guacamole-common`（协议库，47 文件） | ✅ 完成 | Java 17，Jakarta 命名空间 |
| `guacamole-common-js`（JavaScript API，38 模块） | ✅ 完成 | 不变，通过 Maven reactor 引入 |
| `guacamole-ext`（扩展 API，136 文件） | ✅ 完成 | AuthenticationProvider 等 |
| `guacamole`（Web 应用程序） | ✅ 完成 | Spring Boot 主应用，Jersey，WebSocket |
| `guacamole-auth-jdbc`（MySQL） | ✅ 完成 | Spring Boot Starter + MyBatis |
| `guacamole-auth-jdbc`（PostgreSQL） | ✅ 完成 | Spring Boot Starter + MyBatis |
| `guacamole-auth-jdbc`（SQL Server） | ✅ 完成 | Spring Boot Starter + MyBatis |
| `guacamole-auth-header-starter` | ✅ 完成 | `@ConditionalOnProperty` 启用 |
| `guacamole-auth-json-starter` | ✅ 完成 | 加密令牌认证 |
| `guacamole-auth-ldap-starter` | ✅ 完成 | Apache Directory LDAP API |
| `guacamole-auth-radius-starter` | ✅ 完成 | JRadius 库 |
| `guacamole-auth-totp-starter` | ✅ 完成 | ZXing 二维码生成 |
| `guacamole-auth-quickconnect-starter` | ✅ 完成 | URI 快速连接 |
| `guacamole-auth-sso-cas-starter` | ✅ 完成 | CAS 客户端 |
| `guacamole-auth-sso-openid-starter` | ✅ 完成 | jose4j JWT 库 |
| `guacamole-auth-sso-saml-starter` | ✅ 完成 | OneLogin java-saml 工具包 |
| `guacamole-history-starter` | ✅ 完成 | 会话录制 |
| `guacamole-vault-ksm-starter` | ✅ 代码完成 | 等待 KSM 账号测试 |

### 已变更内容

| 方面 | 上游原始版 | 本项目 |
|------|-----------|--------|
| 构建系统 | Ant + Maven WAR | Spring Boot Maven 插件 |
| DI 框架 | Google Guice | Spring IoC / `@Autowired` |
| 打包方式 | WAR（部署到 Servlet 容器） | Fat JAR（内嵌 Tomcat） |
| 配置方式 | `guacamole.properties` 文件 | `application.yml` |
| 扩展加载 | `GUACAMOLE_HOME/extensions/*.jar` | Maven 依赖 + `@ConditionalOnProperty` |
| 扩展打包 | 普通 JAR | Spring Boot Starter |
| Group ID | `org.apache.guacamole` | `com.right` |
| Java 版本 | 8 / 11 | 17 |
| Servlet API | javax（Tomcat 8/9） | jakarta（Tomcat 10 / Spring Boot 3.x） |
| JDBC 框架 | 手动 DataSource + JDBC | MyBatis Spring Boot Starter |

### 已保留内容

- **所有 REST API 端点** —— 不变，与现有客户端完全兼容
- **WebSocket 隧道协议** —— 不变
- **AngularJS 1.8 前端** —— 保留自上游，通过 webpack 构建
- **数据库模式** —— 相同的 SQL 脚本，无需数据迁移
- **扩展 API** —— `AuthenticationProvider` 接口和 `guac-manifest.json` 结构
- **构建时 JS/CSS 压缩** —— Google Closure Compiler（与上游相同）
- **许可证** —— Apache 2.0（保留所有原始版权声明）

---

## 构建与部署

### 构建命令

```bash
# 完整构建（所有模块）
mvn clean package -DskipTests

# 仅构建 Web 应用程序
mvn clean package -pl guacamole -am -DskipTests

# 构建并运行测试
mvn clean package

# 跳过前端构建
mvn clean package -Dskip.frontend

# 强制 npm install（当 package.json 变更时）
mvn clean package -Dforce.npm.install
```

构建会自动跳过 `node_modules` 存在时的 `npm install`（通过 `skip-npm-install` profile）。依赖变更后使用 `-Dforce.npm.install` 覆盖。

### 前端开发

```bash
cd guacamole/frontend
npm ci
npm run build       # 生产构建（webpack + Google Closure Compiler）
npm run dev         # 开发监听模式
```

前端是 AngularJS 1.8 应用程序，使用 webpack 4 构建。构建输出位于 `guacamole/src/main/resources/static/`。`GuacamoleSpringBootApplication` 将其作为静态资源提供服务。

### Docker

```bash
# 构建并启动所有服务（guacd + PostgreSQL + Guacamole）
docker-compose up -d

# 仅构建 Guacamole 镜像
docker build -t guacamole-spring-boot .

# 覆盖环境变量
GUACAMOLE_DB_PASSWORD=secret123 docker-compose up -d
```

Dockerfile 使用多阶段构建：
1. **构建阶段** —— `maven:3.9-eclipse-temurin-17-alpine` 编译并打包应用程序
2. **运行阶段** —— `eclipse-temurin:17-jre-alpine` 以非 root 用户（`guacamole`）运行 Fat JAR

docker-compose.yml 将 guacd（端口 4822）、PostgreSQL 16（含健康检查）和 Guacamole 应用程序（端口 8080）编排为三个服务。数据库密码默认为 `guacamole`，可通过 `GUACAMOLE_DB_PASSWORD` 环境变量覆盖。

### 构建标识符

构建时生成 `${guacamole.build.identifier}` 时间戳（`yyyyMMddHHmmss` 格式），通过 Maven 资源过滤注入到：
- `static/index.html` —— 缓存破坏参数
- `static/verifyCachedVersion.js` —— 版本验证

此行为与原版 Apache Guacamole 构建一致。`maven-resources-plugin` 配置为使用 `${}` 分隔符（从 Spring Boot 默认的 `@` 分隔符恢复）。

---

## 项目结构

```
guacamole-spring-boot/
├── pom.xml                                      # 根 POM（Spring Boot parent 3.3.5）
├── Dockerfile                                   # 多阶段 Docker 构建
├── docker-compose.yml                           # guacd + PostgreSQL + Guacamole
│
├── guacamole-common/                            # Guacamole 协议库（Java，47 文件）
├── guacamole-common-js/                         # Guacamole JavaScript API（npm 包，38 模块）
├── guacamole-ext/                               # 扩展 API（AuthenticationProvider 等，136 文件）
│
├── guacamole/                                   # 主 Web 应用程序（Spring Boot）
│   ├── pom.xml                                  # 所有扩展依赖在此声明
│   ├── frontend/                                # AngularJS 1.8 前端（webpack）
│   │   ├── package.json
│   │   ├── webpack.config.js
│   │   ├── plugins/                             # 自定义 webpack 插件
│   │   └── src/                                 # 前端源码（JS、CSS、模板）
│   └── src/main/
│       ├── java/org/apache/guacamole/
│       │   ├── GuacamoleSpringBootApplication.java   # @SpringBootApplication 入口
│       │   ├── config/                                # Spring @Configuration 配置类
│       │   ├── extension/                        # 扩展加载和清单解析
│       │   ├── resource/                         # ResourceServlet 提供扩展资源
│       │   ├── rest/                             # Jersey REST 资源类
│       │   └── tunnel/                           # WebSocket 隧道端点
│       └── resources/
│           ├── application.yml                   # 默认配置
│           ├── logback-spring.xml                # 日志配置
│           └── static/                           # 前端构建输出（自动生成）
│
├── extensions/
│   ├── guacamole-auth-jdbc/                      # JDBC 认证（多模块）
│   │   ├── guacamole-auth-jdbc-base/             # 共享 JDBC 代码 + MyBatis 映射器
│   │   ├── guacamole-auth-mysql-starter/         # MySQL Starter
│   │   ├── guacamole-auth-postgresql-starter/    # PostgreSQL Starter
│   │   ├── guacamole-auth-sqlserver-starter/     # SQL Server Starter
│   │   └── pom.xml
│   │
│   ├── guacamole-auth-sso/                       # SSO 认证（多模块）
│   │   ├── guacamole-auth-sso-base/              # 共享 SSO 代码
│   │   ├── guacamole-auth-sso-cas-starter/       # CAS Starter
│   │   ├── guacamole-auth-sso-openid-starter/    # OpenID Connect Starter
│   │   ├── guacamole-auth-sso-saml-starter/      # SAML 2.0 Starter
│   │   └── pom.xml
│   │
│   ├── guacamole-vault/                          # Vault 凭据管理（多模块）
│   │   ├── guacamole-vault-base/                 # 共享 Vault 抽象
│   │   ├── guacamole-vault-ksm-starter/          # Keeper Secrets Manager Starter
│   │   └── pom.xml
│   │
│   ├── guacamole-auth-header-starter/            # HTTP 头认证
│   ├── guacamole-auth-json-starter/              # 加密 JSON 令牌认证
│   ├── guacamole-auth-ldap-starter/              # LDAP / Active Directory 认证
│   ├── guacamole-auth-radius-starter/            # RADIUS 认证
│   ├── guacamole-auth-totp-starter/              # 基于时间的一次性密码
│   ├── guacamole-auth-duo-starter/               # Duo Security 双因素认证
│   ├── guacamole-auth-quickconnect-starter/      # URI 快速连接
│   └── guacamole-history-starter/                # 会话录制存储
│
└── docs/                                         # 文档
    ├── QUICK-START.md                            # 快速入门指南（新）
    ├── ARCHITECTURE.md                           # 深入架构分析
    ├── BUILD.md                                  # 构建、Docker、部署指南
    ├── CONFIGURATION.md                          # 完整扩展配置参考
    ├── EXTENSIONS.md                             # 扩展系统与开发指南
    ├── MIGRATION.md                              # 迁移参考（Guice -> Spring Boot）
    ├── REST-API.md                               # REST API 参考
    └── vault-module.md                           # Vault/KSM 模块文档
```

### 关键依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.3.5 | 应用框架 |
| Java | 17 | 运行时 |
| Jersey | 3.1.x | JAX-RS REST 层 |
| MyBatis Spring Boot | 3.0.3 | JDBC 数据库访问 |
| Guava | 32.1.3-jre | 工具库 |
| Jackson | 2.17.2 | JSON 序列化 |
| ZXing | 3.5.3 | 二维码生成（TOTP） |
| Apache Directory LDAP API | 2.1.6 | LDAP 认证 |
| JRadius | 1.1.5 | RADIUS 认证 |
| jose4j | 0.9.6 | JWT / JOSE（OpenID） |
| OneLogin java-saml | 2.9.0 | SAML 2.0 工具包 |
| CAS Client | 3.6.4 | CAS SSO |
| Keeper KSM Core | 16.6.3 | Keeper Secrets Manager |
| Kotlin | 1.9.23 | Keeper KSM SDK 所需 |
| KotlinX Serialization | 1.6.3 | Keeper KSM SDK 所需 |
| Bouncy Castle FIPS | 1.0.2.4 | 加密库 |

---

## 已知问题

### DUO SDK v2 已弃用

DUO 扩展当前使用 **Duo Web SDK v2**（基于 iframe），该 SDK 已于 **2024 年 3 月被 Duo 弃用**。扩展可以无错误启动，但 Duo 服务器将阻止认证。

需要迁移到 **Duo Web SDK v4**：
- **前端**：iframe 集成 -> 重定向集成
- **后端**：HMAC SHA-256 -> HMAC SHA-512
- **配置**：`duo-application-key` 移除；`ikey`/`skey` 重命名为 `client_id`/`client_secret`
- **参考**：https://duo.com/docs/duoweb

### Vault KSM 待测试

`guacamole-vault-ksm-starter` 模块**代码已完成**且编译通过，但尚未使用真实的 Keeper Secrets Manager 账号进行端到端测试。测试因缺少 KSM 订阅而被阻塞。该模块使用：
- Keeper Security Secrets Manager Core SDK 16.6.3
- Kotlin 1.9.23（KSM SDK 所需）
- KotlinX Serialization 1.6.3
- Bouncy Castle FIPS 1.0.2.4

### 其他注意事项

- **需要 Java 17** —— 本项目面向 Java 17，前端 webpack 构建使用 `--openssl-legacy-provider`
- **前端构建需要 Node.js 18** —— `frontend-maven-plugin` 自动安装 Node.js 18.18.0 和 npm 9.8.1
- **不支持 Jetty** —— 应用程序依赖内嵌 Tomcat 的 WebSocket 支持；替换为 Jetty 需要修改 WebSocket 配置

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [QUICK-START.md](docs/QUICK-START.md) | 快速入门：环境检查、安装、首次登录（新用户请从这里开始） |
| [CONFIGURATION.md](docs/CONFIGURATION.md) | 所有扩展的完整配置参考 |
| [BUILD.md](docs/BUILD.md) | 构建、Docker 和部署指南 |
| [EXTENSIONS.md](docs/EXTENSIONS.md) | 扩展系统架构和开发指南 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 深入系统架构分析 |
| [MIGRATION.md](docs/MIGRATION.md) | Guice 到 Spring Boot 的迁移参考，含属性映射表 |
| [REST-API.md](docs/REST-API.md) | REST API 端点参考 |
| [vault-module.md](docs/vault-module.md) | Vault / KSM 模块详细文档 |

---

## 许可证

本项目是 [Apache Guacamole 1.5.5](https://guacamole.apache.org/) 的衍生作品，基于 **Apache License 2.0** 许可发布。

### 源代码许可

- 所有从 Apache Guacamole 继承的源文件保留其原始 Apache 2.0 许可证头
- 新增的 Spring Boot 适配代码同样使用 Apache 2.0 许可
- 完整许可证文本请参见项目根目录下的 [LICENSE](LICENSE) 文件
- 第三方归属声明请参见 [NOTICE](NOTICE) 文件

### 商标声明

Apache Guacamole、Apache 和 Apache 羽毛标志是 [Apache 软件基金会](https://www.apache.org/) 的注册商标。本项目与 Apache 软件基金会无任何隶属关系，亦未获得其认可或赞助。

### 第三方组件

本项目使用了多个第三方开源组件，各组件遵循其各自的许可证条款，详见 [LICENSE](LICENSE) 文件。

> **注意：** JRadius（RADIUS 认证模块依赖）使用 LGPL 2.1 许可证。该库为可选依赖，仅在启用 RADIUS 认证时使用。如需分发包含 JRadius 的二进制文件，请注意 LGPL 2.1 与 Apache 2.0 的兼容性要求。
