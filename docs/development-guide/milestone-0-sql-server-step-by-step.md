# Milestone 0 Implementation Guide

## Repository and Engineering Standards

**Project:** Private Messenger  
**Milestone:** 0 — Repository and standards  
**Database:** Microsoft SQL Server in Docker  
**Application stack:** Java 25, Quarkus 3.33 LTS, Maven  
**Status:** Executable build guide  
**Last reviewed:** 2026-07-05

---

## 0. Canonical Current State (2026-07)

This section is authoritative for the repository's current behavior.
If older sections in this file conflict with this section, follow this section.

### 0.1 Environment model

The project currently uses this environment strategy:

1. **Local**

- App runs from terminal/IDE (`./mvnw quarkus:dev`)
- SQL Server runs locally and is reachable at `localhost:1433`

2. **DevDocker**

- Planned next step
- App and SQL Server both run in Docker
- Intended SQL Server host port: `1434`

3. **Production**

- Future hosted target
- Deployment automation will be activated only after a persistent remote environment exists

### 0.2 Database and credentials baseline

- Database name: `wl_chat`
- Application login/user: `wl_chat_app`
- Runtime datasource defaults are defined in `src/main/resources/application.properties`

### 0.3 SQL initialization model

Initialization is split into two responsibilities:

1. **Bootstrap script (admin, one-time per environment)**

- `scripts/database/bootstrap/V0__create_wl_chat_database.sql`
- Creates the `wl_chat` database if it does not exist

2. **Flyway migrations (versioned, immutable)**

- `src/main/resources/db/migration/V1__create_app_login_and_user.sql`
- `src/main/resources/db/migration/V2__grant_app_permissions.sql`

### 0.4 Quarkus runtime posture

- `quarkus.datasource.devservices.enabled=false`
- Flyway migrate-at-start is off by default and enabled explicitly per environment
- Health endpoints are available at `/q/health`, `/q/health/live`, `/q/health/ready`

### 0.5 CI posture

- `.github/workflows/db-local-bootstrap-migrate.yml`
  - Validation-only workflow using an ephemeral SQL Server container in GitHub runner
- `.github/workflows/db-remote-bootstrap-migrate.yml`
  - Deferred manual workflow scaffold for future hosted target
  - Not treated as active production deployment until persistent remote infrastructure exists

### 0.6 Consistency note

All examples in this document now follow the current repository baseline:

- Database: `wl_chat`
- App login: `wl_chat_app`
- Bootstrap script: `scripts/database/bootstrap/V0__create_wl_chat_database.sql`
- Flyway scripts: `src/main/resources/db/migration/V*__*.sql`

---

## 1. Purpose

Milestone 0 creates a reproducible engineering foundation. It deliberately contains no user, invitation, conversation, or message functionality.

### 1.1 Prerequisites: Initialize Maven Wrapper and `pom.xml`

Before proceeding, ensure both `pom.xml` and the wrapper scripts exist and are valid:

