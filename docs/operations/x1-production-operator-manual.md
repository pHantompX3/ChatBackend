# X1 Production Operator Manual

## Status and authority

**Status:** Repository commands implemented; production-host execution and evidence pending  
**Audience:** Primary stakeholder and production-machine administrator  
**Production host:** Windows 11 Pro at reserved LAN address `192.168.0.199`  
**Governing plan:** [Milestone X1 — Production Infrastructure and Recovery Foundation](../development-guide/milestone-x1-production-infrastructure-and-recovery.md)

This manual translates the X1 plan into a human-executable sequence. The X1 guide remains
authoritative for requirements and exit criteria. This manual explains how to perform them and what
evidence to retain. Use only the named repository commands; do not invent substitutes or mark steps
complete without their required evidence.

Passing this manual produces an X1 production candidate. It does not authorize normal production use
or the `1.0.0` release; those decisions belong to X2.

## 1. Safety rules

1. Never paste passwords, tokens, private keys, recovery keys, production data, or complete private
   topology into Git, GitHub Actions logs, screenshots, tickets, or this manual.
2. Store production secrets only in the restricted production secret directory created by the X1
   bootstrap tooling. Use separate credentials for publishing images, pulling images, SQL roles,
   RabbitMQ roles, backup access, and Cloudflare Tunnel.
3. Do not expose SQL Server, RabbitMQ, ChatBackend, Docker, SSH, RDP, router administration, FTP, or
   SMB to the Internet. Cloudflare Tunnel is the only public ingress path.
4. Do not enable router DMZ, UPnP, Internet FTP, or public router administration.
5. Export configuration before router or firewall changes. Make one material change at a time and
   verify access immediately afterward.
6. Stop before any destructive action unless the target, backup, and rollback path have been
   verified. Database deletion is never an X1 setup technique.
7. Store raw evidence in access-controlled operator storage. Commit only sanitized summaries.

## 2. How to use this manual

Create a private evidence folder outside the repository, organized as follows:

```text
X1-evidence/
├── 01-host-baseline/
├── 02-network-and-dns/
├── 03-release-and-registry/
├── 04-private-stack/
├── 05-public-edge/
├── 06-backup-and-restore/
├── 07-recovery-and-capacity/
└── 08-final-manifest/
```

For each numbered procedure:

1. complete its prerequisites;
2. perform the actions in order;
3. record the date, operator, result, and sanitized evidence location;
4. resolve a failure before continuing to a dependent procedure; and
5. never mark a result as verified solely because a command exited successfully—inspect the stated
   outcome.

Use this result notation in a private operator copy:

```text
Result: PASS | FAIL | BLOCKED | NOT RUN
Date/time (UTC):
Operator:
Release/image digest, if applicable:
Sanitized evidence:
Notes or accepted risk:
```

## 3. Phase A — Production host and external-account preparation

### A1. Record the host baseline

On the Windows production machine:

1. Open **System Information** and confirm Windows 11 Pro, system model, CPU, and installed RAM.
2. Open **Disk Management** and record the system disk capacity and free space.
3. Open **Manage BitLocker** and confirm protection is enabled for the system disk.
4. Confirm the BitLocker recovery key can be located from a second device without displaying or
   copying the key into evidence.
5. Open **Date & time settings** and enable automatic time and timezone synchronization. Confirm the
   timezone is correct for Barbados.
6. Open Docker Desktop and record its version, Docker Engine version, Compose version, WSL2 backend
   setting, resource allocation, and disk-image location.
7. Run `wsl --status` and `wsl --list --verbose` in PowerShell. Record the selected production WSL
   distribution and confirm version 2.
8. Record the Windows update channel and the owner-approved maintenance/reboot procedure.

Expected result: the facts match the accepted X1 host profile, BitLocker is active, time is correct,
the production WSL distribution is explicit, and sufficient free storage exists.

Evidence: sanitized screenshots or text exports in `01-host-baseline`; never capture the BitLocker
recovery key.

### A2. Update and secure the router

Prerequisites: wired administration where practical, region-correct firmware, stable power, and an
exported router configuration.

1. Sign in to the TP-Link Archer AX73 administration interface from the LAN.
2. Export its current configuration and store it outside the production host.
3. Confirm hardware version `Archer AX73 v2.0` and current firmware.
4. Use the router's built-in update facility to install the offered stable firmware applicable to
   that exact hardware and region.
