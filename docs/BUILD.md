# 构建与部署指南

完整的构建、运行和部署 Guacamole Spring Boot 应用的指南。

## 目录

- [环境要求](#环境要求)
- [源码构建](#源码构建)
- [构建阶段详解](#构建阶段详解)
- [构建标识符详解](#构建标识符详解)
- [前端开发](#前端开发)
- [运行应用](#运行应用)
- [Docker 部署](#docker-部署)
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
| npm | 9.8.1 | 前端依赖管理（由 `frontend-maven-plugin` 自动安装） |
| guacd | 1.5.5 | Guacamole 代理守护进程（处理远程桌面连接） |
| PostgreSQL | 14+ | 数据库（可选；MySQL 8+ 和 SQL Server 2019+ 也支持） |

JDK 17 是强制要求。Node.js 和 npm 不需要手动安装——`frontend-maven-plugin` 1.12.1 会在构建过程中自动下载指定版本。

---

## 源码构建

### 全量构建

```bash
cd guacamole-spring-boot
mvn clean package -DskipTests
```

构建产物位于 `guacamole/target/guacamole-*.jar`。

### 按模块构建

```bash
# 仅构建 Web 应用及其依赖
mvn clean package -pl guacamole -am -DskipTests

# 构建所有扩展模块
mvn clean package -pl extensions -am -DskipTests

# 构建单个扩展（如 LDAP starter）
mvn clean package -pl extensions/guacamole-auth-ldap-starter -am -DskipTests
```

`-am`（also-make）确保构建指定模块所需的所有依赖也会被构建。

### 构建并运行测试

```bash
mvn clean package
```

测试主要在 `guacamole` 模块中。扩展模块作为 Spring Boot Starter，依赖注入测试通过 Spring Boot Test 运行。

### 快速启动（开发模式）

跳过测试和前端构建，快速获取可运行 jar：

```bash
mvn clean package -DskipTests -Dskip.frontend
```

### 代理设置

若在代理后构建，Maven 需要代理配置以自动下载 Node.js：

```bash
mvn clean package -DskipTests -Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080
```

### 跳过前端构建

```bash
# 跳过整个前端（webpack 也不会运行）
mvn clean package -DskipTests -Dskip.frontend

# 强制重新安装 npm 依赖（package.json 变更后）
mvn clean package -DskipTests -Dforce.npm.install
```

---

## 构建阶段详解

完整的 Maven 构建分为以下阶段：

### 阶段 1：generate-resources（前端构建）

**目标：** 生成前端静态资源。

1. `frontend-maven-plugin` 执行 `install-node-and-npm`——自动下载 Node.js 18.18.0 和 npm 9.8.1 到 `target/node/`
2. `frontend-maven-plugin` 执行 `npm install`——安装 `guacamole/frontend/package.json` 中的依赖
3. `frontend-maven-plugin` 执行 `npm run build`——运行 webpack 构建

**自动跳过机制：** 若 `frontend/node_modules` 目录已存在且 Maven 属性 `force.npm.install` 未设置，`skip-npm-install` profile 自动激活，跳过 `npm install`（最慢的步骤）。webpack 构建始终运行。

### 阶段 2：process-resources（资源处理）

**目标：** 过滤和复制资源文件。

1. **Maven 资源过滤**（`maven-resources-plugin`）——仅在特定文件中替换 `${guacamole.build.identifier}` 为时间戳
2. **复制 guacamole-common-js**——将 `guacamole-common-js` 的 JS 文件复制到 `static/guacamole-common-js/`
3. **复制核心翻译文件**——将 `frontend/src/translations/*.json` 复制到 `target/classes/translations/`
4. **flatten-maven-plugin**——解析 `${revision}` 版本变量到被发布的 POM 中

### 阶段 3：compile（Java 编译）

**目标：** 编译所有 Java 源码。

- JDK 17 编译，启用 `-Xlint:all` 编译参数
- 所有模块并行编译（Maven 默认行为）

### 阶段 4：prepare-package（扩展资源压缩）

**目标：** 压缩扩展模块的 JS 和 CSS 文件。

- 仅对扩展模块执行
- 使用 `minify-maven-plugin`（Google Closure Compiler）
- 输出 `.min.js` 和 `.min.css` 文件到 `target/classes/`

**注意：** 主应用 JS 已在阶段 1 的 webpack 构建中由 TerserPlugin 压缩，此阶段不再重复压缩。

### 阶段 5：package（打包）

**目标：** 生成可执行的 Fat JAR。

- `spring-boot-maven-plugin` 执行 `repackage` goal
- 将所有依赖（包括扩展）打包到单个 JAR 中
- 设置 `org.apache.guacamole.GuacamoleSpringBootApplication` 为主类
- 排除 `lombok` 依赖（仅编译时需要）

---

## 构建标识符详解

### 时间戳注入

构建标识符是一个时间戳，通过 Maven 资源过滤注入到前端文件中：

```xml
<maven.build.timestamp.format>yyyyMMddHHmmss</maven.build.timestamp.format>
<guacamole.build.identifier>${maven.build.timestamp}</guacamole.build.identifier>
```

例如，2026 年 5 月 31 日 14:30:00 构建会生成标识符 `20260531143000`。

这实现了自动浏览器缓存失效：每次构建产生唯一的标识符。`verifyCachedVersion.js` 在运行时比较此标识符，若检测到新版本部署，强制页面完全重新加载。

### 选择性过滤（Selective Filtering）

资源过滤必须是有选择性的，防止破坏二进制文件（如图片、字体）：

| 文件 | 过滤 | 用途 |
|------|------|------|
| `static/index.html` | 开启 | `${guacamole.build.identifier}` 替换 |
| `static/verifyCachedVersion.js` | 开启 | `${guacamole.build.identifier}` 替换 |
| `**/application*.yml` | 开启 | Spring `@...@` 占位符 |
| `**/application*.yaml` | 开启 | Spring `@...@` 占位符 |
| `**/application*.properties` | 开启 | Spring `@...@` 占位符 |
| 所有其他资源（JSON、JS、CSS、图片、字体） | 关闭 | 原样通过，不受影响 |

实现方式：`guacamole/pom.xml` 中使用两组 `<resource>` 声明，第一组启用过滤并包含目标文件，第二组禁用过滤并排除这些文件。

### 分隔符配置（useDefaultDelimiters）

根 POM 通过启用 `useDefaultDelimiters` 覆盖 Spring Boot 的默认 `@` 分隔符：

```xml
<plugin>
    <artifactId>maven-resources-plugin</artifactId>
    <configuration>
        <useDefaultDelimiters>true</useDefaultDelimiters>
    </configuration>
</plugin>
```

这允许 `${guacamole.build.identifier}`（原生 Maven 语法）和 `@...@`（Spring Boot 占位符，用于 `application.yml`）在同一个项目中**共存**。如果不设置此项，Maven 仅识别 Spring Boot 父 POM 覆盖的 `@...@` 语法。

---

## 前端开发

### 技术栈

- **框架：** AngularJS 1.8
- **构建工具：** webpack 4
- **JS 压缩：** TerserPlugin（主应用）/ Closure Compiler（扩展）
- **CSS 压缩：** CssMinimizerPlugin

### 本地前端开发

```bash
cd guacamole/frontend

# 安装依赖
npm install

# 生产构建（输出到 ../src/main/resources/static/）
npm run build

# 开发模式（监听文件变化，自动重新构建）
npm run dev
```

### 前端构建流水线（11 步）

1. **webpack** 打包 `frontend/src/app/` 的 JS/CSS 为 `guacamole.[contenthash].js` 和 `guacamole.[contenthash].css`
2. **AngularTemplateCacheWebpackPlugin** 将 HTML 模板打包到 `templates.js`
3. **TerserPlugin** 压缩 JavaScript（ES5 兼容输出，排除 `templates.js`，启用并行执行）
4. **CssMinimizerPlugin** 压缩 CSS
5. **CopyPlugin** 复制静态资源（应用文件、字体、图片、布局、`verifyCachedVersion.js`）
6. **CopyPlugin** 从 `node_modules/` 复制核心库（Angular、jQuery、Lodash、Blob polyfill、datalist polyfill）
7. **CleanWebpackPlugin** 清理 `static/` 输出目录（保留 `guacamole-common-js/`）
8. **HtmlWebpackPlugin** 生成 `index.html`，自动添加内容哈希 URL
9. **MiniCssExtractPlugin** 将 CSS 提取到独立文件
10. **DependencyListPlugin**（自定义）列出打包的 node 模块，用于许可证生成
11. **NormalizeTemplatePaths**（自定义内联插件）将 `$templateCache.put()` 键中的反斜杠路径规范化为正斜杠（跨平台兼容）

### Maven 集成

`frontend-maven-plugin` 自动执行整个过程——无需手动安装 Node.js。

**npm install 行为：** Maven 插件运行 `npm install`（而非 `npm ci`）。名为 `skip-npm-install` 的 profile 在以下条件同时满足时自动激活：
- `frontend/node_modules` 目录已存在
- `force.npm.install` 属性未设置

此 profile 将 `npm.install.skip` 设为 `true`，跳过 `npm install` 步骤，同时 webpack 构建每次都运行。

### 双压缩器机制

由于主应用和扩展使用不同的构建系统，它们的 JavaScript 压缩方式也不同：

| 组件 | 压缩工具 | 引擎 | 阶段 |
|------|----------|------|------|
| **主应用**（webapp） | TerserPlugin（`terser-webpack-plugin`） | Terser (ES6+) | webpack 构建时（`generate-resources`） |
| **扩展模块** | `minify-maven-plugin` | Google Closure Compiler | Maven 打包时（`prepare-package`） |

`minify-maven-plugin` 的配置集中定义在根 POM 的 `<pluginManagement>` 中，独立使用于各扩展模块的 pom.xml。源文件在 `src/main/resources/` 下，压缩后输出到 `target/classes/`，manifest 应引用 `.min.*` 文件。

---

## 运行应用

### 直接运行 Fat JAR

```bash
java -jar guacamole/target/guacamole-*.jar
```

### Maven Spring Boot 插件

```bash
mvn spring-boot:run -pl guacamole
```

此方式在开发时无需预先构建 JAR。修改 Java 源文件后自动重新编译（增量）。

### 使用自定义配置文件

```bash
java -jar guacamole/target/guacamole-*.jar --spring.config.location=/path/to/application.yml
```

### 命令行属性覆盖

```bash
java -jar guacamole/target/guacamole-*.jar \
  --server.port=9090 \
  --spring.datasource.password=secret \
  --guacamole.guacd.hostname=guacd.example.com \
  --guacamole.guacd.port=4822
```

### 设置时区

```bash
java -Duser.timezone=Asia/Shanghai -jar guacamole/target/guacamole-*.jar
```

应用程序启动时读取 `user.timezone` 系统属性并调用 `TimeZone.setDefault()`。如果未设置，保持 JVM 默认（通常是系统时区）。

### 启用调试日志

```bash
java -jar guacamole/target/guacamole-*.jar --logging.level.org.apache.guacamole=DEBUG
```

---

## Docker 部署

### 快速启动

项目根目录包含 `Dockerfile` 和 `docker-compose.yml`，一键启动完整环境：

```bash
docker-compose up -d
```

此命令启动三个服务：
- **guacd**（`guacamole/guacd:1.5.5`）——端口 4822
- **PostgreSQL**（`postgres:16-alpine`）——内部访问，自动健康检查
- **Guacamole**（从本地 Dockerfile 构建）——端口 8080

### Environment 变量

```bash
# 设置自定义数据库密码
GUACAMOLE_DB_PASSWORD=securepass123 docker-compose up -d

# 自定义时区
JAVA_TOOL_OPTIONS="-Duser.timezone=America/New_York" docker-compose up -d

# 自定义数据库连接
SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db.example.com:5432/guacamole \
  docker-compose up -d
```

### 构建 Docker 镜像

```bash
docker build -t guacamole-spring-boot:latest .
```

### Docker 多阶段构建

Dockerfile 使用多阶段构建优化镜像大小：

**阶段 1（`build`）：**
- 基础镜像：`maven:3.9-eclipse-temurin-17-alpine`
- 先复制所有 `pom.xml` 文件，执行 `mvn dependency:go-offline` 预取依赖（利用 Docker 缓存层）
- 然后复制全部源码，执行完整构建

**阶段 2（`runtime`）：**
- 基础镜像：`eclipse-temurin:17-jre-alpine`（仅 JRE，体积更小）
- 从构建阶段复制构建产物 `*.jar`
- 创建非 root 用户 `guacamole` 提升安全性
- 暴露 8080 端口
- 入口点：`java -jar app.jar`

### 生产级 Docker Compose

```yaml
version: '3.8'
services:
  guacd:
    image: guacamole/guacd:1.5.5
    restart: unless-stopped
    ports:
      - "127.0.0.1:4822:4822"    # 仅绑定到回环地址（安全）

  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: guacamole
      POSTGRES_USER: guacamole
      POSTGRES_PASSWORD: ${GUACAMOLE_DB_PASSWORD:-guacamole}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U guacamole"]
      interval: 5s
      timeout: 5s
      retries: 5

  guacamole:
    build: .
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      GUACAMOLE_DB_PASSWORD: ${GUACAMOLE_DB_PASSWORD:-guacamole}
      GUACAMOLE_HISTORY_RECORDING_PATH: ${GUACAMOLE_HISTORY_RECORDING_PATH:-/tmp/guacamole/recordings}
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:-jdbc:postgresql://postgres:5432/guacamole}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME:-guacamole}
      JAVA_TOOL_OPTIONS: ${JAVA_TIMEZONE:--Duser.timezone=Asia/Shanghai}
    depends_on:
      guacd:
        condition: service_started
      postgres:
        condition: service_healthy

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

# 初始化 Schema
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
sqlcmd -S localhost -U sa -P 'YourPassword' -Q "CREATE DATABASE guacamole;"
sqlcmd -S localhost -U sa -P 'YourPassword' -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/001-create-schema.sql
sqlcmd -S localhost -U sa -P 'YourPassword' -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/002-create-admin-user.sql
```

### 版本升级

每个数据库模块包含升级脚本，位于 `schema/upgrade/` 目录：

```bash
ls extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/upgrade/
# upgrade-pre-0.9.7.sql, upgrade-pre-0.9.8.sql, ..., upgrade-pre-1.5.5.sql
```

仅执行比当前 Schema 版本更新的脚本。按文件名顺序执行。

---

## Maven 构建系统

### 模块层次树

```
guacamole-client-spring-boot (pom)  ── 根 POM，版本 ${revision}=1.5.5
│
├── guacamole-common                 ── 协议库（JAR，约 51 个 Java 文件）
├── guacamole-common-js              ── JavaScript API（JAR）
├── guacamole-ext                    ── 扩展 API（JAR，约 136 个 Java 文件）
│
├── guacamole                        ── Web 应用（Spring Boot Fat JAR）
│   └── frontend/                    ── 前端源码（AngularJS 1.8 + webpack 4）
│
└── extensions/                      ── 扩展模块目录
    ├── guacamole-auth-jdbc (pom)
    │   ├── guacamole-auth-jdbc-base         ── JDBC 共享代码
    │   ├── guacamole-auth-mysql-starter     ── MySQL Starter
    │   ├── guacamole-auth-postgresql-starter─ PostgreSQL Starter
    │   └── guacamole-auth-sqlserver-starter ── SQL Server Starter
    ├── guacamole-auth-header-starter        ── HTTP 头认证
    ├── guacamole-auth-json-starter          ── JSON 加密认证
    ├── guacamole-auth-ldap-starter          ── LDAP 认证
    ├── guacamole-auth-radius-starter        ── RADIUS 认证
    ├── guacamole-auth-totp-starter          ── TOTP 双因素认证
    ├── guacamole-auth-duo-starter           ── Duo 双因素认证
    ├── guacamole-auth-quickconnect-starter  ── QuickConnect
    ├── guacamole-auth-sso (pom)
    │   ├── guacamole-auth-sso-base          ── SSO 共享代码
    │   ├── guacamole-auth-sso-cas-starter   ── CAS SSO
    │   ├── guacamole-auth-sso-openid-starter─ OpenID Connect SSO
    │   └── guacamole-auth-sso-saml-starter  ── SAML 2.0 SSO
    ├── guacamole-vault (pom)
    │   ├── guacamole-vault-base             ── Vault 共享代码
    │   └── guacamole-vault-ksm-starter      ── Keeper Secrets Manager Vault
    └── guacamole-history-starter            ── 会话录制存储
```

### 关键 Maven 插件

| 插件 | 阶段 | 用途 |
|------|------|------|
| `spring-boot-maven-plugin` | `package` | Fat JAR 重新打包，`repackage` goal，设置主类 |
| `frontend-maven-plugin` 1.12.1 | `generate-resources` | 自动安装 Node.js/npm，运行 `npm install` + webpack 构建 |
| `minify-maven-plugin` 2.0.1 | `prepare-package` | 扩展 JS/CSS 压缩（Google Closure Compiler） |
| `maven-resources-plugin` | `process-resources` | 资源过滤（`useDefaultDelimiters=true`），资源复制 |
| `maven-compiler-plugin` | `compile` | JDK 17 编译，启用 `-Xlint:all` |
| `flatten-maven-plugin` 1.6.0 | `process-resources` | 解析 `${revision}` 到发布的 POM 中 |

### 资源过滤配置

根 POM 在 `<pluginManagement>` 中设置 `useDefaultDelimiters=true`，所有模块继承。`guacamole/pom.xml` 使用两组 `<resource>` 配置选择性过滤：

```xml
<resources>
    <!-- 过滤开启的文件集 -->
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
        <includes>
            <include>static/index.html</include>
            <include>static/verifyCachedVersion.js</include>
            <include>**/application*.yml</include>
            <include>**/application*.yaml</include>
            <include>**/application*.properties</include>
        </includes>
    </resource>
    <!-- 过滤关闭的文件集 -->
    <resource>
        <directory>src/main/resources</directory>
        <filtering>false</filtering>
        <excludes>
            <exclude>static/index.html</exclude>
            <exclude>static/verifyCachedVersion.js</exclude>
            <exclude>**/application*.yml</exclude>
            <exclude>**/application*.yaml</exclude>
            <exclude>**/application*.properties</exclude>
        </excludes>
    </resource>
</resources>
```

### 依赖管理

所有依赖版本集中在根 POM 的 `<dependencyManagement>` 中。子模块声明依赖时**不指定版本**（除非与管理的版本不同）。

关键受管依赖：

| 依赖 | 版本 | 用途 |
|------|------|------|
| Guava | 32.1.3-jre | 通用工具库 |
| Jackson | 2.17.2 | JSON 序列化 |
| MyBatis Spring Boot | 3.0.3 | 数据库映射 |
| Apache Directory LDAP API | 2.1.6 | LDAP 认证 |
| JRadius | 1.1.5 | RADIUS 认证 |
| CAS Client | 3.6.4 | CAS SSO |
| jose4j | 0.9.6 | JOSE 实现（JWT） |
| OneLogin java-saml | 2.9.0 | SAML SSO |
| Keeper KSM | 16.6.3 | Keeper 密钥管理 |
| ZXing | 3.5.3 | 二维码生成（TOTP） |

### 版本管理

使用 Maven CI-Friendly 版本管理。根 POM 定义：

```xml
<properties>
    <revision>1.5.5</revision>
</properties>
```

所有模块继承 `${revision}` 作为版本。`flatten-maven-plugin` 在 `process-resources` 阶段解析此变量，使发布的 POM 中包含实际版本号而非占位符。

---

## 故障排除

### 构建错误："Node.js not found"

`frontend-maven-plugin` 自动下载 Node.js。如果下载失败（代理、网络问题）：

```bash
# 通过代理
mvn clean package -DskipTests -Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080

# 手动安装 Node.js 18.18.0 到 PATH 后重试
```

### Webpack 构建错误

清除 node_modules 并重试：

```bash
cd guacamole/frontend
rm -rf node_modules package-lock.json
npm install
npm run build
```

如果错误涉及 OpenSSL（如 `ERR_OSSL_EVP_UNSUPPORTED`），构建已自动设置 `NODE_OPTIONS=--openssl-legacy-provider`。

### Maven 编译错误

```bash
# 完全清理重建
mvn clean compile

# 详细输出
mvn clean compile -X
```

### 应用无法连接 guacd

```bash
# 检查 guacd 是否运行
docker ps | grep guacd

# 测试连接
telnet localhost 4822

# 检查应用日志中 guacd 连接详情
# 确认 application.yml 中 guacamole.guacd.hostname 和 guacamole.guacd.port 正确
```

### 构建缓慢（每次 npm install）

`skip-npm-install` profile 在 `node_modules` 存在时自动激活。强制重新安装：

```bash
mvn clean package -Dforce.npm.install
```

完全跳过前端（后端开发时）：

```bash
mvn clean package -Dskip.frontend
```