1. **Check `pom.xml` is not empty:**

   ```bash
   wc -l pom.xml
   ```

   If the file is empty (0 bytes), populate it with a minimal valid Maven POM that includes a compiler configuration:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                http://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion>4.0.0</modelVersion>
       <groupId>com.wayden.messenger</groupId>
       <artifactId>chat-backend</artifactId>
       <version>0.0.1-SNAPSHOT</version>
       <packaging>jar</packaging>
       <name>Chat Backend</name>
       <description>Private messenger platform backend</description>
       <properties>
           <maven.compiler.release>25</maven.compiler.release>
           <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
       </properties>
       <build>
           <plugins>
               <plugin>
                   <groupId>org.apache.maven.plugins</groupId>
                   <artifactId>maven-compiler-plugin</artifactId>
                   <version>3.11.0</version>
                   <configuration>
                       <release>25</release>
                   </configuration>
               </plugin>
           </plugins>
       </build>
   </project>
   ```

2. **Generate Maven Wrapper scripts:**

   Using the globally installed Maven (required only for this step), run:

   ```bash
   mvn org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper \
     -Dmaven=3.9.16 \
     -Dtype=only-script
   ```

   This creates:
   - `mvnw` (Unix script)
   - `mvnw.cmd` (Windows script)
   - `.mvn/wrapper/maven-wrapper.properties` (configuration file)

3. **Make the Unix script executable:**

   ```bash
   chmod +x mvnw
   ```

4. **Verify the wrapper works:**

   ```bash
   ./mvnw --version
   ```

   Expected output:

   ```text
   Apache Maven 3.9.16
   Java version: 25 (or higher)
   ```

### 1.2 Completion Goal

At completion, a developer must be able to:

```bash
git clone <repository-url>
cd private-messenger
docker compose up -d --wait sqlserver
./scripts/database/init-local.sh
./mvnw clean verify
./mvnw quarkus:dev
```

The build must use a pinned Maven version, start against SQL Server, expose health endpoints, run automated tests, enforce formatting and static analysis, and pass in CI.

**Note:** Before running these commands, ensure `pom.xml` and Maven Wrapper files (`mvnw`, `mvnw.cmd`) are initialized. See **Section 1.1: Prerequisites** above for required setup.

---

## 2. Fixed Baseline

| Component               | Baseline                                    |
| ----------------------- | ------------------------------------------- |
| Java                    | 25                                          |
| Quarkus                 | 3.33.2.1 LTS                                |
| Maven                   | 3.9.16 through Maven Wrapper                |
| SQL Server              | SQL Server 2022 Developer Edition container |
| Formatter               | Spotless Maven Plugin 3.6.0                 |
| Java formatter          | google-java-format 1.35.0                   |
| Static analysis         | SpotBugs Maven Plugin 4.10.2.0              |
| Environment enforcement | Maven Enforcer Plugin 3.6.3                 |
| CI                      | GitHub Actions                              |
| Health                  | Quarkus SmallRye Health                     |

Do not use the SQL Server `sa` login from the application. The application will use a separate local-development login named `wl_chat_app`.

---

## 3. Before Starting

Verify the workstation has the necessary tooling:

```bash
java --version
git --version
docker version
docker compose version
mvn --version
```

Expected Java major version:

```text
25
```

### Existing SQL Server container

If another SQL Server container already binds host port `1433`, do one of the following before starting this guide:

1. Stop that container and let this repository manage SQL Server.
2. Reuse its Compose definition inside this repository.
3. Change this project's host port and JDBC URL together.

Do not attempt to run two containers on the same host port.

### Already configured local SQL Server and VS Code connection

If your workstation already has a running SQL Server container on `localhost:1433` (for example `local_sql_server` using `mssql/server:2022-latest`) and you can already create databases and users from VS Code, you may reuse that environment.

In that case:

1. Skip all `docker compose up -d --wait sqlserver` steps in this guide.
2. Skip bootstrap/Flyway commands only if the `wl_chat` database and `wl_chat_app` login/user already exist.
3. Keep `quarkus.datasource.jdbc.url` on `localhost:1433` and continue with Quarkus and test steps.

For team consistency, keep this repository's Compose setup for CI and for developers who do not already have a local SQL Server instance.

Check port usage:

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

---

## 4. Generate the Quarkus Project

From the parent directory in which the repository should live:

```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.33.2.1:create \
  -DprojectGroupId=com.wayden.messenger \
  -DprojectArtifactId=private-messenger \
  -Dextensions='rest-jackson,jdbc-mssql,smallrye-health' \
  -DnoCode
```

Enter the project:

```bash
cd private-messenger
```

The extensions provide:

| Extension                 | Responsibility                                               |
| ------------------------- | ------------------------------------------------------------ |
| `quarkus-rest-jackson`    | HTTP REST and JSON support                                   |
| `quarkus-jdbc-mssql`      | Microsoft SQL Server JDBC driver and datasource integration  |
| `quarkus-smallrye-health` | Liveness, readiness, startup, and aggregate health endpoints |

The `-DnoCode` option avoids generating a disposable greeting endpoint.

---

## 5. Initialize Git

```bash
git init
git branch -M main
```

Create the supporting directories:

```bash
mkdir -p \
  .github/workflows \
  docs/architecture/decisions \
  docs/architecture/diagrams \
  docs/api \
  docs/database \
  docs/development \
  docs/operations \
  postman \
  scripts/database
```

---

## 6. Add Repository-Level Text Standards

### 6.1 `.editorconfig`

Create `.editorconfig`:

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.java]
indent_style = space
indent_size = 4

[*.{yaml,yml}]
indent_style = space
indent_size = 2

[*.xml]
indent_style = space
indent_size = 4

[*.md]
trim_trailing_whitespace = false

[*.cmd]
end_of_line = crlf
```