5. Do not upload firmware obtained from an unrelated regional support page.
6. After reboot, confirm Internet access, DHCP reservation `192.168.0.199`, Wi-Fi/LAN operation, and
   router-storage configuration.
7. Disable UPnP, remote administration, DMZ, and Internet FTP unless already disabled.
8. Confirm no port-forwarding rule targets `192.168.0.199`.

Stop if the firmware identity does not exactly match the hardware/region, configuration restoration
would be uncertain, or power is unstable.

Expected result: updated supported firmware, retained LAN operation, and zero inbound application
forwarding.

### A3. Finish private administration access

1. Complete Windows OpenSSH Server installation from **Optional Features**.
2. Set the SSH service to start automatically.
3. Restrict Windows Firewall SSH access to the approved private LAN source or range; do not allow a
   public profile rule.
4. From the operations Mac, test SSH to the dedicated non-shared Windows administrator account at
   `192.168.0.199`.
5. Prefer SSH key authentication. Store the private key on the operations Mac, not in the repository
   or GitHub.
6. Confirm RDP is either disabled or restricted to the private network. It is not a deployment
   dependency.
7. Confirm SSH is unreachable from an external network. Do not create an SSH router-forwarding rule.

Expected result: the operations Mac can administer the host over the LAN, while the Internet cannot.

### A4. Prepare Docker Hub access

1. Confirm `wl-chat-production` is private in Docker Hub.
2. Keep the repository capable of storing both application and migration artifacts under distinct,
   immutable tags.
3. Create a publisher token for GitHub Actions with only the permissions required to push that
   repository.
4. Create a different pull-only token for the production host if Docker Hub supports the required
   scope. Never reuse the publisher token on production.
5. Store the publisher token only as a GitHub secret and the pull credential only in the production
   host's restricted credential storage.
6. Test authentication without displaying the token. Do not place a token directly on a command
   line or in shell history.
7. Do not push a candidate until X1-B tooling has produced the release manifest and scan evidence.

Expected result: private repository exists and publisher/puller authority is separated.

### A5. Prepare Cloudflare and DNS without changing live routing

1. Confirm administrative access to both the current Hostinger DNS zone and the intended Cloudflare
   account.
2. Export or manually record every existing Hostinger DNS record, including mail-related records.
3. Add `wmkjsolutions.com` to Cloudflare using the free plan and allow Cloudflare to import records.
4. Compare imported records against the Hostinger record inventory. Correct omissions before
   changing nameservers.
5. Do not use Hostinger's **child nameserver** feature; it is unrelated to this migration.
6. Record Cloudflare's assigned authoritative nameservers.
7. Do not switch nameservers until the record comparison passes and existing website/email impact is
   understood.
8. Reserve a dedicated API hostname, recommended `chat-api.wmkjsolutions.com`, but create its Tunnel
   route only during Phase D.

Stop if existing website or mail records cannot be explained. DNS migration must not break unrelated
services.

Expected result: Cloudflare is prepared with a verified record copy and no application exposure yet.

## 4. Phase B — Repository release and deployment controls

This phase uses the committed X1 commands and release workflow.

### B1. Verify the candidate locally

1. Start from a reviewed `main` commit with a clean, understood worktree.
2. Run `./mvnw clean verify`.
3. Run the repository's Postman discovery/validation/sync checks when API or environment contracts
   changed.
4. Run SBOM, dependency, image, and secret-scanning gates defined by the release workflow.
5. Resolve failures; do not suppress a result merely to make the release pass.
6. Review `CHANGELOG.md`, architecture decisions, client responsibilities, and X1 documentation.

Expected result: all repository gates pass for one exact commit.

### B2. Create an immutable release candidate

1. Choose a semantic pre-production version according to the versioning policy.
2. Create an annotated Git tag only from the reviewed commit.
3. Trigger the hosted release workflow.
4. Approve no production deployment at this stage.
5. Confirm the workflow builds the application and migration images once, scans them, and pushes
   distinct immutable tags to `wl-chat-production`.
6. Record both registry digests, source commit, schema bounds, SBOM checksums, and workflow identity
   in the generated release manifest.
7. Confirm later deployment steps reference digests, not mutable tags such as `latest`.

Expected result: one reproducible release identity exists without production secrets entering hosted
CI.

### B3. Rehearse deployment and rollback away from production

