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
records durable entity/tombstone/session/Flyway evidence and elapsed time. To prove application-level
recovery, set all five `WL_CHAT_RESTORE_SMOKE_*` variables to a dedicated synthetic user's username,
password, conversation ID, expected message ID, and expected positive sequence. The drill then logs
in through the restored application and verifies both message history and that user's delivery/read
cursor. Supplying only some variables fails closed; omitting all of them records the application smoke
as `not_configured` and does not satisfy the Milestone 9 recovery exit criterion. Temporary containers
and volumes are removed after the drill; evidence remains under the ignored `backups/` directory.

For production, copy the encrypted artifact off-host on a schedule, apply retention/versioning or
immutability controls, monitor transfer and backup age, and rehearse retrieval from that actual store.
Do not treat a backup remaining only on the database host as disaster recovery.

## Recorded local rehearsal

On 2026-08-27 UTC, the hardened rehearsal created and verified an encrypted checksum backup, restored
it into an isolated clean SQL Server, ran `DBCC CHECKDB` and current Flyway validation, and started the
compatible hardened application. A dedicated synthetic user then authenticated through the restored
application; the expected durable message and delivery/read sequence `1` were recovered. The drill
completed in 16 seconds, below the provisional 60-minute restore objective.

This proves the local recovery mechanics and application/data compatibility. It does not satisfy a
production recovery-point objective: the rehearsal backup was created manually and remained local.
Production activation still requires a selected encrypted off-host destination, automated schedule,
retention/immutability controls, retrieval rehearsal, and acceptance or replacement of the provisional
24-hour RPO.