### 6.2 `.gitattributes`

Create `.gitattributes`:

```gitattributes
* text=auto

*.java text eol=lf
*.xml text eol=lf
*.properties text eol=lf
*.yaml text eol=lf
*.yml text eol=lf
*.sql text eol=lf
*.md text eol=lf
*.sh text eol=lf
mvnw text eol=lf
mvnw.cmd text eol=crlf
```

### 6.3 `.gitignore`

Ensure `.gitignore` contains at least:

```gitignore
target/

.idea/
*.iml

.classpath
.project
.settings/

.vscode/

.DS_Store

.env
.env.local
*.log
```

Do not ignore `.env.example`.

---

## 7. Pin the Maven Wrapper

Quarkus normally generates the Maven Wrapper. Verify that these exist:

```bash
ls -la mvnw mvnw.cmd .mvn/wrapper/maven-wrapper.properties
```

Pin Maven 3.9.16 using the current wrapper plugin:

```bash
mvn org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper \
  -Dmaven=3.9.16 \
  -Dtype=only-script
```

Ensure the Unix script is executable:

```bash
chmod +x mvnw
```

Verify:

```bash
./mvnw --version
```

Expected:

```text
Apache Maven 3.9.16
Java version: 25
```

From this point onward, use `./mvnw`, not the globally installed `mvn`.

---

## 8. Review and Update `pom.xml`

The Quarkus generator creates the BOM, Quarkus Maven plugin, Surefire, and initial dependencies.

### 8.1 Set Java 25

Ensure the properties include:

```xml
<maven.compiler.release>25</maven.compiler.release>
```

### 8.2 Confirm dependencies

Ensure the following dependencies exist:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-jackson</artifactId>
</dependency>

<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-mssql</artifactId>
</dependency>

<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
</dependency>

<dependency>
    <groupId>io.quarkus</groupId>
  <artifactId>quarkus-junit</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
```

Include Flyway in the baseline. Bootstrap and migration scripts in this repository already rely on Flyway-managed, versioned SQL scripts.

### 8.3 Add build-tool version properties

Add:

```xml
<spotless.version>3.6.0</spotless.version>
<google-java-format.version>1.35.0</google-java-format.version>
<spotbugs.version>4.10.2.0</spotbugs.version>
<maven-enforcer.version>3.6.3</maven-enforcer.version>
```

### 8.4 Add Maven Enforcer

Under `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>${maven-enforcer.version}</version>

    <executions>
        <execution>
            <id>enforce-build-environment</id>
            <goals>
                <goal>enforce</goal>
            </goals>

            <configuration>
                <failFast>true</failFast>

                <rules>
                    <requireJavaVersion>
                        <version>[25,26)</version>
                    </requireJavaVersion>

                    <requireMavenVersion>
                        <version>[3.9.16,4.0.0)</version>
                    </requireMavenVersion>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

This rejects Java 24, Java 26, Maven versions older than 3.9.16, and Maven 4 until the project deliberately adopts them.

### 8.5 Add Spotless

Under `<build><plugins>`:

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>${spotless.version}</version>

    <configuration>
        <formats>
            <format>
                <includes>
                    <include>*.md</include>
                    <include>*.yaml</include>
                    <include>*.yml</include>
                    <include>*.xml</include>
                    <include>*.properties</include>
                    <include>*.sql</include>
                </includes>

                <trimTrailingWhitespace/>
                <endWithNewline/>
            </format>
        </formats>

        <java>
            <googleJavaFormat>
                <version>${google-java-format.version}</version>
                <style>GOOGLE</style>
            </googleJavaFormat>

            <removeUnusedImports/>
            <formatAnnotations/>
            <trimTrailingWhitespace/>
            <endWithNewline/>
        </java>
    </configuration>

    <executions>
        <execution>
            <id>check-formatting</id>
            <phase>validate</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Local correction:

```bash
./mvnw spotless:apply
```

Validation without modification:

```bash
./mvnw spotless:check
```

CI must check formatting; it must not rewrite committed code.

### 8.6 Add SpotBugs

