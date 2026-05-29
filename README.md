# Guacamole Spring Boot

Apache Guacamole 1.5.5 迁移至 Spring Boot 3.3.5 + JDK 17 的远程桌面网关。

## 构建

```bash
mvn clean package -pl guacamole-core -am -DskipTests
```

## 运行

```bash
java -jar guacamole-core/target/guacamole-core-1.0.0-SNAPSHOT.jar
```

或:

```bash
mvn spring-boot:run -pl guacamole-core
```

## Docker

```bash
docker-compose up -d
```

## 配置

配置文件: `guacamole-core/src/main/resources/application.yml`

启用认证扩展需添加Maven依赖并设置 `guacamole.auth.xxx.enabled: true`

## 项目结构

- guacamole-common: 协议层
- guacamole-ext: 扩展API层  
- guacamole-core: Web应用核心
- starters/: 认证扩展Starter模块
