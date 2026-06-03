# Guacamole Spring Boot 快速入门指南

[← 返回文档索引](../README_zh.md#documentation-index)

本文面向首次使用的用户，手把手引导您从零搭建并运行 Guacamole Spring Boot 系统。

---

## 目录

- [环境检查清单](#环境检查清单)
- [安装 guacd](#安装-guacd)
- [创建数据库](#创建数据库)
- [配置 application.yml](#配置-applicationyml)
- [构建项目](#构建项目)
- [启动应用](#启动应用)
- [首次登录](#首次登录)
- [创建第一个连接](#创建第一个连接)
- [常见启动问题排查](#常见启动问题排查)
- [下一步](#下一步)

---

## 环境检查清单

在开始之前，请确保您的系统满足以下要求：

| 项目 | 要求 | 验证命令 |
|------|------|----------|
| JDK | 17 或更高版本 | `java -version` |
| Maven | 3.8+ | `mvn --version` |
| Docker | 任意版本（可选，用于 guacd 和/或数据库） | `docker --version` |
| PostgreSQL 客户端 | psql（可选，如需手动初始化数据库） | `psql --version` |
| Node.js | 18.x（构建前端时自动安装，无需手动准备） | — |

如果缺少某个组件，请先安装后再继续。

---

## 安装 guacd

guacd 是 Guacamole 的代理守护进程，负责解析和渲染远程桌面协议（RDP、VNC、SSH 等）。

### 使用 Docker 安装（推荐）

一行命令即可：

```bash
docker run -d \
  --name guacd \
  -p 4822:4822 \
  guacamole/guacd:1.5.5
```

验证 guacd 是否正常运行：

```bash
docker logs guacd
```

您应该看到类似以下的输出：

```
guacd[1]: INFO: Guacamole proxy daemon (guacd) version 1.5.5 started
```

> **注意**：如果系统中有防火墙，请确保端口 4822 已放行。guacd 默认监听 TCP 4822 端口。

### 不使用 Docker 的安装方式

如果您不使用 Docker，可以参考 [Apache Guacamole 官方文档](https://guacamole.apache.org/doc/gug/installing-guacamole.html) 从源码编译 guacd，或使用系统包管理器安装（如 `apt install guacamole`）。

---

## 创建数据库

Guacamole 需要数据库来存储用户、连接和权限信息。以下以 PostgreSQL 为例，MySQL 和 SQL Server 的步骤类似。

### 使用 Docker 创建 PostgreSQL

```bash
docker run -d \
  --name guacamole-db \
  -e POSTGRES_DB=guacamole \
  -e POSTGRES_USER=guacamole \
  -e POSTGRES_PASSWORD=guacamole \
  -p 5432:5432 \
  postgres:16-alpine
```

### 初始化数据库模式

Guacamole 提供 SQL 脚本来创建所需的表结构和默认管理员用户。

**PostgreSQL：**

```bash
# 创建表结构
docker exec -i guacamole-db psql -U guacamole -d guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/001-create-schema.sql

# 创建默认管理员用户（guacadmin/guacadmin）
docker exec -i guacamole-db psql -U guacamole -d guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/002-create-admin-user.sql
```

**MySQL：**

```bash
# 创建表结构
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/001-create-schema.sql

# 创建默认管理员用户（guacadmin/guacadmin）
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/002-create-admin-user.sql
```

**SQL Server：**

```bash
# 创建表结构
sqlcmd -S localhost -U guacamole -P guacamole -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/001-create-schema.sql

# 创建默认管理员用户（guacadmin/guacadmin）
sqlcmd -S localhost -U guacamole -P guacamole -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/002-create-admin-user.sql
```

> **验证**：初始化后，数据库中应包含 `guacamole_user`、`guacamole_connection`、`guacamole_connection_permission` 等表。

---

## 配置 application.yml

项目默认配置文件位于 `guacamole/src/main/resources/application.yml`。以下是最小可用配置。

### 最小配置（PostgreSQL）

```yaml
server:
  port: 8080

guacamole:
  guacd:
    hostname: localhost           # guacd 的地址
    port: 4822                    # guacd 的端口
  auth:
    postgresql:
      enabled: true               # 启用 PostgreSQL 认证

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: guacamole           # 建议使用环境变量引用
    driver-class-name: org.postgresql.Driver
```

> **安全建议**：不要在配置文件中硬编码数据库密码。使用环境变量引用：`password: ${GUACAMOLE_DB_PASSWORD}`。

### 使用 Docker 时的配置差异

如果您使用 `docker-compose up -d` 启动整个系统，以上配置已内置在默认 `application.yml` 中。Docker 环境中的数据库地址应为服务名 `postgres` 而非 `localhost`，docker-compose.yml 已通过 `SPRING_DATASOURCE_URL` 环境变量自动处理此差异。

---

## 构建项目

### 首次构建（完整构建）

在项目根目录执行：

```bash
mvn clean package -DskipTests
```

此命令会：
1. 编译 `guacamole-common`、`guacamole-common-js`、`guacamole-ext` 三个核心模块
2. 编译所有 17 个扩展模块
3. 安装 Node.js 18.18.0 和 npm 9.8.1（首次构建时自动下载）
4. 执行 `npm install` 安装前端依赖
5. 使用 webpack 构建 AngularJS 前端
6. 打包为 Spring Boot Fat JAR

构建成功后，JAR 文件位于：`guacamole/target/guacamole-1.5.5.jar`

> **注意**：首次构建可能需要 5-10 分钟，具体取决于网络速度。后续构建由于前端依赖已缓存，通常只需 1-2 分钟。

### 构建选项

| 选项 | 说明 |
|------|------|
| `-DskipTests` | 跳过测试 |
| `-Dskip.frontend` | 跳过全部前端构建（最快） |
| `-Dforce.npm.install` | 强制重新执行 `npm install` |

示例（跳过前端，仅构建后端）：

```bash
mvn clean package -DskipTests -Dskip.frontend
```

---

## 启动应用

### 直接运行 Fat JAR

```bash
java -jar guacamole/target/guacamole-*.jar
```

### 使用 Maven 插件运行（开发模式）

```bash
mvn spring-boot:run -pl guacamole
```

### 使用 Docker Compose 运行

```bash
docker-compose up -d
```

这会同时启动 guacd、PostgreSQL 和 Guacamole 三个容器。

### 验证启动

启动成功后，您应该能在控制台看到类似以下输出：

```
[----------------------------------------------------------]
	启动成功！访问地址:	http://192.168.1.100:8080
[----------------------------------------------------------]
```

此时，在浏览器中打开 `http://localhost:8080/` 即可看到 Guacamole 登录页面。

---

## 首次登录

### 登录信息

| 项目 | 值 |
|------|------|
| URL | `http://localhost:8080/` |
| 用户名 | `guacadmin` |
| 密码 | `guacadmin` |

> **注意**：默认管理员账号仅在启用 JDBC 认证（PostgreSQL/MySQL/SQL Server）且执行了 `002-create-admin-user.sql` 后才会存在。请在生产环境中立即修改默认密码。

### 登录步骤

1. 在浏览器中打开 `http://localhost:8080/`
2. 看到 Guacamole 登录界面（紫色主题）
3. 输入用户名 `guacadmin`，密码 `guacadmin`
4. 点击"登录"按钮

登录成功后，您将进入 Guacamole 的主界面，左侧显示用户菜单和连接列表（目前为空）。

---

## 创建第一个连接

以创建一个 SSH 连接为例：

1. **登录后**，点击右上角的设置图标（齿轮）
2. **在设置页面**，点击左侧"连接"菜单
3. **点击右上角的"新建连接"按钮**
4. **填写连接信息**：
   - **名称**：输入连接名称，例如 `我的 SSH 服务器`
   - **位置**：保持默认（根组）
   - **协议**：选择 `SSH`
   - **参数**：
     - **主机名**：输入目标服务器的 IP 或域名
     - **端口**：默认 `22`
     - **用户名**：登录用户名
     - **密码**：登录密码（或不填，使用认证凭据设置）
   - **其他选项**：可根据需要调整颜色深度、字体等
5. **点击"保存"**

### 测试连接

1. 返回主界面，您应该能在连接列表中看到刚创建的连接
2. 点击连接名称
3. 浏览器将打开 WebSocket 隧道，通过 guacd 连接到目标服务器
4. 如果一切正常，您将看到 SSH 终端的登录提示

> **故障排除**：如果连接失败，请检查：
> - guacd 是否在运行：`docker ps | grep guacd`
> - 目标服务器是否可达：`ping <目标 IP>`
> - guacd 端口是否可访问：`telnet localhost 4822`（从 Guacamole 服务器）
> - 应用日志中是否有错误信息

---

## 常见启动问题排查

### 问题 1：启动时提示数据库连接失败

**错误信息**：`Cannot create PoolableConnectionFactory` 或 `Connection refused`

**可能原因**：
- 数据库未启动
- 数据库连接信息配置错误
- 防火墙阻止了数据库端口

**解决方法**：
- 检查数据库是否运行：`docker ps | grep postgres`
- 检查 `spring.datasource.url` 中的地址和端口是否正确
- 确认数据库用户名和密码正确

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole   # 确认地址和端口
    username: guacamole                                 # 确认用户名
    password: ${GUACAMOLE_DB_PASSWORD:guacamole}        # 确认密码
```

### 问题 2：启动时提示 guacd 连接失败

**错误信息**：`Connection refused` 或 `guacd: Connection closed`

**可能原因**：
- guacd 未启动
- `guacamole.guacd.hostname` 配置错误
- guacd 端口配置与运行端口不一致

**解决方法**：
```yaml
guacamole:
  guacd:
    hostname: localhost           # 改为 guacd 实际运行的地址
    port: 4822                    # 确认端口与 docker run -p 一致
```

### 问题 3：空白页面或前端加载失败

**可能原因**：
- 前端构建未正确运行
- 静态资源被浏览器缓存

**解决方法**：
```bash
# 重新构建前端
mvn clean package -DskipTests
# 或使用无缓存刷新（Ctrl+Shift+R / Cmd+Shift+R）
```

### 问题 4：端口被占用

**错误信息**：`Web server failed to start. Port 8080 was already in use.`

**解决方法**：修改端口或结束占用进程
```yaml
server:
  port: 9090    # 改为其他可用端口
```

### 问题 5：扩展未生效

**已启用扩展但未加载的原因**：
- 扩展依赖未添加到 `guacamole/pom.xml` 中（仅修改 `application.yml` 不够）
- 扩展的 `enabled` 属性未设为 `true`

**验证方法**：启动日志中会打印已加载的扩展模块。

---

## 下一步

恭喜！您已成功搭建并运行了 Guacamole Spring Boot。您现在可以：

1. **深入了解配置** —— 参阅 [CONFIGURATION.md](CONFIGURATION.md) 了解所有扩展的详细配置选项
2. **配置 LDAP 认证** —— 集成企业 LDAP/AD 目录
3. **配置 SSO** —— 使用 CAS、OpenID Connect 或 SAML 实现单点登录
4. **启用 TOTP 双因素认证** —— 增加账户安全性
5. **配置会话录制** —— 审计和回放用户会话
6. **自定义构建和部署** —— 参阅 [BUILD.md](BUILD.md) 了解 Docker、CI/CD 等高级构建选项
7. **了解系统架构** —— 参阅 [ARCHITECTURE.md](ARCHITECTURE.md) 深入了解内部设计
8. **开发扩展** —— 参阅 [EXTENSIONS.md](EXTENSIONS.md) 学习如何开发自定义扩展

### 生产环境注意事项

- **修改默认密码**：首次登录后立即修改 `guacadmin` 密码
- **使用环境变量**：敏感信息（密码、密钥）通过环境变量注入，勿硬编码
- **配置 SSL/TLS**：为 Web 界面配置 HTTPS
- **限制端口暴露**：仅对外暴露 8080/443 端口，guacd 端口 4822 和数据库端口不应对外开放
- **开启审计日志**：启用 History 扩展记录用户操作
- **定期备份数据库**：`pg_dump`（PostgreSQL）或 `mysqldump`（MySQL）
- **资源监控**：通过 Spring Boot Actuator 端点监控应用健康状态