Under `<build><plugins>`:

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>${spotbugs.version}</version>

    <configuration>
        <effort>Max</effort>
        <threshold>Medium</threshold>
        <failOnError>true</failOnError>
        <xmlOutput>true</xmlOutput>
    </configuration>

    <executions>
        <execution>
            <id>spotbugs-check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

SpotBugs is the initial defect-oriented static analyser. A project-specific Checkstyle ruleset can be introduced later after the team has intentionally defined naming and structure rules.

---

## 9. Create the SQL Server Compose Environment

### 9.1 Local environment example

Create `.env.example`:

```dotenv
MSSQL_SA_PASSWORD=replace_with_sa_password
WL_CHAT_DB_USERNAME=wl_chat_app
WL_CHAT_DB_PASSWORD=replace_with_app_password
WL_CHAT_DB_URL=jdbc:sqlserver://localhost:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=true
WL_CHAT_FLYWAY_MIGRATE_AT_START=false
WL_CHAT_FLYWAY_USERNAME=sa
WL_CHAT_FLYWAY_PASSWORD=replace_with_sa_password
```

Create the local file:

```bash
cp .env.example .env
```

The `.env` file is intentionally ignored by Git.

These are local-only development credentials. They must never be reused for a remotely accessible deployment.

### 9.2 Database bootstrap and migration scripts

Use the existing repository scripts:

1. Bootstrap (one-time, admin)
   - `scripts/database/bootstrap/V0__create_wl_chat_database.sql`
2. Flyway migrations (versioned)
   - `src/main/resources/db/migration/V1__create_app_login_and_user.sql`
   - `src/main/resources/db/migration/V2__grant_app_permissions.sql`

Bootstrap must run with an admin login (for example `sa`) so the target database exists before migrations are applied.

Flyway scripts are intentionally idempotent where needed and versioned for deterministic replay.

### 9.3 `compose.yaml`

Create or replace `compose.yaml`:

```yaml
name: wl-chat-local

services:
  sqlserver:
    image: mcr.microsoft.com/mssql/server:2022-latest
    container_name: wl-chat-sqlserver
    hostname: wl-chat-sqlserver

    environment:
      ACCEPT_EULA: "Y"
      MSSQL_PID: "Developer"
      MSSQL_SA_PASSWORD: ${MSSQL_SA_PASSWORD:?MSSQL_SA_PASSWORD is required}

    ports:
      - "127.0.0.1:1433:1433"

    volumes:
      - wl-chat-sqlserver-data:/var/opt/mssql

    healthcheck:
      test:
        [
          "CMD-SHELL",
          '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$$MSSQL_SA_PASSWORD" -C -Q "SELECT 1" > /dev/null || exit 1',
        ]
      interval: 5s
      timeout: 5s
      retries: 30
      start_period: 20s

    restart: unless-stopped

volumes:
  wl-chat-sqlserver-data:
```

Important properties:

- Port `1433` is bound only to `127.0.0.1`.
- SQL Server data is held in a named Docker volume.
- The health check verifies an actual SQL query.
- Initialization uses repository bootstrap and Flyway migration scripts.
- The application does not authenticate as `sa`.

Start SQL Server:

```bash
docker compose up -d --wait sqlserver
```

If you are reusing an existing local SQL Server container that is already running on `localhost:1433`, skip this command.

Bootstrap and migrate the database:

```bash
./scripts/database/bootstrap-local.sh
./scripts/database/migrate-local.sh
```

or as a single command:

```bash
./scripts/database/init-local.sh
```

Inspect:

```bash
docker compose ps
```

Verify directly as `wl_chat_app`:

```bash
docker compose exec sqlserver \
  /opt/mssql-tools18/bin/sqlcmd \
  -S localhost \
  -U wl_chat_app \
  -P "$WL_CHAT_DB_PASSWORD" \
  -C \
  -d wl_chat \
  -Q "SELECT DB_NAME() AS database_name, SUSER_SNAME() AS login_name;"
```

Expected values:

```text
database_name = wl_chat
login_name    = wl_chat_app
```

---

## 10. Configure Quarkus

Replace `src/main/resources/application.properties` with:

