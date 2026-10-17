# Local container secrets

This directory is bind-mounted read-only at `/run/secrets` and its contents are ignored by Git.
Fixture mode requires no files. Each SQL environment supplies its own least-privilege password file,
for example `local-monitor-password` and `production-monitor-password`. X4 production onboarding
supplies:

- `production-monitor-password`: the projection-only `wl_chat_monitor` password, with no trailing content;
- `sql-ca.pem`: the private SQL Server CA certificate used by Node.js to verify the server.

Never place either value in the image, Compose file, browser configuration, or repository.
