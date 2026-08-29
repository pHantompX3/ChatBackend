# SQL Server principals and permissions

**Scope:** Milestone 9 hardened single-instance deployment

**Last reviewed:** 2026-08-27

## Authority model

| Principal | Lifetime | Authority |
|---|---|---|
| Database operator | Interactive/approved provisioning only | Creates database/logins/users, assigns migrator/backup/restore roles, provisions backup certificate |
| `messenger_migrator` | One-shot migration job | Flyway history, schema DDL/security changes, migration-time DML |
| `wl_chat_app` | Long-running application | Explicit table-level DML needed by repository code; no DDL, role management, Flyway history mutation, audit deletion, or backup/restore |
| `wl_chat_backup` | Scheduled backup job only | `db_backupoperator` on `wl_chat` and `VIEW DEFINITION` on the backup certificate |
| `wl_chat_restore` | Guarded isolated drill/recovery only | `dbcreator` plus backup-certificate visibility; absent from all long-running containers |

The runtime principal is intentionally named `wl_chat_app` for compatibility with immutable early
migrations. It is not a fixed database-role member after the hardening migration. The application
container receives only this runtime credential.

## Provision and migrate

`scripts/database/provision-hardened-principals.sh` is the operator boundary.
`scripts/database/migrate-hardened.sh` receives only the migrator credential. On a clean database,
immutable migration `V20260808111000` temporarily assigns broad data roles, so the supported sequence
is:

1. provision with `WL_CHAT_PRESERVE_RUNTIME_FIXED_ROLES=true`;
2. migrate through `20260814100000` as `messenger_migrator`;
3. provision with `WL_CHAT_PRESERVE_RUNTIME_FIXED_ROLES=false`;
4. migrate to latest, applying the forward-only table grants and denials;
5. run `scripts/database/verify-hardened-permissions.sh --scenario clean`.

An upgrade begins at step 3 and uses `--scenario upgrade-milestone-8`. Never edit an applied Flyway
migration to simplify this sequence.

## Runtime grants

Migrations `V20260826100000` and `V20260826100500` remove fixed data roles and enumerate the
application's table access. They explicitly deny:

- DDL and schema alteration;
- updates/deletes to `platform.flyway_schema_history`;
- deletes from durable audit history;
- access beyond the repository's current identity, conversation, message, delivery/read, throttle,
  and audit operations.

Every future migration that introduces a table or stored routine used at runtime must add its exact
grant in that same new migration and extend the positive/negative permission verification.

## Backup encryption

`scripts/database/provision-backup-encryption.sh` runs as the operator, creates the master certificate,
exports its password-protected private key into the controlled backup volume, and grants only
certificate visibility to backup/restore principals. The certificate and private-key password must be
kept for as long as any dependent backup is retained and stored separately from the encrypted backup.

## Verification

The automated permission script proves runtime reads while rejecting runtime DDL, Flyway-history
mutation, and audit deletion. It also proves the migrator can create a transactional DDL probe and
read Flyway history. Backup creation, `RESTORE VERIFYONLY`, and an isolated clean restore are covered by
the operations scripts and runbook; the 2026-08-27 rehearsal additionally proved restored
authentication, message history, and delivery/read cursors through the application.

The local CI workflow exercises the clean two-stage path. Remote migration is manual,
environment-protected, and migrator-only; it does not accept or use `sa`.