```properties
quarkus.application.name=chat-backend

# HTTP
quarkus.http.host=127.0.0.1
quarkus.http.port=8080

# SQL Server
quarkus.datasource.db-kind=mssql
quarkus.datasource.username=${WL_CHAT_DB_USERNAME:wl_chat_app}
quarkus.datasource.password=${WL_CHAT_DB_PASSWORD}
quarkus.datasource.jdbc.url=${WL_CHAT_DB_URL:jdbc:sqlserver://localhost:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=true}

# Disable SQL Server Dev Services and use explicit local DB
quarkus.datasource.devservices.enabled=false

# Flyway
quarkus.flyway.migrate-at-start=${WL_CHAT_FLYWAY_MIGRATE_AT_START:false}
quarkus.flyway.locations=db/migration
quarkus.flyway.username=${WL_CHAT_FLYWAY_USERNAME:${WL_CHAT_DB_USERNAME:wl_chat_app}}
quarkus.flyway.password=${WL_CHAT_FLYWAY_PASSWORD:${WL_CHAT_DB_PASSWORD}}
quarkus.flyway.placeholders.app_login=wl_chat_app
quarkus.flyway.placeholders.app_password=${WL_CHAT_DB_PASSWORD}

# Conservative initial pool
quarkus.datasource.jdbc.min-size=1
quarkus.datasource.jdbc.max-size=10
quarkus.datasource.jdbc.acquisition-timeout=10S

# Health
quarkus.datasource.health.enabled=true

# Health UI is not required in non-development environments
%prod.quarkus.smallrye-health.ui.enabled=false
```

`trustServerCertificate=true` is acceptable only for the local self-signed development container. A remotely deployed system must use a trusted server certificate and disable certificate bypass.

---

## 11. Start the Application

Start SQL Server and run initialization:

```bash
docker compose up -d --wait sqlserver
./scripts/database/init-local.sh
```

When reusing an already initialized local SQL Server on `localhost:1433`, skip the bootstrap and migration commands and proceed directly to Quarkus startup.

Start Quarkus development mode:

```bash
./mvnw quarkus:dev
```

In another terminal, test:

```bash
curl -i http://localhost:8080/q/health
curl -i http://localhost:8080/q/health/live
curl -i http://localhost:8080/q/health/ready
curl -i http://localhost:8080/q/health/started
```

Expected behavior:

| Endpoint            | Meaning                        | Expected                   |
| ------------------- | ------------------------------ | -------------------------- |
| `/q/health`         | Aggregate health               | `UP`                       |
| `/q/health/live`    | Process should remain alive    | `UP`                       |
| `/q/health/ready`   | Application can serve requests | `UP` with datasource check |
| `/q/health/started` | Startup completed              | `UP`                       |

Test the distinction deliberately:

```bash
docker compose stop sqlserver
curl -i http://localhost:8080/q/health/ready
curl -i http://localhost:8080/q/health/live
```

Expected:

- Readiness becomes `DOWN`.
- Liveness normally remains `UP`.

Restart the database:

```bash
docker compose start sqlserver
```

---

## 12. Add Automated Tests

### 12.1 Health endpoint test

Create:

```text
src/test/java/com/wayden/messenger/bootstrap/HealthEndpointTest.java
```

```java
package com.wayden.messenger.bootstrap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class HealthEndpointTest {

    @Test
    void livenessShouldReportUp() {
        given()
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void readinessShouldReportUpWhenSqlServerIsAvailable() {
        given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
```

### 12.2 Direct database connectivity test

Create:

```text
src/test/java/com/wayden/messenger/bootstrap/DatabaseConnectivityTest.java
```

```java
package com.wayden.messenger.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class DatabaseConnectivityTest {

    @Inject AgroalDataSource dataSource;

    @Test
    void applicationLoginShouldConnectToWlChatDatabase() throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT DB_NAME()");
                var resultSet = statement.executeQuery()) {

            resultSet.next();

            assertEquals("wl_chat", resultSet.getString(1));
        }
    }
}
```

Format and run:

```bash
./mvnw spotless:apply
./mvnw test
```

The tests intentionally require SQL Server. In Milestone 1, Testcontainers will make database-dependent test startup more isolated and automatic.

---

## 13. Create the ADR Template

Create:

```text
docs/architecture/decisions/ADR-0000-template.md
```