1. Deploy the exact digests to the approved non-production hardened target.
2. Run migration preflight, migration, rollout, health, and smoke checks.
3. Roll back to the compatible previous image without rebuilding either image.
4. Repeat deployment of the original candidate.
5. Test that an incompatible or unknown schema/image pairing fails closed.
6. Preserve the sanitized manifest and results.

Expected result: deploy and rollback work twice using the same immutable artifacts.

## 5. Phase C — Private production host installation

### C1. Run the production prerequisite checker

Use `scripts/production/windows/Test-X1Prerequisites.ps1`. It must
check Windows, BitLocker, WSL2, Docker/Compose, OpenSSH, free space, time synchronization, required
Unix tools, and network assumptions without printing secrets.

Resolve every failed required check before proceeding. Warnings require an explicit operator note.

### C2. Bootstrap restricted production directories

1. Run `scripts/production/windows/Initialize-X1Host.ps1` as the designated
   administrator.
2. Confirm it creates separate locations for deployment metadata, service-specific secrets, backup
   staging, evidence, logs/status, and recovery material.
3. Inspect Windows ACLs and WSL permissions. The ordinary user and application containers must not
   gain blanket access.
4. Install only the reviewed deployment bundle and manifest—never clone the Git repository onto the
   production machine.
5. Run the bootstrap a second time and confirm it makes no unsafe or destructive changes.

Expected result: a repeatable least-privilege filesystem boundary exists outside source control.

### C3. Install production secrets

1. Generate every credential independently with sufficient randomness.
2. Install each secret in its designated restricted file; do not use one global environment file.
3. Provision separate SQL bootstrap/operator, migrator, runtime, backup, restore, and future
   monitoring authorities.
4. Provision separate RabbitMQ bootstrap/operator and ChatBackend application authorities.
5. Install the Docker Hub pull credential and, later, the Cloudflare Tunnel credential separately.
6. Install the backup archive passphrase separately from retained backup packages and manifests.
7. Record secret identifiers, owners, creation dates, and rotation status without recording values.
8. Run `scripts/production/validate-production-config.sh` and confirm it reports only presence,
   permissions, and safe metadata.

Expected result: no application container receives operator, backup, restore, registry-write, NAS,
or Cloudflare administrative authority.

### C4. Deploy the private stack

1. Transfer the signed deployment bundle through LAN SSH from the operations workstation.
2. Verify its manifest and checksums before use.
3. Authenticate Docker using the production pull-only credential.
4. Pull application and migration images by recorded digest.
5. Run `scripts/production/validate-production-config.sh`.
6. Provision SQL Server Express and RabbitMQ using bounded operator commands.
7. Run migrations with the migrator identity before starting the new application image.
8. Start the production Compose project with no public host binding for ChatBackend, SQL Server, or
   RabbitMQ.
9. Confirm NGINX and `cloudflared` share only the required private path; keep the Tunnel route
   disabled until Phase D.
10. Run runtime permission-negative, SQL TLS, queue topology, persistence, and internal health tests.
11. Inspect running container configuration for credential overexposure.
12. Revoke or rotate bootstrap credentials and prove retained bootstrap values no longer work.

Expected result: the complete stack operates privately and persists through a controlled container
restart, with least-privilege identities.

### C5. Configure Windows startup and firewall

1. Install the repository-provided Docker-readiness/start-stack scheduled task (**implementation
   pending**) to run after owner sign-in.
2. Confirm retries are bounded and failures leave a visible status record.
3. Apply `scripts/production/windows/Set-X1Firewall.ps1`.
4. Confirm SQL Server, RabbitMQ, ChatBackend, Docker, SMB, RDP, and SSH are not publicly reachable.
5. Confirm approved LAN SSH remains available before ending the local console session.
6. Restart the host normally, sign in, and confirm Docker Desktop becomes ready and the stack returns
   without manually issuing Compose commands.

Stop if the stack cannot recover reliably without automatic insecure Windows sign-in. Record a
runtime decision instead of bypassing the requirement.

## 6. Phase D — Cloudflare Tunnel, DNS, and public edge

### D1. Activate Cloudflare DNS

1. Recheck the saved Hostinger and Cloudflare DNS inventories.
2. Change the domain's authoritative nameservers at the registrar to those assigned by Cloudflare.
3. Wait for authoritative propagation; do not repeatedly edit records while caches converge.
4. Verify the existing website and any mail service from an external connection.
5. Roll back nameservers if a critical unrelated service fails and cannot be corrected promptly.

Expected result: Cloudflare is authoritative without breaking existing domain services.

