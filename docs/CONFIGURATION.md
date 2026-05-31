# 配置参考

Guacamole Spring Boot 所有扩展的完整配置参考。

## 目录

- [核心配置](#核心配置)
- [认证扩展](#认证扩展)
  - [Header 认证](#header-认证)
  - [JSON 认证](#json-认证)
  - [JDBC 数据库认证](#jdbc-数据库认证)
  - [LDAP 认证](#ldap-认证)
  - [RADIUS 认证](#radius-认证)
  - [TOTP 双因素认证](#totp-双因素认证)
  - [DUO 双因素认证](#duo-双因素认证)
  - [SSO — CAS](#sso--cas)
  - [SSO — OpenID Connect](#sso--openid-connect)
  - [SSO — SAML 2.0](#sso--saml-20)
- [功能扩展](#功能扩展)
  - [Quick Connect 快速连接](#quick-connect-快速连接)
  - [History 会话录像](#history-会话录像)
  - [Vault 密钥管理](#vault-密钥管理)
- [多认证链](#多认证链)
- [环境变量](#环境变量)

---

## 核心配置

### 服务端口与 guacd

```yaml
server:
  port: 8080

guacamole:
  guacd:
    hostname: localhost     # guacd 守护进程地址
    port: 4822              # guacd 端口（默认 4822）
    ssl: false              # 是否使用 SSL/TLS 连接 guacd
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `guacamole.guacd.hostname` | 是 | `localhost` | guacd 代理守护进程的主机名或 IP |
| `guacamole.guacd.port` | 否 | `4822` | guacd 守护进程端口 |
| `guacamole.guacd.ssl` | 否 | `false` | 启用 SSL/TLS 连接 guacd |

### 时区

通过 JVM 系统属性在启动时设置：

```bash
java -Duser.timezone=Asia/Shanghai -jar guacamole/target/guacamole-*.jar
```

Docker 环境：
```yaml
environment:
  JAVA_TOOL_OPTIONS: "-Duser.timezone=Asia/Shanghai"
```

---

## 认证扩展

### 扩展启用/禁用规则

所有认证扩展遵循统一的启用模式：

```yaml
guacamole:
  auth:
    <扩展名>:
      enabled: true    # true 启用，false 禁用
```

扩展**默认全部禁用**。需要同时满足两个条件才会激活：Maven 依赖存在 **且** `enabled: true`。

---

### Header 认证

反向代理 SSO —— 基于 HTTP 请求头中的用户名进行认证（通常由 Nginx、Apache 或 IdP 代理设置）。

```yaml
guacamole:
  auth:
    header:
      enabled: true
      http-auth-header: REMOTE_USER    # 可选，默认 REMOTE_USER
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `guacamole.auth.header.enabled` | 是 | `false` | 启用 Header 认证 |
| `http-auth-header` | 否 | `REMOTE_USER` | 包含认证用户名的 HTTP 头名称 |

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

加密 Token 认证，无需数据库。所有连接和用户数据加密存储在客户端提交的 JSON 中，服务端进行解密和签名验证。

```yaml
guacamole:
  auth:
    json:
      enabled: true
      json-secret-key: <base64编码的256位密钥>
      json-trusted-networks: 192.168.1.0/24,10.0.0.0/8   # 可选
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `guacamole.auth.json.enabled` | 是 | `false` | 启用 JSON 认证 |
| `json-secret-key` | **是** | — | Base64 编码的 256 位 AES 密钥，用于加密和 HMAC 签名 |
| `json-trusted-networks` | 否 | 允许所有 | 逗号分隔的 CIDR 网络列表，只有这些来源的请求允许认证 |

**生成密钥：**
```bash
openssl rand -base64 32
```

**注意：** 生成 Token 的客户端必须使用相同的密钥。Token 使用 AES-256-CBC 加密，HMAC-SHA256 签名。

---

### JDBC 数据库认证

基于数据库的用户/连接/权限管理。

**同时只能启用一个数据库后端。** 启用多个会导致启动报错。

#### PostgreSQL

```yaml
guacamole:
  auth:
    postgresql:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: ${GUACAMOLE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

#### MySQL

```yaml
guacamole:
  auth:
    mysql:
      enabled: true
      mysql-hostname: localhost          # 默认值：localhost
      mysql-port: 3306                   # 默认值：3306
      mysql-database: guacamole          # 必填
      mysql-username: guacamole
      mysql-password: ${MYSQL_PASSWORD}
      mysql-driver: mariadb              # mysql 或 mariadb（不填则自动检测）
      mysql-ssl-mode: preferred          # disabled | preferred | required | verify-ca | verify-identity
      mysql-auto-create-accounts: false  # 自动为其他认证源的用户在数据库中创建账号
      mysql-user-required: false         # 是否要求所有用户必须在数据库中有账号
      mysql-absolute-max-connections: 0  # 全局最大并发连接数（0 = 无限制）
      mysql-default-max-connections: 0   # 每个连接默认最大并发数
      mysql-default-max-group-connections: 0
      mysql-default-max-connections-per-user: 0
      mysql-default-max-group-connections-per-user: 1
      mysql-batch-size: 1000            # SQL 批处理大小

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/guacamole?useSSL=false&serverTimezone=UTC
    username: guacamole
    password: ${MYSQL_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

#### SQL Server

```yaml
guacamole:
  auth:
    sqlserver:
      enabled: true
      sqlserver-hostname: localhost
      sqlserver-port: 1433
      sqlserver-database: guacamole
      sqlserver-username: guacamole
      sqlserver-password: ${MSSQL_PASSWORD}
      sqlserver-driver: sqlserver2005   # 默认
      sqlserver-auto-create-accounts: false
      sqlserver-user-required: false
      sqlserver-absolute-max-connections: 0
      sqlserver-default-max-connections: 0
      sqlserver-default-max-group-connections: 0
      sqlserver-default-max-connections-per-user: 0
      sqlserver-default-max-group-connections-per-user: 1
      sqlserver-batch-size: 1000

spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=guacamole
    username: guacamole
    password: ${MSSQL_PASSWORD}
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
```

#### JDBC 属性参考

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `<db>-database` | — | 数据库名称（**必填**） |
| `<db>-username` | — | 数据库用户（**必填**，除非用了 `spring.datasource`） |
| `<db>-password` | — | 数据库密码（**必填**，除非用了 `spring.datasource`） |
| `<db>-auto-create-accounts` | `false` | 自动为通过其他扩展认证的用户在数据库中创建账号 |
| `<db>-user-required` | `false` | 若为 `true`，所有用户必须在数据库中有账号 |
| `<db>-absolute-max-connections` | `0` | 全局并发连接限制（0 = 无限制） |
| `<db>-default-max-connections` | `0` | 每个连接的默认并发限制 |
| `<db>-default-max-group-connections` | `0` | 每个组的默认并发限制 |
| `<db>-default-max-connections-per-user` | `0` | 每用户每连接默认限制 |
| `<db>-default-max-group-connections-per-user` | `1` | 每用户每组默认限制 |
| `<db>-batch-size` | `1000` | SQL 批处理操作大小 |

**注意：** 配置了 `spring.datasource.*` 后，JDBC URL/用户名/密码由 Spring Boot 接管。`<db>-hostname` 和 `<db>-port` 等属性仅用于旧版 `JDBCEnvironment.getProperty()` 路径。

---

### LDAP 认证

支持 LDAP 目录和 Microsoft Active Directory 认证。

```yaml
guacamole:
  auth:
    ldap:
      enabled: true

      # 必填项
      ldap-hostname: ldap.example.com
      ldap-user-base-dn: ou=users,dc=example,dc=com
      ldap-username-attribute: uid               # OpenLDAP 用 uid，AD 用 sAMAccountName

      # 可选项
      ldap-port: 389                             # 389（明文）或 636（SSL）
      ldap-encryption-method: none               # none | ssl | starttls
      ldap-search-bind-dn: cn=admin,dc=example,dc=com
      ldap-search-bind-password: adminpass
      ldap-user-search-filter: (&(objectClass=person)(uid={0}))
      ldap-group-base-dn: ou=groups,dc=example,dc=com
      ldap-group-name-attribute: cn
      ldap-group-search-filter: (&(objectClass=groupOfNames)(member={0}))
      ldap-member-attribute: member
      ldap-member-attribute-type: dn            # dn | uid
      ldap-max-search-results: 1000
      ldap-operation-timeout: 30                # 秒
      ldap-network-timeout: 30                  # 秒
      ldap-follow-referrals: false
      ldap-max-referral-hops: 5
      ldap-dereference-aliases: never           # never | always | finding | searching
      ldap-user-attributes: cn,mail,displayName # 逗号分隔的额外属性
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `ldap-hostname` | **是** | — | LDAP 服务器地址 |
| `ldap-user-base-dn` | **是** | — | 用户搜索的基础 DN |
| `ldap-username-attribute` | **是** | — | 用户名属性（如 `uid`、`sAMAccountName`、`cn`） |
| `ldap-port` | 否 | `389` | LDAP 端口（SSL 用 `636`） |
| `ldap-encryption-method` | 否 | `none` | 加密方式：`none`、`ssl` 或 `starttls` |
| `ldap-search-bind-dn` | 否 | — | 搜索前绑定的 DN（不填则匿名搜索） |
| `ldap-search-bind-password` | 否 | — | 搜索绑定 DN 的密码 |
| `ldap-user-search-filter` | 否 | `(objectClass=*)` | 用户搜索 LDAP 过滤器（`{0}` → 用户名） |
| `ldap-group-base-dn` | 否 | — | 组搜索的基础 DN |
| `ldap-group-name-attribute` | 否 | `cn` | 组名称属性 |
| `ldap-group-search-filter` | 否 | — | 组成员搜索过滤器（`{0}` → 用户 DN） |
| `ldap-member-attribute` | 否 | `member` | 组中列出成员的属性 |
| `ldap-member-attribute-type` | 否 | `dn` | 成员属性类型：`dn`（值为用户 DN）或 `uid`（值为用户名） |
| `ldap-user-attributes` | 否 | — | 需要暴露给连接的额外属性（逗号分隔） |

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

---

### RADIUS 认证

```yaml
guacamole:
  auth:
    radius:
      enabled: true

      # 必填项
      radius-hostname: radius.example.com
      radius-shared-secret: ${RADIUS_SECRET}
      radius-auth-protocol: PAP               # PAP | CHAP | MSCHAPv1 | MSCHAPv2 | EAP-TLS | EAP-TTLS

      # 可选项
      radius-auth-port: 1812
      radius-acct-port: 1813
      radius-max-retries: 3
      radius-timeout: 30                      # 秒
      radius-nas-ip: 192.168.1.1              # NAS IP 地址
      radius-trust-all: false                 # 是否信任所有服务器证书
      radius-ca-file: /path/to/ca.pem
      radius-key-file: /path/to/client.pem
      radius-key-password: ${RADIUS_KEY_PASS}
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `radius-hostname` | **是** | — | RADIUS 服务器地址 |
| `radius-shared-secret` | **是** | — | RADIUS 共享密钥 |
| `radius-auth-protocol` | **是** | — | 认证协议 |
| `radius-auth-port` | 否 | `1812` | RADIUS 认证端口 |
| `radius-acct-port` | 否 | `1813` | RADIUS 记账端口 |
| `radius-max-retries` | 否 | `3` | 最大重试次数 |
| `radius-timeout` | 否 | `30` | 超时时间（秒） |

---

### TOTP 双因素认证

基于时间的一次性密码（RFC 6238）。兼容 Google Authenticator、Authy、FreeOTP 等。

```yaml
guacamole:
  auth:
    totp:
      enabled: true
      totp-issuer: Guacamole                  # 在认证器应用中显示的名称
      totp-period: 30                         # 验证码有效时长（秒）
      totp-mode: sha1                         # sha1 | sha256 | sha512
      totp-digits: 6                          # 验证码位数（6 或 8）
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `guacamole.auth.totp.enabled` | 是 | `false` | 启用 TOTP 认证 |
| `totp-issuer` | 否 | `Guacamole` | 认证器应用中显示的发行方名称 |
| `totp-period` | 否 | `30` | TOTP 验证码有效时长（秒） |
| `totp-mode` | 否 | `sha1` | 哈希算法：`sha1`、`sha256` 或 `sha512` |
| `totp-digits` | 否 | `6` | 验证码位数：`6` 或 `8` |

**注意：** TOTP 必须配合另一个认证提供者（如 JDBC 或 LDAP）使用。用户首次登录后需要完成 TOTP 密钥绑定。

---

### DUO 双因素认证

> **⚠️ 重要：** 此扩展使用 Duo Web SDK v2，Duo 已于 **2024 年 3 月停止支持**。生产使用前需要升级到 Web SDK v4。

```yaml
guacamole:
  auth:
    duo:
      enabled: false    # SDK v4 升级完成前不要启用
      duo-api-hostname: api-XXXXXXXX.duosecurity.com
      duo-integration-key: ${DUO_IKEY}
      duo-secret-key: ${DUO_SKEY}
      duo-application-key: ${DUO_AKEY}        # v4 中将改为 client_id/client_secret
```

---

### SSO — CAS

CAS 单点登录认证。

```yaml
guacamole:
  auth:
    sso-cas:
      enabled: true

      # 必填项
      cas-authorization-endpoint: https://cas.example.org/cas
      cas-redirect-uri: http://localhost:8080/

      # 可选项
      cas-clearpass-key: /path/to/private.key  # 解密 ClearPass 密码的私钥
      cas-group-attribute: memberOf             # CAS 属性中表示组成员的属性名
      cas-group-format: plain                   # plain | ldap
      cas-group-ldap-base-dn: ou=groups,dc=example,dc=com
      cas-group-ldap-attribute: cn
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `cas-authorization-endpoint` | **是** | — | CAS 服务器的授权端点 URL |
| `cas-redirect-uri` | **是** | — | CAS 认证完成后的回调 URL（Guacamole 的访问地址） |
| `cas-clearpass-key` | 否 | — | ClearPass 私钥文件路径 |
| `cas-group-attribute` | 否 | — | CAS 返回属性中的组成员属性名 |
| `cas-group-format` | 否 | `plain` | 组名格式：`plain`（纯文本）或 `ldap`（LDAP DN） |
| `cas-group-ldap-base-dn` | 否 | — | LDAP 格式组的基础 DN |
| `cas-group-ldap-attribute` | 否 | `cn` | LDAP 格式组的属性名 |

---

### SSO — OpenID Connect

OpenID Connect 认证（支持 Google、Okta、Keycloak、Auth0、Azure AD 等）。

```yaml
guacamole:
  auth:
    sso-openid:
      enabled: true

      # 必填项
      openid-authorization-endpoint: https://accounts.google.com/o/oauth2/v2/auth
      openid-jwks-endpoint: https://www.googleapis.com/oauth2/v3/certs
      openid-issuer: https://accounts.google.com
      openid-client-id: ${OPENID_CLIENT_ID}
      openid-redirect-uri: http://localhost:8080/

      # 可选项
      openid-scope: openid email profile
      openid-username-claim-type: email        # JWT 中的用户名字段
      openid-groups-claim-type: groups         # JWT 中的组字段
      openid-allowed-clock-skew: 30            # 秒
      openid-max-token-validity: 300           # 分钟
      openid-max-nonce-validity: 10            # 分钟
```

**各平台配置示例：**

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

### SSO — SAML 2.0

SAML 2.0 单点登录，支持元数据 URL 和手动 IdP 配置两种方式。

```yaml
guacamole:
  auth:
    sso-saml:
      enabled: true

      # 必填项
      saml-callback-url: http://localhost:8080/

      # 方式一：使用 IdP 元数据 URL（推荐）
      saml-idp-metadata-url: https://idp.example.com/metadata.xml

      # 方式二：手动配置 IdP 信息
      saml-idp-url: https://idp.example.com/sso
      saml-entity-id: http://localhost:8080/

      # 可选项
      saml-strict: true                       # 生产环境建议保持 true
      saml-debug: false                       # 是否启用 SAML 调试日志
      saml-compress-request: true
      saml-compress-response: true
      saml-group-attribute: groups
      saml-auth-timeout: 5                    # 分钟
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `saml-callback-url` | **是** | — | SAML 回调基础 URL（Guacamole 的访问地址） |
| `saml-idp-metadata-url` | 条件必填 | — | IdP 元数据 XML URL（方式一） |
| `saml-idp-url` | 条件必填 | — | IdP SSO 登录 URL（方式二，无元数据时使用） |
| `saml-entity-id` | 条件必填 | — | SAML 实体 ID（方式二，无元数据时使用） |
| `saml-strict` | 否 | `true` | 是否强制严格安全检查（生产环境建议开启） |
| `saml-debug` | 否 | `false` | 启用 SAML 调试日志 |
| `saml-compress-request` | 否 | `true` | 是否压缩 SAML 认证请求 |
| `saml-compress-response` | 否 | `true` | 是否请求压缩 SAML 响应 |
| `saml-group-attribute` | 否 | `groups` | IdP 返回的组成员属性名 |
| `saml-auth-timeout` | 否 | `5` | SAML 认证超时时间（分钟） |

---

## 功能扩展

### Quick Connect 快速连接

在 Guacamole 界面中通过 URI 快速创建临时连接。

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

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `guacamole.auth.quickconnect.enabled` | 是 | `false` | 启用 Quick Connect |

---

### History 会话录像

存储会话录像和连接历史记录。

```yaml
guacamole:
  history:
    enabled: true
    recording-search-path: ${GUACAMOLE_HISTORY_RECORDING_PATH:/tmp/guacamole/recordings}
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `guacamole.history.enabled` | 是 | `false` | 启用历史录像 |
| `recording-search-path` | 否 | `/var/lib/guacamole/recordings` | 搜索录像文件的文件系统路径 |

**注意：** 会话录像由 guacd 生成并存储。`recording-search-path` 必须指向 guacd 写入录像文件的目录，且 Guacamole Web 应用需要对该目录有读取权限。

**Docker 注意：** 如果使用 Docker，需要挂载共享数据卷：
```yaml
volumes:
  - /host/path/recordings:/tmp/guacamole/recordings
```

---

### Vault 密钥管理

Keeper Secrets Manager 凭据注入集成。

```yaml
guacamole:
  vault:
    ksm:
      enabled: true
      config:
        ksm-config: "keeper://<base64编码的配置>"
        allow-unverified-cert: false
```

详见 **[vault-module.md](vault-module.md)**。

---

## 多认证链

多个认证提供者可以同时启用。Guacamole 按顺序尝试每个提供者：

```
登录请求
  → 提供者 1（如 LDAP）→ 成功 → 用户已登录
  → 提供者 1 失败
  → 提供者 2（如 PostgreSQL）→ 成功 → 用户已登录
  → 提供者 2 失败
  → 提供者 3（如 JSON）→ 成功 → 用户已登录
  → 全部失败 → 登录被拒绝
```

### 互斥规则

**启动时强制校验**（违反会导致应用启动失败）：

| 组别 | 规则 |
|------|------|
| JDBC 后端 | `mysql`、`postgresql`、`sqlserver` 只能启用一个 |
| SSO 提供者 | `sso-cas`、`sso-openid`、`sso-saml` 只能启用一个 |

**允许的组合：**
- JDBC + LDAP + TOTP ✅
- LDAP + JSON + QuickConnect ✅
- PostgreSQL + CAS ✅
- MySQL + PostgreSQL ❌（JDBC 冲突）
- CAS + OpenID ❌（SSO 冲突）

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

此配置下：
1. LDAP 用户先通过 LDAP 认证，然后绑定 TOTP
2. 不在 LDAP 中的用户回退到 PostgreSQL 认证
3. TOTP 对所有用户的主认证后强制要求

---

## 环境变量

应用程序支持通过环境变量覆盖配置，遵循 Spring Boot 约定。

| 环境变量 | 映射属性 | 默认值 |
|---------|---------|--------|
| `GUACAMOLE_DB_PASSWORD` | `spring.datasource.password` | `guacamole` |
| `GUACAMOLE_HISTORY_RECORDING_PATH` | `guacamole.history.recording-search-path` | `/tmp/guacamole/recordings` |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | （来自 `application.yml`） |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | （来自 `application.yml`） |
| `SERVER_PORT` | `server.port` | `8080` |
| `OPENID_CLIENT_ID` | `guacamole.auth.sso-openid.openid-client-id` | — |
| `DUO_IKEY` | DUO 集成密钥 | — |
| `DUO_SKEY` | DUO 密钥 | — |

Spring Boot 的任何属性都可以通过大写 + 下划线的方式用环境变量覆盖（如 `GUACAMOLE_AUTH_LDAP_ENABLED=true`）。

**Docker 环境：**
```bash
GUACAMOLE_DB_PASSWORD=secret123 SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/guacamole docker-compose up -d
```