```markdown
# ADR-NNNN: Decision title

- Status: Proposed
- Date: YYYY-MM-DD
- Decision owners: Project maintainers

## Context

Describe the problem, constraints, and reason a decision is required.

## Decision

State the selected approach clearly.

## Alternatives considered

### Alternative 1

Describe the option and why it was not selected.

### Alternative 2

Describe the option and why it was not selected.

## Consequences

### Positive

- Consequence

### Negative

- Consequence

### Risks and mitigations

- Risk and mitigation

## Security impact

Describe confidentiality, integrity, authentication, authorization, and
availability implications.

## Operational impact

Describe deployment, monitoring, backup, maintenance, and support implications.

## Revisit conditions

State the evidence or change that would justify reconsidering this decision.
```

Create the first decision:

```text
docs/architecture/decisions/ADR-0001-use-modular-monolith.md
```

Decision summary:

```text
Build one deployable Quarkus application with explicit internal feature
boundaries. Do not introduce microservices until independent deployment,
ownership, scaling, or failure isolation becomes a demonstrated requirement.
```

---

## 14. Write the Initial README

Create `README.md`:

````markdown
# Private Messenger

API-first, invite-only instant messaging platform built as an
enterprise-grade learning exercise.

## Current status

Milestone 0 — repository and engineering foundation.

No identity, conversation, or messaging functionality exists yet.

## Technology

- Java 25
- Quarkus 3.33 LTS
- Maven Wrapper 3.9.16
- Microsoft SQL Server 2022 in Docker
- Quarkus Agroal JDBC datasource
- SmallRye Health
- JUnit 5
- REST Assured
- Spotless
- SpotBugs
- GitHub Actions

## Prerequisites

- JDK 25
- Git
- Docker with Docker Compose
- x86-64 Docker host for a supported SQL Server Linux container

A global Maven installation is not required after project creation.

## First-time setup

```bash
cp .env.example .env
docker compose up -d --wait sqlserver
./scripts/database/init-local.sh
```
````

## Build and test

```bash
./mvnw clean verify
```

## Run locally

```bash
./mvnw quarkus:dev
```

## Health endpoints

```text
GET http://localhost:8080/q/health
GET http://localhost:8080/q/health/live
GET http://localhost:8080/q/health/ready
GET http://localhost:8080/q/health/started
```

## Format source

```bash
./mvnw spotless:apply
```

## Stop local infrastructure

Preserve data:

```bash
docker compose down
```

Delete the local database volume:

```bash
docker compose down --volumes
```

The second command permanently removes local SQL Server data.

## Architecture decisions

Architecture decision records are stored under:

```text
docs/architecture/decisions
```

## Security

Do not commit credentials, access tokens, private keys, `.env` files, database
backups, or production configuration.

````

---

## 15. Add GitHub Actions CI

Create:

```text
.github/workflows/ci.yaml
````

```yaml
name: CI

on:
  push:
    branches:
      - main

  pull_request:

permissions:
  contents: read

jobs:
  verify:
    name: Build and verify
    runs-on: ubuntu-latest
    timeout-minutes: 20

    env:
      MSSQL_SA_PASSWORD: Messenger_SA_CI_2026!
      WL_CHAT_DB_USERNAME: wl_chat_app
      WL_CHAT_DB_PASSWORD: WL_Chat_App_CI_2026!
      WL_CHAT_DB_URL: jdbc:sqlserver://localhost:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=true

    steps:
      - name: Check out repository
        uses: actions/checkout@v6

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "25"
          architecture: x64
          cache: maven

      - name: Verify Maven Wrapper
        run: ./mvnw --version

      - name: Start SQL Server
        run: docker compose up -d --wait sqlserver

      - name: Bootstrap database
        run: docker compose exec -T sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d master -b -i /dev/stdin < scripts/database/bootstrap/V0__create_wl_chat_database.sql

      - name: Run Flyway migrations
        run: docker run --rm --network host -v "$PWD/src/main/resources/db/migration:/flyway/sql" flyway/flyway:10.17.3 -url="jdbc:sqlserver://localhost:1433;databaseName=wl_chat;encrypt=true;trustServerCertificate=true" -user="sa" -password="$MSSQL_SA_PASSWORD" -locations="filesystem:/flyway/sql" -placeholders.app_login="wl_chat_app" -placeholders.app_password="$WL_CHAT_DB_PASSWORD" migrate

      - name: Build, test, format-check, and analyze
        run: ./mvnw --batch-mode --no-transfer-progress clean verify

      - name: Print SQL Server logs on failure
        if: failure()
        run: docker compose logs --no-color sqlserver

      - name: Stop infrastructure
        if: always()
        run: docker compose down --volumes
