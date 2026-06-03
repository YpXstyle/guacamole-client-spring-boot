# Guacamole Spring Boot Quick Start Guide

[← Back to Documentation](../README.md#documentation)

This guide is for first-time users, walking you through setting up and running the Guacamole Spring Boot system from scratch.

---

## Table of Contents

- [Environment Checklist](#environment-checklist)
- [Installing guacd](#installing-guacd)
- [Creating the Database](#creating-the-database)
- [Configuring application.yml](#configuring-applicationyml)
- [Building the Project](#building-the-project)
- [Starting the Application](#starting-the-application)
- [First Login](#first-login)
- [Creating Your First Connection](#creating-your-first-connection)
- [Common Startup Issues](#common-startup-issues)
- [Next Steps](#next-steps)

---

## Environment Checklist

Before you begin, ensure your system meets the following requirements:

| Component | Requirement | Verification Command |
|-----------|-------------|---------------------|
| JDK | 17 or higher | `java -version` |
| Maven | 3.8+ | `mvn --version` |
| Docker | Any version (optional, for guacd and/or database) | `docker --version` |
| PostgreSQL client | psql (optional, for manual database initialization) | `psql --version` |
| Node.js | 18.x (auto-installed during frontend build, no manual setup needed) | — |

If any component is missing, please install it before continuing.

---

## Installing guacd

guacd is the Guacamole proxy daemon responsible for parsing and rendering remote desktop protocols (RDP, VNC, SSH, etc.).

### Install with Docker (Recommended)

A single command is all you need:

```bash
docker run -d \
  --name guacd \
  -p 4822:4822 \
  guacamole/guacd:1.5.5
```

Verify guacd is running properly:

```bash
docker logs guacd
```

You should see output similar to:

```
guacd[1]: INFO: Guacamole proxy daemon (guacd) version 1.5.5 started
```

> **Note**: If you have a firewall, ensure port 4822 is open. guacd listens on TCP port 4822 by default.

### Installing Without Docker

If you don't use Docker, refer to the [Apache Guacamole official documentation](https://guacamole.apache.org/doc/gug/installing-guacamole.html) to compile guacd from source, or use your system package manager (e.g., `apt install guacamole`).

---

## Creating the Database

Guacamole requires a database to store users, connections, and permissions. The following uses PostgreSQL as an example; MySQL and SQL Server steps are similar.

### Create PostgreSQL with Docker

```bash
docker run -d \
  --name guacamole-db \
  -e POSTGRES_DB=guacamole \
  -e POSTGRES_USER=guacamole \
  -e POSTGRES_PASSWORD=guacamole \
  -p 5432:5432 \
  postgres:16-alpine
```

### Initialize the Database Schema

Guacamole provides SQL scripts to create the required table structure and default administrator user.

**PostgreSQL:**

```bash
# Create table structure
docker exec -i guacamole-db psql -U guacamole -d guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/001-create-schema.sql

# Create default administrator user (guacadmin/guacadmin)
docker exec -i guacamole-db psql -U guacamole -d guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-postgresql-starter/src/main/resources/schema/002-create-admin-user.sql
```

**MySQL:**

```bash
# Create table structure
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/001-create-schema.sql

# Create default administrator user (guacadmin/guacadmin)
mysql -h localhost -u guacamole -p guacamole \
  < extensions/guacamole-auth-jdbc/guacamole-auth-mysql-starter/src/main/resources/schema/002-create-admin-user.sql
```

**SQL Server:**

```bash
# Create table structure
sqlcmd -S localhost -U guacamole -P guacamole -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/001-create-schema.sql

# Create default administrator user (guacadmin/guacadmin)
sqlcmd -S localhost -U guacamole -P guacamole -d guacamole \
  -i extensions/guacamole-auth-jdbc/guacamole-auth-sqlserver-starter/src/main/resources/schema/002-create-admin-user.sql
```

> **Verification**: After initialization, the database should contain tables such as `guacamole_user`, `guacamole_connection`, `guacamole_connection_permission`, etc.

---

## Configuring application.yml

The default configuration file is located at `guacamole/src/main/resources/application.yml`. Below is the minimal usable configuration.

### Minimal Configuration (PostgreSQL)

```yaml
server:
  port: 8080

guacamole:
  guacd:
    hostname: localhost           # guacd address
    port: 4822                    # guacd port
  auth:
    postgresql:
      enabled: true               # Enable PostgreSQL authentication

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole
    username: guacamole
    password: guacamole           # Recommend using environment variable reference
    driver-class-name: org.postgresql.Driver
```

> **Security Recommendation**: Do not hardcode database passwords in configuration files. Use environment variable references: `password: ${GUACAMOLE_DB_PASSWORD}`.

### Configuration Differences with Docker

If you use `docker-compose up -d` to start the entire system, the above configuration is already built into the default `application.yml`. In a Docker environment, the database address should be the service name `postgres` instead of `localhost`. The docker-compose.yml handles this difference automatically through the `SPRING_DATASOURCE_URL` environment variable.

---

## Building the Project

### First Build (Full Build)

Execute in the project root directory:

```bash
mvn clean package -DskipTests
```

This command will:
1. Compile the three core modules: `guacamole-common`, `guacamole-common-js`, `guacamole-ext`
2. Compile all 17 extension modules
3. Install Node.js 18.18.0 and npm 9.8.1 (auto-downloaded on first build)
4. Run `npm install` to install frontend dependencies
5. Build the AngularJS frontend with webpack
6. Package into a Spring Boot Fat JAR

After a successful build, the JAR file is located at: `guacamole/target/guacamole-1.5.5.jar`

> **Note**: The first build may take 5-10 minutes depending on network speed. Subsequent builds are typically 1-2 minutes since frontend dependencies are cached.

### Build Options

| Option | Description |
|--------|-------------|
| `-DskipTests` | Skip tests |
| `-Dskip.frontend` | Skip all frontend builds (fastest) |
| `-Dforce.npm.install` | Force re-run `npm install` |

Example (skip frontend, backend only):

```bash
mvn clean package -DskipTests -Dskip.frontend
```

---

## Starting the Application

### Run Fat JAR Directly

```bash
java -jar guacamole/target/guacamole-*.jar
```

### Run with Maven Plugin (Development Mode)

```bash
mvn spring-boot:run -pl guacamole
```

### Run with Docker Compose

```bash
docker-compose up -d
```

This starts three containers simultaneously: guacd, PostgreSQL, and Guacamole.

### Verify Startup

After successful startup, you should see output similar to:

```
[----------------------------------------------------------]
	Started successfully! Access URL: http://192.168.1.100:8080
[----------------------------------------------------------]
```

At this point, open `http://localhost:8080/` in your browser to see the Guacamole login page.

---

## First Login

### Login Credentials

| Item | Value |
|------|-------|
| URL | `http://localhost:8080/` |
| Username | `guacadmin` |
| Password | `guacadmin` |

> **Note**: The default administrator account only exists after enabling JDBC authentication (PostgreSQL/MySQL/SQL Server) and executing `002-create-admin-user.sql`. Please change the default password immediately in production environments.

### Login Steps

1. Open `http://localhost:8080/` in your browser
2. See the Guacamole login interface (purple theme)
3. Enter username `guacadmin`, password `guacadmin`
4. Click the "Login" button

After successful login, you will enter the Guacamole main interface with the user menu and connection list (currently empty) on the left side.

---

## Creating Your First Connection

Using an SSH connection as an example:

1. **After logging in**, click the settings icon (gear) in the upper right corner
2. **On the settings page**, click the "Connections" menu on the left
3. **Click the "New Connection" button** in the upper right corner
4. **Fill in the connection information**:
   - **Name**: Enter a connection name, e.g., `My SSH Server`
   - **Location**: Keep default (root group)
   - **Protocol**: Select `SSH`
   - **Parameters**:
     - **Hostname**: Enter the target server's IP or domain name
     - **Port**: Default `22`
     - **Username**: Login username
     - **Password**: Login password (or leave blank to use credential settings)
   - **Other options**: Adjust color depth, fonts, etc. as needed
5. **Click "Save"**

### Test the Connection

1. Return to the main interface; you should see the newly created connection in the connection list
2. Click the connection name
3. The browser will open a WebSocket tunnel, connecting to the target server via guacd
4. If everything is working, you will see the SSH terminal login prompt

> **Troubleshooting**: If the connection fails, check:
> - Is guacd running: `docker ps | grep guacd`
> - Is the target server reachable: `ping <target IP>`
> - Is the guacd port accessible: `telnet localhost 4822` (from the Guacamole server)
> - Are there error messages in the application logs

---

## Common Startup Issues

### Issue 1: Database Connection Failure at Startup

**Error Message**: `Cannot create PoolableConnectionFactory` or `Connection refused`

**Possible Causes**:
- Database not started
- Database connection information misconfigured
- Firewall blocking the database port

**Solution**:
- Check if the database is running: `docker ps | grep postgres`
- Verify the address and port in `spring.datasource.url` are correct
- Confirm the database username and password are correct

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/guacamole   # Verify address and port
    username: guacamole                                 # Verify username
    password: ${GUACAMOLE_DB_PASSWORD:guacamole}        # Verify password
```

### Issue 2: guacd Connection Failure at Startup

**Error Message**: `Connection refused` or `guacd: Connection closed`

**Possible Causes**:
- guacd not started
- `guacamole.guacd.hostname` misconfigured
- guacd port configuration doesn't match the running port

**Solution**:
```yaml
guacamole:
  guacd:
    hostname: localhost           # Change to the actual guacd address
    port: 4822                    # Confirm port matches docker run -p
```

### Issue 3: Blank Page or Frontend Loading Failure

**Possible Causes**:
- Frontend build didn't run correctly
- Static resources cached by the browser

**Solution**:
```bash
# Rebuild frontend
mvn clean package -DskipTests
# Or force refresh without cache (Ctrl+Shift+R / Cmd+Shift+R)
```

### Issue 4: Port Already in Use

**Error Message**: `Web server failed to start. Port 8080 was already in use.`

**Solution**: Change the port or terminate the occupying process
```yaml
server:
  port: 9090    # Change to another available port
```

### Issue 5: Extension Not Working

**Reasons why an enabled extension may not load**:
- Extension dependency not added to `guacamole/pom.xml` (modifying `application.yml` alone is not enough)
- Extension's `enabled` property not set to `true`

**Verification**: The startup log prints loaded extension modules.

---

## Next Steps

Congratulations! You have successfully set up and running Guacamole Spring Boot. You can now:

1. **Learn more about configuration** — See [CONFIGURATION.md](CONFIGURATION_EN.md) for detailed configuration options for all extensions
2. **Configure LDAP authentication** — Integrate enterprise LDAP/AD directories
3. **Configure SSO** — Use CAS, OpenID Connect, or SAML for single sign-on
4. **Enable TOTP two-factor authentication** — Add account security
5. **Configure session recording** — Audit and replay user sessions
6. **Custom build and deployment** — See [BUILD.md](BUILD_EN.md) for Docker, CI/CD, and other advanced build options
7. **Understand system architecture** — See [ARCHITECTURE.md](ARCHITECTURE_EN.md) for in-depth internal design
8. **Develop extensions** — See [EXTENSIONS_EN.md](EXTENSIONS_EN.md) to learn how to develop custom extensions

### Production Environment Notes

- **Change default password**: Change the `guacadmin` password immediately after first login
- **Use environment variables**: Inject sensitive information (passwords, keys) via environment variables, never hardcode
- **Configure SSL/TLS**: Set up HTTPS for the web interface
- **Limit port exposure**: Only expose ports 8080/443 externally; guacd port 4822 and database ports should not be publicly accessible
- **Enable audit logging**: Enable the History extension to record user operations
- **Regular database backups**: `pg_dump` (PostgreSQL) or `mysqldump` (MySQL)
- **Resource monitoring**: Monitor application health via Spring Boot Actuator endpoints
