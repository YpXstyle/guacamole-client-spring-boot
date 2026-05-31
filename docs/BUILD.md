# 构建与部署指南

## 目录

- [环境要求](#环境要求)
- [源码构建](#源码构建)
- [前端开发](#前端开发)
- [运行应用](#运行应用)
- [Docker 部署](#docker-部署)
- [生产环境部署](#生产环境部署)
- [数据库初始化](#数据库初始化)
- [Maven 构建系统](#maven-构建系统)
- [故障排除](#故障排除)

---

## 环境要求

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | Java 编译和运行 |
| Maven | 3.8+ | 构建系统 |
| Node.js | 18.18.0 | 前端构建（由 `frontend-maven-plugin` 自动安装） |
| npm | 9.8.1 | 前端依赖（由 `frontend-maven-plugin` 自动安装） |
| guacd | 1.5.5 | Guacamole 代理守护进程（处理连接） |
| PostgreSQL | 14+ | 数据库（可选，也支持 MySQL 8+、SQL Server 2019+） |

---

## 源码构建

### 完整构建

```bash
cd guacamole-spring-boot
mvn clean package -DskipTests
```

构建产物位于 `guacamole/target/guacamole-*.jar`。

### 按模块构建

```bash
# 仅构建 Web 应用及其依赖
mvn clean package -pl guacamole -am -DskipTests

# 仅构建扩展模块
mvn clean package -pl extensions -am -DskipTests
```

### 含测试构建

```bash
mvn clean package
```

### 构建阶段

构建按以下阶段执行：

1. **`generate-resources`** — 安装 Node.js、`npm ci`、webpack 构建（前端 → `static/`）
2. **`process-resources`** — Maven 资源过滤（`${guacamole.build.identifier}`）、复制 `guacamole-common-js`、复制翻译文件
3. **`compile`** — Java 编译
4. **`prepare-package`** — JS/CSS 压缩（Closure Compiler，`minify-maven-plugin`）
5. **`package`** — Spring Boot 打包（Fat JAR）

### 构建标识符

构建时间戳通过 Maven 资源过滤注入到 `index.html` 和 `verifyCachedVersion.js` 中：

```
maven.build.timestamp.format = yyyyMMddHHmmss
guacamole.build.identifier = ${maven.build.timestamp}
```

这实现了浏览器的自动缓存失效：每次构建生成唯一的标识符，`verifyCachedVersion.js` 在部署新版本时强制刷新浏览器缓存。

---

## 前端开发

前端是使用 webpack 4 构建的 AngularJS 1.8.3 应用。

```bash
cd guacamole/frontend

# 安装依赖
npm ci

# 生产构建（输出到 ../src/main/resources/static/）
npm run build

# 开发模式（监听文件变化自动重新构建）
npm run dev
```

### 前端构建流程

1. **webpack** 将 `frontend/src/app/` 的 JS/CSS 打包为 `guacamole.[contenthash].js` / `guacamole.[contenthash].css`
2. **AngularTemplateCacheWebpackPlugin** 将 HTML 模板打包为 `templates.js`
3. **TerserPlugin** 压缩 JS（输出 ES5 兼容代码）
4. **CssMinimizerPlugin** 压缩 CSS
5. **CopyPlugin** 复制静态资源（图片、字体、polyfill）和 `node_modules/` 中的核心库
6. **CleanWebpackPlugin** 清理 `static/` 目录（保留 `guacamole-common-js/`）
7. **HtmlWebpackPlugin** 生成 `index.html`（带内容哈希的 URL）

Maven 构建通过 `frontend-maven-plugin` 自动化此流程——无需手动安装 Node.js。

---

## 运行应用

### 直接运行 JAR

```bash
java -jar guacamole/target/guacamole-*.jar
```

### Maven Spring Boot 插件

```bash
mvn spring-boot:run -pl guacamole
```

### 使用自定义配置

```bash
java -jar guacamole/target/guacamole-*.jar --spring.config.location=/path/to/application.yml
```

### 覆盖属性

```bash
java -jar guacamole/target/guacamole-*.jar \
  --server.port=9090 \
  --spring.datasource.password=secret \
  --guacamole.guacd.hostname=guacd.example.com
```

### 设置时区

```bash
java -Duser.timezone=Asia/Shanghai -jar guacamole/target/guacamole-*.jar
```

---

## Docker 部署

### 快速启动

```bash
docker-compose up -d
```

这会启动：
- **guacd**（guacamole/guacd:1.5.5）— 端口 4822
- **PostgreSQL**（postgres:16-alpine）— 仅内部访问
- **Guacamole**（从 Dockerfile 构建）— 端口 8080

### 环境变量

```bash
# 设置自定义数据库密码
GUACAMOLE_DB_PASSWORD=securepass123 docker-compose up -d

# 自定义时区
JAVA_TIMEZONE="-Duser.timezone=America/New_York" docker-compose up -d

# 自定义数据源 URL
SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db.example.com:5432/guacamole docker-compose up -d
```

### 构建 Docker 镜像

```bash
docker build -t guacamole-spring-boot:latest .
```

Dockerfile 使用**多阶段构建**：
- **阶段 1（`build`）**：Maven + JDK 17 编译
- **阶段 2（`runtime`）**：仅 JRE 17 Alpine + 构建好的 JAR

### 生产环境 Docker Compose

```yaml
version: '3.8'
services:
  guacd:
    image: guacamole/guacd:1.5.5
    restart: always
    ports:
      - "127.0.0.1:4822:4822"    # 仅绑定本地回环地址

  postgres:
    image: postgres:16-alpine
    restart: always
    environment:
      POSTGRES_DB: guacamole
      POSTGRES_USER: guacamole
      POSTGRES_PASSWORD: ${GUACAMOLE_DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U guacamole"]
      interval: 10s
      timeout: 5s
      retries: 5

  guacamole:
    build: .
    restart: always
    ports:
      - "8080:8080"
    environment:
      GUACAMOLE_DB_PASSWORD: ${GUACAMOLE_DB_PASSWORD}
      GUACAMOLE_HISTORY_RECORDING_PATH: ${GUACAMOLE_HISTORY_RECORDING_PATH:-/tmp/guacamole/recordings}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/guacamole
    depends_on:
      guacd:
        condition: service_started
      postgres:
        condition: service_healthy
    volumes:
      - /path/to/recordings:/tmp/guacamole/recordings

volumes:
  postgres-data:
```

---

## 数据库初始化

### PostgreSQL

```bash
# 创建数据库和用户
sudo -u postgres psql <<EOF
CREATE USER guacamole WITH PASSWORD 'securepass';
CREATE DATABASE guacamole OWNER guacamole;
GRANT ALL PRIVILEGES ON DATABASE guacamole TO guacamole;
EOF

# 初始化表结构
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/001-create-schema.sql
psql -h localhost -U guacamole -d guacamole \
  -f extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/002-create-admin-user.sql
```

### MySQL

```bash
mysql -u root -p <<EOF
CREATE DATABASE guacamole;
CREATE USER 'guacamole'@'%' IDENTIFIED BY 'securepass';
GRANT ALL PRIVILEGES ON guacamole.* TO 'guacamole'@'%';
FLUSH PRIVILEGES;
EOF

mysql -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/001-create-schema.sql
mysql -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/002-create-admin-user.sql
```

### SQL Server

```bash
sqlcmd -S localhost -U sa -P 'YourPassword' <<EOF
CREATE DATABASE guacamole;
GO
USE guacamole;
GO
-- 按顺序执行建表脚本（使用 sqlcmd -i 逐个执行 .sql 文件）
EOF
```

### 版本升级

每个数据库模块在 `schema/upgrade/` 下包含升级脚本。按顺序执行：

```bash
ls extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/upgrade/
# upgrade-pre-0.9.7.sql, upgrade-pre-0.9.8.sql, ..., upgrade-pre-1.0.0.sql
```

只执行比当前 schema 版本更新的升级脚本。

---

## Maven 构建系统

### 模块层次

```
guacamole-client-spring-boot (pom)
├── guacamole-common                         → 协议库 (JAR)
├── guacamole-common-js                      → JavaScript API (JAR)
├── guacamole-ext                            → 扩展 API (JAR)
├── guacamole                                → Web 应用 (Spring Boot JAR)
└── extensions/
    ├── guacamole-auth-jdbc (pom)
    │   ├── guacamole-auth-jdbc-base         → JDBC 共享代码 (JAR)
    │   ├── guacamole-auth-mysql-starter     → MySQL Starter (JAR)
    │   ├── guacamole-auth-postgresql-starter→ PostgreSQL Starter (JAR)
    │   └── guacamole-auth-sqlserver-starter → SQL Server Starter (JAR)
    ├── guacamole-auth-header-starter
    ├── guacamole-auth-json-starter
    ├── guacamole-auth-ldap-starter
    ├── guacamole-auth-radius-starter
    ├── guacamole-auth-totp-starter
    ├── guacamole-auth-duo-starter
    ├── guacamole-auth-quickconnect-starter
    ├── guacamole-auth-sso (pom)
    │   ├── guacamole-auth-sso-base
    │   ├── guacamole-auth-sso-cas-starter
    │   ├── guacamole-auth-sso-openid-starter
    │   └── guacamole-auth-sso-saml-starter
    ├── guacamole-vault (pom)
    │   ├── guacamole-vault-base
    │   └── guacamole-vault-ksm-starter
    └── guacamole-history-starter
```

### 关键插件

| 插件 | 用途 |
|------|------|
| `spring-boot-maven-plugin` | Fat JAR 打包，`repackage` 目标 |
| `frontend-maven-plugin` | 自动安装 Node.js，执行 `npm ci` + webpack 构建 |
| `minify-maven-plugin`（Closure Compiler） | 扩展 JS/CSS 压缩 |
| `maven-resources-plugin` | 资源过滤（`${*}` 分隔符）、资源复制 |
| `maven-compiler-plugin` | Java 17 编译，启用 `-Xlint:all` |

### 资源过滤

根 POM 覆盖了 Spring Boot 的 `@` 分隔符，恢复 Maven 原生的 `${*}` 分隔符：

```xml
<plugin>
    <artifactId>maven-resources-plugin</artifactId>
    <configuration>
        <useDefaultDelimiters>true</useDefaultDelimiters>
    </configuration>
</plugin>
```

这样 `${guacamole.build.identifier}`（构建信息）和 `@...@`（Spring Boot 在 `application.yml` 中的占位符）可以**共存**。

**选择性过滤**：仅对 `index.html`、`verifyCachedVersion.js` 和 `application*.yml/properties` 进行过滤。其他所有资源（JSON、JS、CSS、图片）原样输出。

### 依赖管理

所有依赖版本集中在根 POM 的 `<dependencyManagement>` 中管理。子模块声明依赖时**不加版本号**（除非与管理的版本不同）。

---

## 故障排除

### 构建报错"找不到 Node.js"

`frontend-maven-plugin` 会自动下载安装 Node.js。如果在代理后面：

```bash
mvn clean package -DskipTests -Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080
```

### webpack 构建报错

清除 Node 模块后重试：

```bash
cd guacamole/frontend
rm -rf node_modules package-lock.json
npm install
npm run build
```

### Maven 编译报错

```bash
# 从头完整重新编译
mvn clean compile

# 详细输出用于调试
mvn clean compile -X
```

### 应用无法连接 guacd

```bash
# 确认 guacd 正在运行
docker ps | grep guacd

# 检查连接
telnet localhost 4822

# 查看应用日志中 guacd 连接详情
```
