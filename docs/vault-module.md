# Vault 模块 — Keeper Secrets Manager 集成

## 概述

Vault 模块将 Keeper Secrets Manager（KSM）集成到 Guacamole 中，实现凭据的自动获取和注入。连接配置中的密码等敏感信息不再需要硬编码，而是在连接建立时从 KSM 动态获取。

**主要功能：**
- 从 Keeper Secrets Manager 获取密钥
- 通过 `${TOKEN}` 替换机制自动注入凭据
- 支持自定义 Token 映射路径
- 内存缓存，优化性能（5 秒 TTL）

---

## 模块结构

```
extensions/guacamole-vault/
├── pom.xml
├── guacamole-vault-base/                    # Vault 抽象层
│   └── src/main/java/org/apache/guacamole/vault/
│       ├── VaultAuthenticationProvider.java        # Vault 基类
│       ├── conf/VaultConfigurationService.java     # Vault 配置服务
│       ├── secret/
│       │   ├── VaultSecretService.java             # 密钥获取接口
│       │   └── CachedVaultSecretService.java       # 带缓存的装饰器
│       └── user/
│           └── VaultUserContext.java               # Vault 感知的用户上下文
└── guacamole-vault-ksm-starter/             # KSM 实现
    └── src/main/java/org/apache/guacamole/vault/ksm/
        ├── KsmAuthenticationProvider.java          # KSM 认证提供者
        ├── KsmAuthenticationAutoConfiguration.java # Spring 自动配置
        ├── conf/
        │   ├── KsmConfigurationService.java        # KSM 配置
        │   └── KsmConfigProperty.java              # KSM 属性定义
        └── secret/
            ├── KsmClient.java                      # KSM API 客户端
            ├── KsmRecordService.java               # KSM 记录管理
            └── KsmSecretService.java               # KSM 密钥解析
```

---

## 配置说明

### 启用 Vault 模块

```yaml
guacamole:
  vault:
    ksm:
      enabled: true
```

### KSM 连接配置

```yaml
guacamole:
  vault:
    ksm:
      enabled: true
      config:
        ksm-config: "keeper://<base64编码的配置>"
        allow-unverified-cert: false
```

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `guacamole.vault.ksm.enabled` | 是 | `false` | 启用 KSM Vault |
| `config.ksm-config` | **是** | — | Base64 编码的 KSM 配置（由 Keeper Commander CLI 生成） |
| `config.allow-unverified-cert` | 否 | `false` | 是否允许未验证的 SSL 证书 |

**生成 KSM 配置：**
```bash
keeper commander client --config --output=base64
```

---

## Token 映射

创建 Token 映射文件：`~/.guacamole/ksm-token-mapping.yml`

```yaml
# 格式：TOKEN名称: "KSM中的密钥路径"
USERNAME: "production/server-01/username"
PASSWORD: "production/server-01/password"
CONNECTION_HOSTNAME: "production/server-01/hostname"
CONNECTION_USERNAME: "production/server-01/username"
```

### 支持的 Token

| Token | 说明 | 使用示例 |
|-------|------|---------|
| `${USERNAME}` | 当前用户名 | 连接的用户名字段 |
| `${CONNECTION_NAME}` | 连接显示名称 | 主机名参数 |
| `${CONNECTION_ID}` | 连接数据库 ID | 自定义属性 |
| `${CONNECTION_HOSTNAME}` | 连接主机名 | VNC/RDP 主机字段 |
| `${CONNECTION_USERNAME}` | 连接用户名 | SSH 用户字段 |
| `${CONNECTION_GROUP_NAME}` | 父级组名称 | 标记用 |
| `${CONNECTION_GROUP_ID}` | 父级组数据库 ID | 过滤用 |

### 连接配置中使用 Token

```
连接名称：生产服务器
主机名：  ${CONNECTION_HOSTNAME}
用户名：  ${USERNAME}
密码：    ${PASSWORD}
```

建立连接时，`${USERNAME}` 会被替换为 KSM 中 `production/server-01/username` 的实际值。

---

## 属性文件（可选）

创建 `~/.guacamole/guacamole.properties.ksm`：

```properties
# 格式：属性名=KSM中的密钥路径
guacd.hostname=production/guacd-hostname
guacd.port=production/guacd-port
```

---

## 处理流程

### 启动阶段

```
Spring Boot 启动
  → KsmAuthenticationAutoConfiguration 加载
  → 创建 KsmClient、KsmSecretService、KsmRecordService Bean
  → 注册 KsmAuthenticationProvider
```

