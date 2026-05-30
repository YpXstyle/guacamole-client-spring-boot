# Vault 模块文档

## 📋 概述

Vault 模块是 Guacamole Spring Boot 的扩展模块，用于集成 Keeper Secrets Manager (KSM)，实现密钥的自动管理和注入。

**主要功能：**
- 从 Keeper Secrets Manager 获取密钥
- 自动注入密钥到连接参数
- 支持 Token 映射和替换
- 缓存密钥，优化性能

---

## 🏗️ 模块结构

```
guacamole-spring-boot/
├── starters/
│   └── guacamole-vault/
│       ├── pom.xml                    # 父 POM
│       ├── guacamole-vault-base/      # 基础模块
│       │   ├── pom.xml
│       │   └── src/main/java/
│       │       └── org/apache/guacamole/vault/
│       │           ├── VaultAuthenticationProvider.java
│       │           ├── conf/
│       │           │   └── VaultConfigurationService.java
│       │           ├── secret/
│       │           │   ├── VaultSecretService.java
│       │           │   └── CachedVaultSecretService.java
│       │           └── user/
│       │               ├── VaultUserContext.java
│       │               └── VaultUserContextFactory.java
│       └── guacamole-vault-ksm-starter/  # KSM 实现
│           ├── pom.xml
│           └── src/main/java/
│               └── org/apache/guacamole/vault/ksm/
│                   ├── KsmAuthenticationProvider.java
│                   ├── KsmAuthenticationAutoConfiguration.java
│                   ├── conf/
│                   │   ├── KsmConfigurationService.java
│                   │   └── KsmConfigProperty.java
│                   └── secret/
│                       ├── KsmClient.java
│                       ├── KsmRecordService.java
│                       └── KsmSecretService.java
```

---

## 🔧 配置说明

### **1. 启用 Vault 模块**

```yaml
# application.yml
guacamole:
  vault:
    ksm:
      enabled: true
```

### **2. 配置 KSM 连接**

```yaml
# application.yml
guacamole:
  vault:
    ksm:
      enabled: true
      config:
        ksm-config: "keeper://your-ksm-config"
        allow-unverified-cert: false
```

**配置说明：**
- `ksm-config`: KSM 配置信息，从 Keeper Commander CLI 生成
- `allow-unverified-cert`: 是否允许未验证的 SSL 证书（默认 false）

### **3. Token 映射文件**

创建文件：`~/.guacamole/ksm-token-mapping.yml`

```yaml
# Token 映射配置
# 格式：TOKEN_NAME: "secret/path"
USERNAME: "my-server/username"
PASSWORD: "my-server/password"
CONNECTION_HOSTNAME: "my-server/hostname"
```

### **4. 属性文件（可选）**

创建文件：`~/.guacamole/guacamole.properties.ksm`

```properties
# 属性文件配置
# 格式：property.name=secret/path
guacd.hostname=my-server/guacd-hostname
guacd.port=my-server/guacd-port
```

---

## 🔄 自动处理流程

### **1. 启动阶段**

```
Spring Boot 启动
→ 加载 KsmAuthenticationAutoConfiguration
→ 创建所有 Bean (KsmClient, KsmSecretService, etc.)
→ 注册 KsmAuthenticationProvider
```

**自动完成：**
- ✅ 创建 KsmConfigurationService
- ✅ 创建 KsmRecordService
- ✅ 创建 KsmClient
- ✅ 创建 KsmSecretService
- ✅ 创建 KsmAuthenticationProvider

### **2. 用户登录**

```
用户提交登录请求
→ Spring Security 调用 KsmAuthenticationProvider
→ 认证成功
→ 创建 VaultUserContext
```

**自动完成：**
- ✅ 用户认证
- ✅ 创建 VaultUserContext
- ✅ 准备注入 Token

### **3. 用户访问连接**

```
用户点击连接
→ VaultUserContext.getConfiguration()
→ 获取 Token 映射
→ 注入 Token
```

**自动完成：**
- ✅ 获取连接配置
- ✅ 读取 Token 映射文件
- ✅ 替换 Token

### **4. 密钥获取**

```
KsmSecretService.getValue()
→ KsmClient.getSecret()
→ 检查缓存 (5秒 TTL)
→ 从 KSM 获取密钥
→ 更新缓存
```

**自动完成：**
- ✅ 检查缓存
- ✅ 从 KSM 获取密钥
- ✅ 更新缓存
- ✅ 返回密钥值

### **5. Token 替换**

```
TokenFilter.filter()
→ 替换 ${TOKEN} 格式
→ 生成最终配置
→ 建立连接
```

**自动完成：**
- ✅ 替换所有 Token
- ✅ 生成最终配置
- ✅ 建立连接

---

## 📊 支持的 Token

