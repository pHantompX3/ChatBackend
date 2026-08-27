# Rehearsal TLS material

Run `scripts/deploy/generate-rehearsal-tls.sh` to create a local CA, proxy certificate, SQL Server
certificate, and Java truststore under `generated/`. The generated directory is ignored except for its
placeholder and must never be committed.

Production deployments must use certificates issued and rotated by the deployment environment. The
rehearsal CA is not a production trust anchor.
