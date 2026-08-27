# Rehearsal TLS material

Run `scripts/deploy/generate-rehearsal-tls.sh` to create a local CA, proxy certificate, SQL Server
certificate, and Java truststore under `generated/`. The generated directory is ignored except for its
placeholder and must never be committed.

The generator writes the public rehearsal CA as both `ca.crt` and the identical PEM-encoded `ca.pem`.
Use `ca.pem` when a client such as Postman filters trusted-CA imports by filename extension. Neither
public CA file has a passphrase, and clients must not receive the generated private keys.

Production deployments must use certificates issued and rotated by the deployment environment. The
rehearsal CA is not a production trust anchor.
