# Backup and restore runbook

## Create and verify

Run:

```text
scripts/operations/backup-database.sh --environment rehearsal
scripts/operations/verify-backup.sh --environment rehearsal
```

The backup uses SQL Server `CHECKSUM`, compression, and AES-256 certificate encryption. The backup
principal is limited to database backup and certificate visibility. The output contains a checksum
manifest plus the password-protected certificate private key needed for recovery. Transfer the backup
and key to separately controlled off-host locations; losing the certificate or its password makes the
encrypted backup unrecoverable.

`RESTORE VERIFYONLY` is preliminary media verification. It is not proof that application data can be
recovered.

## Isolated restore drill

Run:

```text
scripts/operations/restore-database.sh \
  --environment rehearsal \
  --isolated-target wl_chat_restore_drill
```

The script refuses `wl_chat`, starts an isolated clean SQL Server, imports the recovery certificate,
verifies and restores the backup with explicit file moves, runs `DBCC CHECKDB`, applies any newer
forward migrations as the migrator, starts the compatible application image, verifies readiness, and
records durable entity/tombstone/session/Flyway evidence and elapsed time. Temporary containers and
volumes are removed after the drill; evidence remains under the ignored `backups/` directory.

For production, copy the encrypted artifact off-host on a schedule, apply retention/versioning or
immutability controls, monitor transfer and backup age, and rehearse retrieval from that actual store.
Do not treat a backup remaining only on the database host as disaster recovery.