### D2. Create and install the Tunnel

1. In Cloudflare Zero Trust, create a named production Tunnel dedicated to ChatBackend.
2. Select the Docker connector instructions, but do not store the displayed token in Git or evidence.
3. Install the token in the production host's restricted Tunnel secret location.
4. Configure the pinned `cloudflared` image in the production Compose overlay.
5. Route the selected public hostname to the private NGINX HTTPS service—not directly to
   ChatBackend.
6. Configure strict validation of NGINX's private certificate and expected server name. Do not enable
   an insecure origin-verification bypass.
7. Start the connector and confirm the Tunnel is healthy before enabling public traffic.

Expected result: outbound-only `cloudflared` reaches NGINX through the private Compose network.

### D3. Enforce and verify the public edge

1. Configure NGINX's production hostname allow-list, request/body limits, login throttling, WebSocket
   handshake limits, forwarding rules, and security headers using repository-owned configuration.
2. Expose only minimal public liveness. Keep detailed readiness private.
3. From a device on cellular data or another external network, verify trusted HTTPS and WSS using
   the production hostname.
4. Run the production Postman environment smoke flow with purpose-created test identities.
5. Verify invalid origins, malformed authentication, oversized requests, excessive handshakes, and
   direct internal-service attempts are rejected as designed.
6. Scan the home public address externally and confirm no production origin or administrative port
   is reachable.
7. Stop and restart `cloudflared`; verify service recovery and client reconnection behavior.
8. Exercise Tunnel credential rotation and prove the retired credential fails.

Expected result: the hostname works through Cloudflare, while the origin has zero public inbound
ports. Cloudflare is explicitly the public TLS termination boundary; NGINX validates the private
connector-to-origin TLS connection.

## 7. Phase E — Off-host backup and recovery

### E1. Configure the router share

1. Enable authenticated Samba/SMB storage on the Archer for the attached drive.
2. Disable Internet FTP and anonymous access.
3. Create a dedicated backup account and ChatBackend backup directory where supported.
4. From Windows, connect using SMB and confirm authenticated create/read/delete behavior in that
   directory.
5. Verify the negotiated protocol is SMB 2 or SMB 3. Do not enable SMB1.
6. Confirm the production backup job can write while the ChatBackend application cannot browse the
   share.
7. If authenticated SMB 2/3 cannot be made to work, stop and document the result. Plain FTP requires
   a separate residual-risk decision and is not the default fallback.

Expected result: an authenticated LAN-only off-host destination is available.

### E2. Run the complete backup lifecycle

1. Invoke `scripts/production/backup-production.sh` using the bounded SQL
   backup identity.
2. Confirm SQL Server creates an uncompressed `BACKUP DATABASE ... WITH CHECKSUM` artifact.
3. Run `RESTORE VERIFYONLY` before packaging.
4. Compress and encrypt the `.bak` externally using the approved free tool and strong passphrase.
5. Generate the authenticated manifest containing safe artifact identity, timestamp, release/schema
   identity, sizes, and checksums.
6. Transfer the encrypted package and manifest to the router share.
7. Verify the destination checksum after transfer.
8. Apply bounded retention without deleting the only verified recovery point.
9. Confirm overdue or failed backup/transfer status is visible to the operator.

Expected result: the off-host location contains a verified encrypted package, not plaintext database
backup material.

### E3. Retrieve and restore independently

1. Select a retained off-host package rather than the local staging copy.
2. Retrieve it to an isolated restore location.
3. Verify its authenticated manifest and checksum before decryption.
4. Decrypt and decompress it using separately stored recovery material.
5. Restore into an isolated SQL Server Express instance using the guarded restore procedure.
6. Run `DBCC CHECKDB` and application-level identity, conversation, message, delivery, and audit
   verification queries.
7. Record elapsed time and calculate the represented recovery point.
8. Destroy the isolated restored environment only after evidence is retained; do not alter the live
   production database.

Acceptance targets: no more than 24 hours of recoverable data loss and no more than 60 minutes for
the documented ordinary restore path, unless the stakeholder records a revised proportional target.

### E4. Rehearse total-host loss

Perform a tabletop or isolated-machine rehearsal that starts only with documented installation
media/instructions, separately held keys, the deployment manifest, registry artifacts, and the
off-host backup. Prove that recovery does not depend on files existing solely on the production SSD
or in this chat history.

## 8. Phase F — Recovery, characterization, and handoff

