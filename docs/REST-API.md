# REST API 参考

Guacamole Spring Boot 完整的 REST API 端点参考。所有端点与原 Apache Guacamole 1.5.5 完全兼容。基于源代码验证。

## 目录

- [认证](#认证)
- [会话管理](#会话管理)
- [连接管理](#连接管理)
- [连接组管理](#连接组管理)
- [用户管理](#用户管理)
- [用户组管理](#用户组管理)
- [共享配置文件](#共享配置文件)
- [活跃连接](#活跃连接)
- [历史记录](#历史记录)
- [隧道管理](#隧道管理)
- [Schema](#schema)
- [扩展与补丁](#扩展与补丁)
- [语言](#语言)
- [通用说明](#通用说明)

---

## 认证

### 登录

创建新的认证 Token。此端点是唯一不需要认证的端点。

```
POST /api/tokens
Content-Type: application/x-www-form-urlencoded
```

**请求参数**（表单）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 否 | 用户名。留空时从 `Authorization: Basic` 头获取 |
| `password` | String | 否 | 密码。留空时从 `Authorization: Basic` 头获取 |
| `token` | String | 否 | 已存在的认证 Token（多因素认证的第二步） |

同时支持 HTTP Basic Auth 头作为用户名/密码的后备来源：

```
Authorization: Basic base64(username:password)
```

**返回**（200 OK）：

```json
{
    "authToken": "a1b2c3d4e5f6...",
    "username": "admin",
    "authProviderIdentifier": "postgresql",
    "availableDataSources": ["postgresql"]
}
```

**错误**（400/401/403）：

```json
{
    "message": "Permission Denied.",
    "type": "INVALID_CREDENTIALS",
    "expected": {"type": "USERNAME_PASSWORD"},
    "translatableMessage": null
}
```

### 注销

```
DELETE /api/tokens/{token}
```

**参数：**
| 参数 | 位置 | 类型 | 说明 |
|------|------|------|------|
| `token` | 路径 | String | 要失效的认证 Token |

**返回：** 204 No Content（成功）。404 Not Found（Token 不存在）。

---

## 会话管理

### 获取当前会话

```
GET /api/session/
```

**查询参数：** `token`（认证 Token）

**认证：** 需要 Token（通过 `Guacamole-Token` 头或 `token` 查询参数）

**返回**：SessionResource，通过子资源定位器提供数据源路由。

### 注销当前会话

```
DELETE /api/session/
```

**认证：** 需要 Token

**返回：** 204 No Content

### 获取用户上下文

```
GET /api/session/data/{dataSource}
```

**参数：**
| 参数 | 位置 | 类型 | 说明 |
|------|------|------|------|
| `dataSource` | 路径 | String | 认证提供者标识符（如 `mysql`、`postgresql`） |

**认证：** 需要 Token

**返回：** 通过子资源定位器提供对连接、用户、组等目录的访问。

### 获取当前用户

```
GET /api/session/data/{dataSource}/self
```

**认证：** 需要 Token

**返回**：

```json
{
    "identifier": "admin",
    "attributes": {},
    "permissions": {"systemPermissions": ["ADMINISTER"]}
}
```

---

## 连接管理

### 连接列表

```
GET /api/session/data/{dataSource}/connections
```

| 参数 | 位置 | 类型 | 说明 |
|------|------|------|------|
| `dataSource` | 路径 | String | 认证提供者标识符 |

**认证：** 需要 Token

**返回**：连接对象映射（`Map<String, APIConnection>`）：

```json
{
    "5": {
        "identifier": "5",
        "name": "生产服务器",
        "protocol": "rdp",
        "parentIdentifier": "ROOT",
        "activeConnections": 0,
        "attributes": {
            "guacd-port": "4822",
            "guacd-hostname": "localhost"
        }
    },
    "8": {
        "identifier": "8",
        "name": "开发环境",
        "protocol": "ssh",
        "parentIdentifier": "ROOT",
        "activeConnections": 1,
        "attributes": {}
    }
}
```

### 获取单个连接

```
GET /api/session/data/{dataSource}/connections/{id}
```

| 参数 | 位置 | 类型 | 说明 |
|------|------|------|------|
| `id` | 路径 | String | 连接标识符 |

**认证：** 需要 Token

### 创建连接

```
POST /api/session/data/{dataSource}/connections
Content-Type: application/json
```

**请求体**：

```json
{
    "name": "新连接",
    "protocol": "rdp",
    "parentIdentifier": "ROOT",
    "attributes": {
        "guacd-hostname": "localhost",
        "guacd-port": "4822",
        "hostname": "192.168.1.100",
        "port": "3389",
        "username": "administrator",
        "password": "secret",
        "security": "any",
        "ignore-cert": "true"
    }
}
```

**认证：** 需要 Token。需要 `CREATE_CONNECTION` 系统权限及目标组的 `UPDATE` 权限。

**返回：** 创建的连接对象（201 Created）。

### 更新连接

```
PUT /api/session/data/{dataSource}/connections/{id}
Content-Type: application/json
```

**认证：** 需要 Token。需要连接的 `UPDATE` 权限。

### 部分更新连接

```
PATCH /api/session/data/{dataSource}/connections/{id}
Content-Type: application/json
```

支持的 JSON Patch 操作：`add`、`replace`、`remove`。

```json
[
    {"op": "add", "path": "/attributes/hostname", "value": "192.168.1.200"},
    {"op": "replace", "path": "/name", "value": "新名称"},
    {"op": "remove", "path": "/attributes/password"}
]
```

**认证：** 需要 Token。需要连接的 `UPDATE` 权限。

### 删除连接

```
DELETE /api/session/data/{dataSource}/connections/{id}
```

**认证：** 需要 Token。需要连接的 `DELETE` 权限。

### 获取连接参数

```
GET /api/session/data/{dataSource}/connections/{id}/parameters
```

返回连接的参数列表（带 Token 替换后的实际值）。需要 `ADMINISTER` 系统权限或连接的 `UPDATE` 权限。

**认证：** 需要 Token。

**返回**：

```json
{
    "hostname": "192.168.1.100",
    "port": "3389",
    "username": "administrator",
    "security": "any",
    "ignore-cert": "true"
}
```

### 连接使用历史

```
GET /api/session/data/{dataSource}/connections/{id}/history
```

**查询参数：**
| 参数 | 类型 | 说明 |
|------|------|------|
| `token` | String | 认证 Token |
| `sort` | String | 排序字段（`startDate`、`endDate`、`username`） |
| `order` | String | 排序方向（`ASC`、`DESC`） |
| `limit` | int | 返回记录数限制 |

**认证：** 需要 Token。

### 连接关联的共享配置

```
GET /api/session/data/{dataSource}/connections/{id}/sharingProfiles
```

返回当前用户可以用于共享此连接的共享配置文件。

**认证：** 需要 Token。

---

## 连接组管理

### 连接组列表

```
GET /api/session/data/{dataSource}/connectionGroups
```

**认证：** 需要 Token。

**返回**：连接组对象映射。

### 获取单个连接组

```
GET /api/session/data/{dataSource}/connectionGroups/{id}
```

**认证：** 需要 Token。

### 创建连接组

```
POST /api/session/data/{dataSource}/connectionGroups
Content-Type: application/json
```

```json
{
    "name": "新分组",
    "parentIdentifier": "ROOT",
    "type": "ORGANIZATIONAL",
    "attributes": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | String | `ORGANIZATIONAL`（组织组）或 `BALANCING`（负载均衡组） |

**认证：** 需要 Token。需要 `CREATE_CONNECTION_GROUP` 系统权限。

### 更新连接组

```
PUT /api/session/data/{dataSource}/connectionGroups/{id}
```

**认证：** 需要 Token。需要连接组的 `UPDATE` 权限。

### 删除连接组

```
DELETE /api/session/data/{dataSource}/connectionGroups/{id}
```

**认证：** 需要 Token。需要连接组的 `DELETE` 权限。

### 连接组树

```
GET /api/session/data/{dataSource}/connectionGroups/{id}/tree
```

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `permission` | String | 否 | 过滤权限级别（如 `READ`） |

**认证：** 需要 Token。

**返回**：该组下所有连接和子组的层级结构，可被指定权限过滤。

---

## 用户管理

### 用户列表

```
GET /api/session/data/{dataSource}/users
```

**认证：** 需要 Token。需要 `ADMINISTER` 权限。

### 获取单个用户

```
GET /api/session/data/{dataSource}/users/{username}
```

**认证：** 需要 Token。

### 创建用户

```
POST /api/session/data/{dataSource}/users
Content-Type: application/json
```

```json
{
    "username": "newuser",
    "password": "userpassword",
    "attributes": {}
}
```

**认证：** 需要 Token。需要 `CREATE_USER` 系统权限。

### 更新用户

```
PUT /api/session/data/{dataSource}/users/{username}
```

**认证：** 需要 Token。需要用户的 `UPDATE` 权限。

### 删除用户

```
DELETE /api/session/data/{dataSource}/users/{username}
```

**认证：** 需要 Token。需要用户的 `DELETE` 权限。

### 修改密码

```
PUT /api/session/data/{dataSource}/users/{username}/password
Content-Type: application/json
```

```json
{
    "oldPassword": "currentpassword",
    "newPassword": "newpassword"
}
```

**认证：** 需要 Token。需要知道旧密码或拥有 `ADMINISTER` 权限。

### 获取用户权限

```
GET /api/session/data/{dataSource}/users/{username}/permissions
```

**认证：** 需要 Token。需要 `ADMINISTER` 权限。

**返回**：

```json
{
    "connectionPermissions": {"5": "READ", "8": "READ"},
    "connectionGroupPermissions": {"ROOT": "ADMINISTER"},
    "userPermissions": {},
    "userGroupPermissions": {},
    "systemPermissions": ["CREATE_CONNECTION", "CREATE_USER"]
}
```

### 设置用户权限

```
PUT /api/session/data/{dataSource}/users/{username}/permissions
```

全量替换用户权限。

### 更新用户权限

```
PATCH /api/session/data/{dataSource}/users/{username}/permissions
Content-Type: application/json
```

```json
[
    {"op": "add", "path": "/connectionPermissions/5", "value": "READ"},
    {"op": "add", "path": "/systemPermissions", "value": "ADMINISTER"},
    {"op": "remove", "path": "/connectionPermissions/3"}
]
```

### 获取有效权限

```
GET /api/session/data/{dataSource}/users/{username}/effectivePermissions
```

返回用户从自身权限和所属用户组继承的全部有效权限的并集。

**认证：** 需要 Token。需要 `ADMINISTER` 权限。

### 获取用户所属组

```
GET /api/session/data/{dataSource}/users/{username}/userGroups
```

**认证：** 需要 Token。需要 `ADMINISTER` 权限。

### 更新用户所属组

```
PUT /api/session/data/{dataSource}/users/{username}/userGroups
PATCH /api/session/data/{dataSource}/users/{username}/userGroups
```

---

## 用户组管理

### 用户组列表

```
GET /api/session/data/{dataSource}/userGroups
```

**认证：** 需要 Token。

### 获取单个用户组

```
GET /api/session/data/{dataSource}/userGroups/{id}
```

**认证：** 需要 Token。

### 创建用户组

```
POST /api/session/data/{dataSource}/userGroups
Content-Type: application/json
```

```json
{
    "identifier": "admins",
    "attributes": {}
}
```

**认证：** 需要 Token。需要 `CREATE_USER_GROUP` 系统权限。

### 更新用户组

```
PUT /api/session/data/{dataSource}/userGroups/{id}
```

**认证：** 需要 Token。需要用户组的 `UPDATE` 权限。

### 删除用户组

```
DELETE /api/session/data/{dataSource}/userGroups/{id}
```

**认证：** 需要 Token。需要用户组的 `DELETE` 权限。

### 用户组权限

```
GET /api/session/data/{dataSource}/userGroups/{id}/permissions
PUT /api/session/data/{dataSource}/userGroups/{id}/permissions
PATCH /api/session/data/{dataSource}/userGroups/{id}/permissions
```

### 用户组成员管理

```
GET /api/session/data/{dataSource}/userGroups/{id}/memberUsers
PUT /api/session/data/{dataSource}/userGroups/{id}/memberUsers
PATCH /api/session/data/{dataSource}/userGroups/{id}/memberUsers

GET /api/session/data/{dataSource}/userGroups/{id}/memberUserGroups
PUT /api/session/data/{dataSource}/userGroups/{id}/memberUserGroups
PATCH /api/session/data/{dataSource}/userGroups/{id}/memberUserGroups
```

---

## 共享配置文件

### 共享配置文件列表

```
GET /api/session/data/{dataSource}/sharingProfiles
```

**认证：** 需要 Token。

### 获取单个共享配置文件

```
GET /api/session/data/{dataSource}/sharingProfiles/{id}
```

**认证：** 需要 Token。

### 创建共享配置文件

```
POST /api/session/data/{dataSource}/sharingProfiles
Content-Type: application/json
```

```json
{
    "name": "分享会话",
    "primaryConnectionIdentifier": "5",
    "attributes": {}
}
```

**认证：** 需要 Token。需要 `CREATE_SHARING_PROFILE` 系统权限。

### 更新共享配置文件

```
PUT /api/session/data/{dataSource}/sharingProfiles/{id}
```

**认证：** 需要 Token。需要共享配置文件的 `UPDATE` 权限。

### 删除共享配置文件

```
DELETE /api/session/data/{dataSource}/sharingProfiles/{id}
```

**认证：** 需要 Token。需要共享配置文件的 `DELETE` 权限。

---

## 活跃连接

### 活跃连接列表

```
GET /api/session/data/{dataSource}/activeConnections
```

返回所有当前活跃的远程桌面连接。

**认证：** 需要 Token。

### 获取单个活跃连接

```
GET /api/session/data/{dataSource}/activeConnections/{id}
```

**认证：** 需要 Token。

### 管理活跃连接

```
PATCH /api/session/data/{dataSource}/activeConnections/{id}
Content-Type: application/json
```

```json
[
    {"op": "add", "path": "/sharingProfiles/3", "value": "READ"}
]
```

### 活跃连接的连接详情

```
GET /api/session/data/{dataSource}/activeConnections/{id}/connection
GET /api/session/data/{dataSource}/activeConnections/{id}/connection/sharingProfiles
```

**认证：** 需要 Token。

### 获取活跃连接凭证

```
GET /api/session/data/{dataSource}/activeConnections/{id}/credentials
```

返回活跃连接的用户凭证信息。

**认证：** 需要 Token。需要连接的 `ADMINISTER` 权限。

---

## 历史记录

### 用户连接历史

```
GET /api/session/data/{dataSource}/history
```

返回当前用户的连接历史记录。

**认证：** 需要 Token。

### 所有用户历史

```
GET /api/session/data/{dataSource}/history/users
```

**认证：** 需要 Token。需要 `ADMINISTER` 权限。

**查询参数：**
| 参数 | 类型 | 说明 |
|------|------|------|
| `sort` | String | 排序字段（`startDate`、`endDate`、`username`、`hostname`） |
| `order` | String | 排序方向（`ASC`、`DESC`） |
| `limit` | int | 返回记录数限制 |
| `identifier` | String | 按用户/连接标识符过滤 |

### 连接历史

```
GET /api/session/data/{dataSource}/history/connections
```

### 活跃会话日志

```
GET /api/session/data/{dataSource}/history/active
```

返回当前活跃的会话列表及其持续时间。

### 历史记录日志下载

```
GET /api/session/data/{dataSource}/history/session/{id}/logs/{log}
GET /api/session/data/{dataSource}/history/connection/{id}/logs/{log}
```

下载历史会话或连接的关联日志文件。

---

## 隧道管理

### 活跃隧道列表

```
GET /api/session/tunnels
```

返回所有当前活跃的 Guacamole 隧道 UUID 集合。

**认证：** 需要 Token。

**返回**：

```json
["uuid-1", "uuid-2"]
```

### 创建隧道

```
POST /api/session/tunnels
Content-Type: application/json
```

**请求体**：

```json
{
    "connectionIdentifier": "5",
    "authProviderIdentifier": "postgresql",
    "width": 1024,
    "height": 768,
    "dpi": 96,
    "audioMimetypes": ["audio/wav", "audio/ogg"],
    "videoMimetypes": ["video/mp4"],
    "imageMimetypes": ["image/png", "image/jpeg"],
    "timezone": "Asia/Shanghai"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `connectionIdentifier` | String | 是 | 连接标识符 |
| `authProviderIdentifier` | String | 否 | 认证提供者标识符 |
| `width` | int | 否 | 最佳屏幕宽度 |
| `height` | int | 否 | 最佳屏幕高度 |
| `dpi` | int | 否 | 最佳分辨率（DPI） |
| `timezone` | String | 否 | 客户端时区（如 `Asia/Shanghai`） |
| `audioMimetypes` | String[] | 否 | 支持的音频 MIME 类型 |
| `videoMimetypes` | String[] | 否 | 支持的视频 MIME 类型 |
| `imageMimetypes` | String[] | 否 | 支持的图片 MIME 类型 |

**认证：** 需要 Token。

**返回**：包含隧道 UUID 的 JSON 对象。

### 隧道状态

```
GET /api/session/tunnels/{uuid}
```

**认证：** 需要 Token。

**返回**：隧道当前状态信息。

### 隧道协议

```
GET /api/session/tunnels/{uuid}/protocol
```

返回隧道使用的协议类型及其参数信息。

**认证：** 需要 Token。

**返回**：

```json
{
    "name": "rdp",
    "parameters": {
        "hostname": {"type": "TEXT", "name": "hostname"},
        "port": {"type": "NUMERIC", "name": "port"},
        "username": {"type": "TEXT", "name": "username"}
    }
}
```

### 隧道的活跃连接

```
GET /api/session/tunnels/{uuid}/activeConnection
GET /api/session/tunnels/{uuid}/activeConnection/connection
GET /api/session/tunnels/{uuid}/activeConnection/connection/sharingProfiles
```

**认证：** 需要 Token。

### 流数据传输

**下载流数据：**

```
GET /api/session/tunnels/{uuid}/streams/{index}/{filename}
```

| 参数 | 位置 | 类型 | 说明 |
|------|------|------|------|
| `index` | 路径 | int | 流索引 |
| `filename` | 路径 | String | 文件名 |
| `type` | 查询 | String | MIME 类型（默认 `application/octet-stream`） |

**上传流数据：**

```
POST /api/session/tunnels/{uuid}/streams/{index}/{filename}
Content-Type: application/octet-stream
```

**认证：** 需要 Token。

---

## Schema

### 获取 Schema

```
GET /api/session/data/{dataSource}/schema
```

返回当前认证提供者的属性 Schema 概览。

### 用户属性定义

```
GET /api/session/data/{dataSource}/schema/userAttributes
```

返回描述用户对象可能属性的表单集合。

### 用户组属性定义

```
GET /api/session/data/{dataSource}/schema/userGroupAttributes
```

### 连接属性定义

```
GET /api/session/data/{dataSource}/schema/connectionAttributes
```

### 连接组属性定义

```
GET /api/session/data/{dataSource}/schema/connectionGroupAttributes
```

### 共享配置文件属性定义

```
GET /api/session/data/{dataSource}/schema/sharingProfileAttributes
```

### 协议信息

```
GET /api/session/data/{dataSource}/schema/protocols
```

返回系统定义的所有协议名称到协议信息的映射。每个协议包含其可用参数的定义。

**认证：** 需要 Token。

---

## 扩展与补丁

### HTML 补丁

```
GET /api/patches
```

返回所有已启用扩展的 HTML 片段列表，这些片段将被注入到前端 DOM 中。

**认证：** 不需要 Token（在 AngularJS 应用启动前加载）。

**返回**：

```json
[
    "<meta name='guacamole-extension' content='totp'>...",
    "<meta name='guacamole-extension' content='duo'>..."
]
```

### 扩展 REST 端点

```
GET /api/ext/{identifier}/
```

各扩展自行注册的子资源端点，由 `AuthenticationProvider.getResource()` 提供：

| 端点 | SSO 类型 | 用途 |
|------|----------|------|
| `/api/ext/cas/` | CAS SSO | CAS 认证重定向和回调 |
| `/api/ext/openid/` | OpenID Connect | OpenID 认证重定向和回调 |
| `/api/ext/saml/` | SAML 2.0 | SAML 认证重定向和回调 |
| `/api/session/ext/{dataSource}/` | QuickConnect | QuickConnect 接口 |

这些端点由 Jersey 自动扫描的 `org.apache.guacamole.auth.sso` 包提供。

---

## 语言

### 语言列表

```
GET /api/languages
```

返回已安装的语言包列表。

**返回**：

```json
{
    "en": {"name": "English"},
    "zh": {"name": "Chinese"},
    "de": {"name": "Deutsch"},
    ...
}
```

### 翻译文件

```
GET /translations/{lang}.json
```

返回指定语言的翻译 JSON 文件。

**支持的语言：** `ca`（加泰罗尼亚语）、`cs`（捷克语）、`de`（德语）、`en`（英语）、`es`（西班牙语）、`fr`（法语）、`it`（意大利语）、`ja`（日语）、`ko`（韩语）、`nl`（荷兰语）、`no`（挪威语）、`pt`（葡萄牙语）、`ru`（俄语）、`zh`（中文）。

**返回**：语言的翻译键值对 JSON 对象。

---

## 通用说明

### 认证传递方式

所有需要认证的端点支持以下 Token 传递方式（优先级从高到低）：

1. **HTTP 头：** `Guacamole-Token: a1b2c3d4...`
2. **查询参数：** `GET /api/...?token=a1b2c3d4...`

```
# 头传递方式（推荐）
GET /api/session/data/postgresql/connections
Guacamole-Token: a1b2c3d4...

# 查询参数方式
GET /api/session/data/postgresql/connections?token=a1b2c3d4...
```

### Content-Type

| 端点类型 | Content-Type |
|----------|-------------|
| JSON API（大多数端点） | `application/json` |
| 流数据传输 | `application/octet-stream` |
| 登录端点 | `application/x-www-form-urlencoded` |

### 响应格式

**成功响应：** HTTP 2xx，JSON 响应体。

**客户端错误（4xx）**：
```json
{
    "message": "Permission Denied.",
    "type": "PERMISSION_DENIED",
    "expected": null,
    "translatableMessage": null
}
```

**服务端错误（5xx）**：
```json
{
    "message": "Internal server error.",
    "type": "INTERNAL_ERROR",
    "expected": null,
    "translatableMessage": null
}
```

### 权限模型

| 对象权限级别 | 说明 |
|-------------|------|
| `READ` | 可查看和使用连接 |
| `UPDATE` | 可修改连接配置 |
| `DELETE` | 可删除连接 |
| `ADMINISTER` | 完全控制（含权限管理，继承所有下级权限） |

| 系统权限 | 说明 |
|----------|------|
| `CREATE_CONNECTION` | 创建连接 |
| `CREATE_CONNECTION_GROUP` | 创建连接组 |
| `CREATE_SHARING_PROFILE` | 创建共享配置文件 |
| `CREATE_USER` | 创建用户 |
| `CREATE_USER_GROUP` | 创建用户组 |
| `ADMINISTER` | 完全系统管理权限 |

### 错误类型

| 错误类型 | HTTP 状态码 | 说明 |
|----------|-------------|------|
| `BAD_REQUEST` | 400 | 请求参数无效 |
| `UNAUTHORIZED` | 401 | 缺少认证 Token |
| `INVALID_CREDENTIALS` | 403 | 凭证无效 |
| `PERMISSION_DENIED` | 403 | 权限不足 |
| `RESOURCE_NOT_FOUND` | 404 | 资源不存在 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |
| `SERVER_BUSY` | 503 | 服务器繁忙 |

### 数据源标识符

`{dataSource}` 路径参数的值取决于启用的认证提供者：

| 标识符 | 认证提供者 |
|--------|-----------|
| `mysql` | MySQL JDBC |
| `postgresql` | PostgreSQL JDBC |
| `sqlserver` | SQL Server JDBC |
| `ldap` | LDAP |
| `json` | JSON 加密认证 |
| `header` | HTTP 头认证 |
| `file` | 文件认证（回退方案） |

多个认证提供者同时启用时，各提供者分别暴露一个独立的数据源。