```

CI intentionally uses the same Compose definition and Maven command as local development. There is no separate hidden CI-only build path.

---

## 16. Perform the Full Local Verification

From the repository root:

```bash
docker compose down --volumes

cp -n .env.example .env

docker compose up -d --wait sqlserver
./scripts/database/init-local.sh

./mvnw spotless:apply
./mvnw clean verify
```

Start the application:

```bash
./mvnw quarkus:dev
```

In another terminal:

```bash
curl -s http://localhost:8080/q/health/live
curl -s http://localhost:8080/q/health/ready
```

Both should report:

```json
{
  "status": "UP"
}
```

Stop Quarkus with `Ctrl+C`.

---

## 17. Create the Initial Commit

Review:

```bash
git status
git diff
```

Stage and commit:

```bash
git add .
git commit -m "chore: establish Quarkus project foundation"
```

Create a private remote repository without auto-generating another README or `.gitignore`.

Connect and push:

```bash
git remote add origin <repository-url>
git push -u origin main
```

Confirm the GitHub Actions workflow passes.

---

## 18. Clean-Checkout Test

This is the most important Milestone 0 proof.

From another directory:

```bash
git clone <repository-url> private-messenger-clean
cd private-messenger-clean

cp .env.example .env

docker compose up -d --wait sqlserver
./scripts/database/init-local.sh

./mvnw clean verify
./mvnw quarkus:dev
```

No IDE settings, global Maven version, hand-created database, or uncommitted source file should be required.

---

## 19. Exit Criteria Checklist

### Git repository

- [ ] Repository initialized.
- [ ] `main` branch exists.
- [ ] Initial commit pushed.
- [ ] `.gitignore`, `.gitattributes`, and `.editorconfig` are committed.

### Maven Wrapper

- [ ] `mvnw`, `mvnw.cmd`, and wrapper properties are committed.
- [ ] Maven 3.9.16 is pinned.
- [ ] `./mvnw --version` reports Java 25.

### Quarkus skeleton

- [ ] Quarkus 3.33.2.1 LTS is used.
- [ ] REST Jackson, SQL Server JDBC, and SmallRye Health extensions are present.
- [ ] No sample greeting endpoint remains.

### Coding formatter

- [ ] Spotless is bound to `validate`.
- [ ] `./mvnw spotless:apply` formats code.
- [ ] Formatting violations fail `./mvnw verify`.

### Static analysis

- [ ] SpotBugs is bound to `verify`.
- [ ] Medium-or-higher findings fail the build.
- [ ] Static-analysis output is visible in the Maven build.

### CI

- [ ] GitHub Actions starts SQL Server.
- [ ] CI bootstraps the database and applies Flyway migrations.
- [ ] CI runs `./mvnw clean verify`.
- [ ] CI passes on `main` and pull requests.

### ADR

- [ ] ADR template exists.
- [ ] ADR-0001 records the modular-monolith decision.

### SQL Server Compose

- [ ] SQL Server starts from `compose.yaml`.
- [ ] Data uses a named volume.
- [ ] Port 1433 binds only to loopback.
- [ ] Health check executes `SELECT 1`.
- [ ] Application database and login are initialized idempotently.
- [ ] Application does not use `sa`.

### Health endpoints

- [ ] `/q/health/live` reports `UP`.
- [ ] `/q/health/ready` reports `UP` while SQL Server is reachable.
- [ ] Readiness reports `DOWN` when SQL Server is stopped.
- [ ] Automated health tests pass.

### README

- [ ] Prerequisites are documented.
- [ ] First-time setup is documented.
- [ ] Build, test, run, format, and shutdown commands are documented.
- [ ] Security warning is included.

### Final exit criteria

- [ ] Clean checkout builds.
- [ ] Tests run.
- [ ] Application starts.
- [ ] Database is reachable.
- [ ] CI passes.

---

## 20. Milestone Boundary

Do not implement the following during Milestone 0:

- Flyway migrations
- application schemas
- user tables
- invitation tables
- authentication
- REST business endpoints
- message repositories
- WebSockets

The next milestone should establish:

- Flyway SQL Server integration
- migration and runtime principals
- logical schemas
- migration naming and immutability rules
- migration validation
- disposable SQL Server integration tests
- the first versioned migration