### F1. Test restart and failure recovery

1. Perform a controlled Windows restart and validate the full service chain.
2. Simulate the documented power-loss recovery procedure without intentionally risking filesystem
   damage when a tabletop is the proportionate choice.
3. Verify SQL integrity, RabbitMQ durable topology/messages, application data, HTTPS/WSS, audit
   delivery, and backup scheduling afterward.
4. Exercise RabbitMQ interruption: the application should become ready-but-degraded as designed,
   bounded fallback should not grow without limit, and recovery should drain safely.
5. Exercise diagnostic-bundle creation and confirm it contains no secrets or message bodies.

### F2. Characterize the production host

1. Seed only synthetic production-representative data.
2. Run the repository's approved workload against the public edge and separately record internal
   resource behavior.
3. Capture CPU, memory, disk, database size, connection-pool, RabbitMQ backlog, latency, failure, and
   WebSocket evidence.
4. Confirm the SQL Express database remains comfortably below its edition size limit.
5. Record proportional thresholds for approximately 5–10 users; do not invent a public SLA.
6. Remove synthetic accounts/data only through an approved cleanup procedure.

### F3. Assemble the X1 evidence manifest

Record sanitized references for:

- reviewed source commit and annotated release tag;
- application and migration image digests;
- SBOM, dependency, image, and secret scan results;
- schema version and rollback bounds;
- host baseline, BitLocker, Docker/WSL, startup, firewall, and restart evidence;
- Cloudflare hostname, Tunnel identity, public TLS, external port scan, and rotation evidence;
- SQL/RabbitMQ principals, permissions, TLS, persistence, integrity, and degradation evidence;
- off-host backup identity, retention, retrieval, restore, RPO, and RTO evidence;
- characterization results, diagnostics, unresolved risks, owners, and review dates; and
- documentation, threat-model, changelog, and client-responsibility reconciliation.

Do not include secret values, raw production logs, complete private topology, database content, or
private keys.

### F4. Make the X1 handoff decision

1. Review every X1 exit criterion against evidence, not recollection.
2. Mark each item passed, blocked, or risk-accepted with owner and review date.
3. Confirm this manual and the governing X1 guide match the deployed candidate.
4. Produce the X2 handoff package.
5. Describe the result as a **secure and recoverable production candidate**, never as production
   approval.

## 9. Operator intervention map

| Task | Can be prepared in repository | Requires production host/account | Requires external network |
|---|---:|---:|---:|
| Compose, NGINX, SQL/Rabbit scripts, Windows tooling | Yes | Verification | No |
| Release workflow and manifest | Yes | Pull/deploy verification | Docker Hub |
| Host ACL, firewall, startup, reboot | Tooling only | Yes | No |
| Cloudflare/DNS/Tunnel | Templates only | Connector installation | Yes |
| Public HTTPS/WSS and port scan | Test tooling only | Running target | Yes |
| Router SMB backup | Tooling only | Yes | LAN/router |
| Backup/restore scripts | Yes | Authoritative verification | LAN/router |
| Capacity and recovery evidence | Test tooling only | Yes | Public edge for end-to-end test |

## 10. Current operator preparation checklist

The following can be completed before repository implementation finishes:

- [ ] Preserve the BitLocker recovery key separately and verify it is retrievable.
- [ ] Update the Archer firmware safely and retain a configuration export.
- [ ] Confirm DHCP reservation `192.168.0.199` after the router update.
- [ ] Complete LAN-only OpenSSH setup and test it from the operations Mac.
- [ ] Confirm Docker Desktop starts at owner sign-in and uses WSL2.
- [ ] Record Docker/Compose/WSL versions and the Docker disk-image location.
- [ ] Confirm `wl-chat-production` remains private.
- [ ] Prepare separate Docker Hub publisher and production pull credentials.
- [ ] Inventory Hostinger DNS records and prepare the Cloudflare zone without switching nameservers.
- [ ] Select the dedicated API hostname.
- [ ] Configure authenticated SMB storage and verify SMB 2/3 without exposing it publicly.
- [ ] Create the private `X1-evidence` structure outside the repository.

Do not yet:

- switch DNS nameservers before record comparison;
- create inbound router forwarding;
- place Cloudflare, Docker Hub, SQL, RabbitMQ, backup, or NAS secrets in Git;
- publish unscanned images or use mutable tags as release authority;
- expose the private stack; or
- bootstrap production data before the immutable deployment and recovery paths pass.