| Token 名称 | 说明 | 示例 |
|-----------|------|------|
| `USERNAME` | 当前用户名 | `${USERNAME}` |
| `CONNECTION_NAME` | 连接名称 | `${CONNECTION_NAME}` |
| `CONNECTION_ID` | 连接 ID | `${CONNECTION_ID}` |
| `CONNECTION_HOSTNAME` | 连接主机名 | `${CONNECTION_HOSTNAME}` |
| `CONNECTION_USERNAME` | 连接用户名 | `${CONNECTION_USERNAME}` |
| `CONNECTION_GROUP_NAME` | 连接组名称 | `${CONNECTION_GROUP_NAME}` |
| `CONNECTION_GROUP_ID` | 连接组 ID | `${CONNECTION_GROUP_ID}` |

---

## 🛡️ 错误处理

### **常见错误**

#### **1. 缺少 KSM 配置**
```
ERROR: Property ksm-config is required.
```

**原因：** 没有配置 `ksm-config` 属性

**解决：**
```yaml
guacamole:
  vault:
    ksm:
      config:
        ksm-config: "keeper://your-ksm-config"
```

#### **2. Token 映射文件不存在**
```
WARN: Token mapping file not found: ksm-token-mapping.yml
```

**原因：** 没有创建 Token 映射文件

**解决：** 创建 `~/.guacamole/ksm-token-mapping.yml` 文件

#### **3. KSM 连接失败**
```
ERROR: Failed to connect to Keeper Secrets Manager
```

**原因：** 网络问题或 KSM 配置错误

**解决：**
- 检查网络连接
- 验证 KSM 配置
- 检查防火墙设置

#### **4. Token 未替换**
```
WARN: Token "${USERNAME}" not replaced
```

**原因：** Token 在 KSM 中不存在

**解决：** 检查 Token 映射配置

---

## 🧪 测试验证

### **1. 编译测试**
```bash
mvn clean compile -q
```

### **2. 启动测试**
```bash
mvn clean install -q
java -jar guacamole-core/target/guacamole-core-1.0.0-SNAPSHOT.jar
```

### **3. 日志验证**
```bash
# 检查 Vault 模块日志
grep -i "vault\|ksm" logs/guacamole.log

# 预期日志
INFO  KsmAuthenticationAutoConfiguration : Keeper Secrets Manager vault extension enabled.
INFO  ExtensionResourceConfig : Loaded extension manifest: Keeper Secrets Manager
```

### **4. 功能测试**
1. 启动应用
2. 登录 Guacamole
3. 创建连接（使用 Token）
4. 访问连接
5. 验证 Token 是否替换

---

## 📝 配置示例

### **完整配置示例**

```yaml
# application.yml
guacamole:
  vault:
    ksm:
      enabled: true
      config:
        ksm-config: "keeper://your-base64-encoded-config"
        allow-unverified-cert: false

# Token 映射文件
# ~/.guacamole/ksm-token-mapping.yml
USERNAME: "my-server/username"
PASSWORD: "my-server/password"
CONNECTION_HOSTNAME: "my-server/hostname"
```

### **连接配置示例**

```
连接名称: My Server
主机名: ${CONNECTION_HOSTNAME}
用户名: ${USERNAME}
密码: ${PASSWORD}
```

---

## 🔍 故障排除

### **问题 1：启动失败**

**症状：** 应用启动失败，日志显示 Bean 创建错误

**可能原因：**
- 循环依赖
- 配置错误

**解决：**
- 检查日志中的详细错误信息
- 验证配置文件语法
- 确保所有依赖正确

### **问题 2：Token 不替换**

**症状：** 连接使用原始 Token，没有替换为密钥值

**可能原因：**
- Token 映射文件不存在
- Token 名称错误
- KSM 中没有对应的密钥

**解决：**
- 检查 Token 映射文件是否存在
- 验证 Token 名称是否正确
- 检查 KSM 中是否有对应的密钥

### **问题 3：连接失败**

**症状：** 访问连接时失败，日志显示密钥获取错误

**可能原因：**
- KSM 配置错误
- 网络问题
- 密钥不存在

**解决：**
- 检查 KSM 配置
- 验证网络连接
- 检查 KSM 中的密钥

---

## 📚 相关文档

- [Keeper Secrets Manager 官方文档](https://docs.keeper.io/secrets-manager/)
- [Guacamole 官方文档](https://guacamole.apache.org/doc/gug/)
- [Spring Boot AutoConfiguration](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.auto-configuration)

---

## 🎯 待办事项

- [ ] 添加配置验证（启动时检查 KSM 配置）
- [ ] 添加优雅降级（Vault 失败时不影响正常连接）
- [ ] 添加单元测试
- [ ] 添加集成测试
- [ ] 完善错误信息
- [ ] 添加监控指标

---

## 📞 技术支持

如有问题，请检查：
1. 日志文件中的错误信息
2. 配置文件语法
3. KSM 账号和配置
4. 网络连接

**当前状态：** ✅ 模块已开发完成，等待 KSM 账号测试
