# Production secret-file contract

This directory documents filenames only. Real files live under the restricted production secret
root and are never copied into this repository.

Required files:

- `sql-sa-password`: SQL bootstrap value used only by the SQL container and bounded operator tasks;
- `application.properties`: runtime-only Quarkus properties containing the runtime SQL and RabbitMQ
  credentials and the production origin allowlist;
- `flyway.conf`: migrator-only JDBC URL, username, password, schemas, and history-table settings;
- `sql-backup-password`, `sql-restore-password`, and `sql-maintenance-password`: bounded SQL
  operational identities;
- `rabbitmq-operator-password`: RabbitMQ bootstrap/operator password;
- `rabbitmq-runtime-password`: application RabbitMQ password used when provisioning the runtime user;
- `cloudflared-credentials.json`: credentials for the single production Tunnel;
- `origin-ca.pem`, `origin-ca.key`, `origin.crt`, and `origin.key`: private
  connector-to-NGINX trust material;
- `sql-ca.pem`, `sql-ca.key`, `sqlserver.crt`, `sqlserver.key`, `sql-truststore.p12`, and
  `sql-truststore-password`: private SQL transport trust material;
- `backup-archive-password`: passphrase for the externally encrypted package;
- `backup-manifest-signing-key.pem`: private key used only to sign backup manifests;
- `backup-manifest-public-key.pem`: separately recoverable public key used to verify manifests; and
- `release-public-key.pem`: pinned public key used to verify CI-produced release manifests; and
- `nas-credentials`: Windows/SMB credential reference or protected mount configuration, never a
  command-line password.

Each consumer receives only its listed files. The host bootstrap applies restrictive Windows ACLs;
the production preflight rejects missing, empty, broadly readable, or repository-resident secrets.
