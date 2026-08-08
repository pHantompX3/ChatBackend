# SQL Server Principals and Permissions Baseline

**Scope:** Milestone 1 database foundation  
**Last reviewed:** 2026-08-05

---

## 1. Purpose

This document defines intended SQL Server principal separation for migration and runtime access.

Current repository baseline uses:

- runtime login/user: `wl_chat_app`
- migration/admin operations: `sa` in local/dev workflows (temporary baseline for early setup)

Target separation for hardened environments:

- `messenger_migrator` for schema/migration operations
- `messenger_runtime` for runtime DML operations

---

## 2. Current Baseline (Repository)

### 2.1 Runtime principal

- Login/User name: `wl_chat_app`
- Intended use: application datasource login
- Must not be sysadmin
- Must not have unrestricted DDL privileges

### 2.2 Migration principal

- Local/dev workflows currently use `sa` for bootstrap and Flyway application.
- This is acceptable for local bootstrap convenience, not final least-privilege posture.

---

## 3. Target Principal Model

## 3.1 `messenger_migrator`

Intended capabilities:

- create/alter/drop objects required by versioned migrations
- create schemas required by migration history and domain model
- maintain Flyway history table (`platform.flyway_schema_history`)

Should not be used by runtime application traffic.

## 3.2 `messenger_runtime`

Intended capabilities:

- `SELECT`, `INSERT`, `UPDATE`, `DELETE` on application tables
- `EXECUTE` on approved stored procedures/functions if used
- metadata visibility as required for runtime operations

Must not be member of:

- `sysadmin`
- `db_owner`
- `db_ddladmin`

---

## 4. Example Grant Model (Template)

The following SQL is a template and must be reviewed before production use.

```sql
-- Runtime user data access example
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::identity TO [messenger_runtime];
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::messaging TO [messenger_runtime];
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::audit TO [messenger_runtime];

-- Optional metadata visibility
GRANT VIEW DEFINITION TO [messenger_runtime];
```

Avoid broad grants like:

```sql
ALTER ROLE db_owner ADD MEMBER [messenger_runtime];
```

---

## 5. Verification Queries

### 5.1 Check privileged role membership

```sql
SELECT rp.name AS role_name, mp.name AS member_name
FROM sys.database_role_members drm
JOIN sys.database_principals rp ON rp.principal_id = drm.role_principal_id
JOIN sys.database_principals mp ON mp.principal_id = drm.member_principal_id
WHERE mp.name IN ('wl_chat_app', 'messenger_runtime');
```

Expected:

- no row showing `db_owner` for runtime principal
- no row showing `db_ddladmin` for runtime principal

### 5.2 Check runtime DDL denial (negative test)

Execute as runtime principal:

```sql
CREATE TABLE messaging._ddl_permission_probe (
    id INT NOT NULL
);
```

Expected:

- permission denied error

### 5.3 Check runtime DML success (positive test)

Use a safe read operation:

```sql
SELECT TOP 1 name FROM sys.schemas;
```

Expected:

- succeeds

---

## 6. Milestone 1 Evidence Requirements

To satisfy Milestone 1 exit criteria, capture:

1. role-membership query results
2. runtime DDL-denial output
3. runtime datasource connectivity output
4. CI run proving migration + schema verification checks

---

## 7. Follow-up for Later Milestones

Before production rollout:

1. remove remaining migration use of `sa`
2. create dedicated `messenger_migrator` credential path in workflows
3. rotate credentials and document secret-management flow
4. validate least privilege after every migration introducing new objects