### 用户登录

```
用户提交登录 → KsmAuthenticationProvider → 认证成功
  → 创建 VaultUserContext
  → 加载 Token 映射
```

### 连接访问

```
用户点击连接
  → VaultUserContext.getConfiguration()
  → 扫描配置中的 ${TOKEN} 模式
  → 对每个 Token：
      → KsmSecretService.getValue(token, path)
        → 检查缓存（5 秒 TTL）
        → 缓存未命中 → KsmClient.getSecret(path)
        → 缓存命中 → 返回缓存值
      → 将 ${TOKEN} 替换为密钥值
  → 返回已解析的配置
```

---

## 错误处理

### 缺少 KSM 配置

```
ERROR: Property ksm-config is required.
```

**原因：** 未在 `application.yml` 中配置 `config.ksm-config`。

**解决：**
```yaml
guacamole:
  vault:
    ksm:
      config:
        ksm-config: "keeper://<base64编码的配置>"
```

### Token 映射文件不存在

```
WARN: Token mapping file not found: ksm-token-mapping.yml
```

**原因：** `~/.guacamole/ksm-token-mapping.yml` 文件不存在。

**解决：** 创建该文件并填入相应的 Token 映射。

### KSM 连接失败

```
ERROR: Failed to connect to Keeper Secrets Manager
```

**原因：** 网络问题或 KSM 配置无效。

**解决：**
- 检查到 KSM 服务器的网络连接
- 验证 `ksm-config` 有效且未过期
- 检查防火墙设置

### Token 未被替换

```
WARN: Token "${USERNAME}" not replaced
```

**原因：** Token 名称在映射文件中没有对应条目，或者密钥路径在 KSM 中不存在。

**解决：** 检查 Token 映射文件，确认密钥路径在 KSM 中存在。

---

## 验证

### 编译检查

```bash
mvn clean compile -q
```

### 启动检查

```bash
java -jar guacamole/target/guacamole-*.jar
```

预期的启动日志：
```
INFO  KsmAuthenticationAutoConfiguration : Keeper Secrets Manager vault extension enabled.
INFO  ExtensionResourceConfig : Loaded extension manifest: Keeper Secrets Manager
```

### 功能测试

1. 启用并配置 KSM 后启动应用
2. 登录 Guacamole
3. 创建使用 `${TOKEN}` 占位符的连接
4. 访问该连接
5. 验证 Token 被替换为 KSM 中的实际密钥值

---

## 故障排除

### 应用启动失败

**症状：** 启动时 Bean 创建错误。

**可能原因：**
- Vault Bean 之间存在循环依赖
- `ksm-config` 格式无效

**解决：**
- 检查日志中的详细错误信息
- 验证 `ksm-config` 是否正确 Base64 编码
- 确保 classpath 上有所有 Vault 依赖

### Token 不替换

**症状：** 连接使用了字面量 `${TOKEN}` 文本而非解析后的值。

**可能原因：**
- Token 映射文件缺失或格式错误
- Token 名称大小写不匹配
- 密钥路径在 KSM 中找不到

**解决：**
- 检查 Token 映射文件位置：`~/.guacamole/ksm-token-mapping.yml`
- 验证 Token 名称完全匹配（区分大小写）
- 通过 KSM API 直接测试密钥获取

### 启用 Vault 后连接失败

**症状：** 之前可用的连接，启用 Vault 后失败。

**可能原因：**
- KSM 不可访问（网络/凭据问题）
- 密钥已被更改或撤销
- KSM 限流

**解决：**
- 检查 KSM 连接和凭据
- 验证 KSM 中的密钥值是否正确
- 检查限流或服务可用性

---

## 参考

- [Keeper Secrets Manager 官方文档](https://docs.keeper.io/secrets-manager/)
- [Apache Guacamole 官方手册](https://guacamole.apache.org/doc/gug/)
- [Spring Boot AutoConfiguration](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.auto-configuration)

## 当前状态

**✅ 模块开发完成，等待 KSM 账号进行集成测试。**

---

## 开发计划

- [ ] 启动时配置校验（KSM 配置无效时快速失败）
- [ ] 优雅降级（Vault 失败不影响非 Token 连接）
- [ ] 添加 Token 解析逻辑的单元测试
- [ ] 使用模拟 KSM 服务器添加集成测试
- [ ] 添加监控指标（缓存命中率、密钥获取延迟）
- [ ] 改进错误信息，提供可操作的解决建议
