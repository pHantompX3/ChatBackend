# Milestone X1 — Production Infrastructure and Recovery Foundation

## Status

**Status:** Audited and ready for implementation; X1-A begins with the environment discovery in
section 2.2
**Parent program:** [Milestone X — Production Activation](milestone-x-production-activation.md)  
**Successor increment:** [Milestone X2 — Monitoring and Production Acceptance](milestone-x2-monitoring-and-production-acceptance.md)  
**Last refined:** 2026-08-30 after the Windows-host implementation-readiness audit

## 1. Purpose and boundary

Milestone X1 converts the Milestone 9 hardened local rehearsal into a secure, repeatable, and
recoverable production candidate. It owns the production host, public edge and private internals,
certificates, secrets, immutable release process, SQL Server and RabbitMQ operations, off-host
backups, rollback, incident foundations, capacity characterization, ownership, and evidence manifest.

X1 does not approve normal production use or release version `1.0.0`. Its output is a production
candidate that can be deployed and recovered safely. Monitoring, ChatMonitor, operational alerting,
final acceptance exercises, and the production-release decision belong to X2.

## 2. Entry decisions and prerequisites

Before work begins, resolve and record the applicable decisions using the decision-record template in
the [Milestone X umbrella guide](milestone-x-production-activation.md):

1. production host hardware, physical location, operating system, patch owner, power protection,
   time synchronization, storage layout, and expected availability;
2. public Internet/router topology, ISP/CGNAT and inbound-port capability, firewall owner, DNS owner,
   certificate issuer, renewal owner, and private administrative/monitoring sources;
3. OCI registry, immutable image promotion policy, deployment approver, rollback owner, and protected
   evidence location;
4. production SQL Server Express placement, storage, maintenance owner, capacity limits, and supported backup
   tooling;
5. secret-delivery mechanism, privileged identity inventory, credential-rotation owner, and exposure
   response;
6. client-side-encrypted off-host backup destination, retention policy, recovery owner, RPO, and RTO;
7. release workload model, capacity limits, maintenance window, and X1 acceptance owner.

ADR-0017, ADR-0019, the Milestone 9 guide, threat model, and operations runbooks remain authoritative
for their existing decisions. X1 must document environment-specific values without committing
secrets, raw private topology, or production data.

### 2.1 Confirmed production-host profile

The following stakeholder-supplied facts are accepted inputs rather than unresolved placeholders:

| Concern | Accepted value |
|---|---|
| Host | Lenovo ThinkCentre M70q Gen 2, 11th-generation Intel Core i5-11400T, 6 cores/12 threads, 32 GB RAM |
| Host OS | Windows 11 Pro, retained as the production host OS |
| Storage | One approximately 477 GB system disk with more than 400 GB currently free; BitLocker enabled |
| Container runtime | Docker Desktop using the WSL2 backend |
| Availability | Intended to remain powered on continuously; no public SLA |
| Power | No UPS; the owner accepts outage and manual intervention after power loss |
| Restart | Docker should start at owner sign-in and Compose services should recover through `unless-stopped`; unsafe automatic Windows sign-in is not required |
| Addressing | DHCP reservation `192.168.0.199` |
| Administration | Project owner; local console, Windows OpenSSH/SSH, and Remote Desktop may be used only through private administrative paths |
| Users | Approximately 5–10 users; current payload is text messaging only |
| Maintenance | Owner-selected maintenance windows; automatic host updates/restarts are acceptable when recovery checks follow |
| Artifact delivery | Production receives reviewed artifacts and deployment metadata; it does not require a repository checkout |
| Registry | Docker Hub Personal's single free private repository is the selected OCI distribution mechanism |
| Registry repository | Private repository `wl-chat-production` created; release authentication and token scopes remain to be installed and verified |
| Public address | External lookup returns shared public-looking `196.3.x.x`; the Archer WAN is private `192.168.100.7/24`, and the Huawei Internet WAN is in carrier-shared `100.64.0.0/10`. IPv4 CGNAT is confirmed, so ordinary inbound IPv4 forwarding cannot work. |
| IPv6 | Huawei Internet WAN reports a global `2602:fe45:b00::/48`-family address, but the external client test exposes no IPv6; prefix delegation through the Archer, production-host addressing, firewall policy, reachability, and client compatibility are unproven |
| ISP edge | Digicel-provisioned Huawei EchoLife HG8245W5-6T GPON ONT/router, hardware `1A3D.A`, software `V5R020C00S410`; Internet service is routed PPPoE with NAT enabled and provider VLAN/service bindings |
| DNS administration | Existing Hostinger DNS zone is accessible and permits ordinary DNS records; child nameserver delegation is not required |
| Router | TP-Link Archer AX73 v2.0 at firmware `1.1.2 Build 20250210 rel.53421`; the router reports `1.3.1 Build 20260430 rel.48448` as current |
| Router storage | Archer AX73 advertises Samba for Windows, Local FTP, and Internet FTP; authenticated Samba setup and protocol verification remain X1-A work |
| Remote administration | Windows OpenSSH Server installation is in progress; LAN-only SSH from the operations Mac remains to be verified |
| Recovery material | BitLocker recovery key is stored separately from the production host |

The single disk and absence of a UPS are accepted proportional constraints, not hidden availability
guarantees. Off-host recovery must therefore remain mandatory. X1 must record the BitLocker recovery
key location outside the machine and prove that a normal reboot and a power-loss recovery can be
completed without disabling encryption.

Before opening Internet ingress, update the Archer AX73 through its own region-correct administration
interface to the offered `1.3.1 Build 20260430 rel.48448` or a later applicable stable release. Export
the router configuration first, use a wired administrator connection, verify the hardware/region
match, prevent interruption during the update, and record the post-update version and configuration
checks. The published 1.3.1 release improves stability/security and fixes Access Control whitelist
and OpenVPN behavior; it cannot be downgraded to the preceding release.

### 2.2 Accepted zero-cost decisions and implementation discovery

X1 introduces no paid software, certificate, DNS, VPN, backup, or database requirement. The following
directions are accepted. X1-A must discover and record the remaining environment facts before the
affected control is enabled; they do not block implementation from beginning.

The selections rely on the published capabilities of
[SQL Server 2022 Express](https://learn.microsoft.com/en-us/sql/sql-server/editions-and-components-of-sql-server-2022),
[Cloudflare Tunnel](https://developers.cloudflare.com/tunnel/),
[Hostinger DNS management](https://www.hostinger.com/support/1583249-how-to-manage-dns-records-at-hostinger/),
and [Cloudflare's Tunnel/media boundary](https://developers.cloudflare.com/cloudflare-one/faq/cloudflare-tunnels-faq/). The
release layout must also remain inside Docker Hub Personal's documented
[one-private-repository allowance](https://docs.docker.com/docker-hub/usage/).

| ID | Required decision | Acceptance evidence |
|---|---|---|
| X1-D01 | Use Cloudflare Tunnel for the current text/API/WebSocket workload under ADR-0020. Route a dedicated API hostname to private NGINX through outbound-only `cloudflared`; open no WAN ports. Cloudflare is not adopted for media storage. | Cloudflare/DNS configuration, pinned connector, external HTTPS/WSS tests, connector recovery/rotation, and proof of zero public origin ports |
| X1-D02 | Use the free SQL Server 2022 Express edition. Developer edition remains rehearsal-only. Do not introduce a paid SQL edition, TDE, SQL backup encryption, or SQL-native backup compression. | Clean deploy, migrations, permissions, uncompressed checksum backup, `RESTORE VERIFYONLY`, isolated restore, and capacity evidence on the exact Express image/build |
| X1-D03 | Use the router-provided network drive as the proportional off-host destination. Discover its actual protocol and require SMB 2/3 when available. Compress and encrypt the verified `.bak` on the production host before transfer, and keep its recovery passphrase separately. | Share/protocol record; compressed/encrypted-package transfer; retained-copy listing; checksum after retrieval; decrypt/decompress and isolated-restore proof; recorded acceptance of deletion/no-snapshot risk |
| X1-D04 | Use Cloudflare-managed public TLS for the Tunnel hostname and authenticated private TLS from `cloudflared` to NGINX. No ACME listener or public origin certificate is required. | Public trust/hostname evidence, strict origin route, connector-to-NGINX trust, certificate inspection, and recovery exercise |

Plain FTP is rejected when the router offers SMB 2/3 because FTP exposes reusable share credentials.
If FTP is the router's only usable service, client-side encryption still protects backup content but
does not protect the FTP credential on the LAN; use requires a separately recorded residual-risk
acceptance. X1 does not require snapshots, immutability, a commercial NAS, or cloud backup. The owner
accepts that an actor with deletion authority on the router drive could remove retained copies.

### 2.3 Accepted release and deployment model

`main` remains development-latest. Production is selected by an annotated semantic-version release
tag and a protected GitHub `production` environment rather than by automatically deploying every
merge or maintaining a second long-lived code branch. The stale remote `prod` branch and the local
`production` hook placeholder are not release authorities and must be retired or clearly archived
when the real workflow is introduced.

The accepted control flow is:

```text
reviewed main commit
  -> deliberate annotated vX.Y.Z tag
  -> hosted CI verify/build/SBOM/scan
  -> push app + migration images once under distinct tags in one private Docker Hub repository
  -> record immutable digests and signed release manifest
  -> protected production-environment approval
  -> self-hosted runner on the development/operations workstation
  -> SSH transfer of the reviewed deployment bundle to 192.168.0.199
  -> production host pulls images by digest
  -> migration, rollout, smoke, observation, and evidence upload
```

The production machine receives the deployment bundle, manifests, configuration templates, and image
digests but no Git checkout. Production secrets stay on the production machine. GitHub-hosted jobs
must never receive SQL operator, backup, restore, NAS, Windows administrator, or production private-key
credentials. If the operations workstation is offline, deployment waits; the running production stack
is unaffected.

### 2.4 Windows/WSL2 execution-plane contract

X1 must provide and verify one Windows-specific production runbook and thin PowerShell entrypoints for
host tasks. Bash deployment/database scripts execute inside one named, documented WSL2 distribution;
PowerShell owns Windows Task Scheduler, firewall, Docker readiness, SSH, host restart, and path/ACL
integration.

Required implementation outputs are:

1. a prerequisite checker for Windows version, BitLocker, WSL2 distribution, Docker Desktop/Compose,
   OpenSSH, free storage, time synchronization, and required Unix utilities;
2. an idempotent host bootstrap that creates explicit restricted directories outside the repository,
   applies Windows and WSL ownership, and installs only reviewed deployment metadata;
3. a Docker-readiness/start-stack task triggered after the owner signs in, with bounded retries and a
   visible failure record; no unsafe automatic login;
4. scheduled PowerShell wrappers for backup, transfer, retention, certificate renewal, and status
   checks that invoke the canonical WSL scripts without placing secrets on command lines;
5. Windows Firewall rules allowing no public inbound service and allowing administrative/monitoring
   paths only from selected private sources; `cloudflared` reaches NGINX through private networking;
6. explicit Docker Desktop named-volume/VHDX location, backup boundary, capacity thresholds, and
   recovery procedure; named volumes are not mistaken for independently recoverable storage;
7. a controlled shutdown/reboot procedure and a cold-reboot test; and
8. an unexpected-power-loss recovery test or tabletop that verifies Docker startup, Compose recovery,
   SQL integrity, RabbitMQ persistence, HTTPS/WSS, and durable application data.

If Docker Desktop cannot meet the accepted sign-in/startup behavior reliably on this host, X1 pauses
and records a runtime decision instead of weakening the test or enabling insecure automatic sign-in.

## 3. Production Infrastructure and Recovery Workstreams

1. Provision private application, SQL Server, and RabbitMQ networks with only the authenticated
   HTTPS/WSS NGINX edge reachable from the Internet.
2. Replace rehearsal certificates with trusted, automatically renewed certificates appropriate to
   the selected network exposure.
3. Deliver runtime/operator credentials from the selected secret mechanism; keep them out of images,
   source control, deployment logs, and long-running containers that do not need them.
4. Promote reviewed immutable image digests and exercise migration-before-rollout and schema-aware
   rollback in the selected environment.
5. Schedule checksum backups, package them with external compression and client-side encryption,
   enforce bounded retention,
   monitor age and transfer, and
   perform a timed isolated restore from the retrieved artifact.
6. Produce the production endpoints, evidence, and safe integration boundary required by the
   [X2 monitoring and acceptance guide](milestone-x2-monitoring-and-production-acceptance.md).
7. Run characterization and regression tests against production-representative resources and data,
   then record approved thresholds and capacity limits.
8. Complete the X1 threat-model review, recovery exercise, and production-candidate checklist; final
   incident/alert exercises and the deliberate `1.0.0` decision remain X2 responsibilities.

The detailed contracts below refine these workstreams. They extend rather than replace the accepted
single-host decision in
[`ADR-0017`](../architecture/decision/ADR-0017-harden-single-instance-deployment.md), the rehearsal
mechanics in the
[`Milestone 9 guide`](milestone-9-operational-hardening-step-by-step.md), and the canonical operations
runbooks under [`docs/operations`](../operations). When a host-specific choice is not yet available,
the contract states the required decision record, recommended default, and acceptance evidence rather
than inventing an environment value.

### 3.1 Production host and service-lifecycle contract

#### 3.1.1 Required host decision record

Before provisioning, record:

| Decision | Required detail |
|---|---|
| Hardware | CPU model/cores, RAM, system disk, data disk, expected useful life, and spare capacity |
| Platform | Host operating system, supported version, architecture, patch channel, and licence |
| Ownership | Physical location, administrator, recovery contact, and who can power or unlock it |
| Power | UPS availability, automatic shutdown behavior, restart after power restoration, and test date |
| Time | NTP source, timezone, clock-drift monitoring, and UTC handling |
| Runtime | Docker Engine and Compose versions, installation source, update owner, and support policy |
| Storage | Filesystems, mount points, encryption-at-rest decision, capacity, and failure replacement path |
| Maintenance | Patch window, reboot procedure, pre-checks, post-checks, and rollback owner |

The recommended personal-use baseline is a supported long-term-support host OS, automatic security
updates with a controlled reboot window, full-disk encryption where unattended restart requirements
permit it, a UPS when the host is expected to remain available, and at least 25% free storage after
the initial production dataset and recovery reserve are included. Final values require the actual
machine.

#### 3.1.2 Filesystem and authority layout

Production paths must be explicit, absolute on the host, owned by dedicated administrative or
service identities, and excluded from source control. The implementation guide should map the
environment's actual paths to these logical responsibilities:

```text
production-root/
├── compose/                 # reviewed Compose and non-secret deployment metadata
├── secrets/                 # service-specific mounted secrets; owner/read scope minimized
├── certificates/
│   ├── edge/                # HTTPS/WSS certificate and key
│   ├── sql-client-trust/    # trust material delivered only to SQL clients
│   └── sql-server/          # SQL Server certificate/key where host-managed
├── data/
│   ├── sql/                 # SQL data, log, and tempdb locations or volume mappings
│   ├── rabbitmq/            # broker durable state
│   └── application/         # only explicitly required application state
├── backups/
│   ├── staging/             # bounded temporary backup staging
│   └── manifests/           # checksums and transfer/verification status
├── logs/                    # host-owned service logs when not held in managed volumes
└── evidence/                # sanitized deployment, recovery, and verification records
```

The Windows default production root is `C:\ProgramData\WLChat`, reached from the named WSL
distribution through its corresponding `/mnt/c/ProgramData/WLChat` path only by the reviewed wrappers.
The implementation must create `compose`, `secrets`, `certificates`, `backups`, `logs`, and `evidence`
under that root with explicit Windows ACLs. Repository worktrees, Downloads, Desktop, user profile
folders, and Docker build contexts are prohibited secret or recovery locations.

SQL and RabbitMQ durable data remain Docker named volumes inside Docker Desktop's managed WSL storage
unless testing proves a safer supported mapping. Record the exact Docker Desktop disk-image location,
maximum size, free-space thresholds, export/recovery behavior, and the fact that it shares the single
physical system disk. Backup staging and manifests use the protected host root and are removed only
after verified off-host transfer. The named volumes are operational persistence, not disaster recovery.

Do not grant one long-running container access to the whole production root. NGINX receives only its
edge certificate material and configuration; ChatBackend receives only runtime application and SQL
trust material; SQL Server receives its data, backup staging, and SQL certificate material; RabbitMQ
receives only its broker data and credential/certificate inputs. Operator, migrator, backup, restore,
and monitor credentials remain absent from ChatBackend.

#### 3.1.3 Boot, restart, and shutdown behavior

The production service manager must:

1. start Docker before the production Compose project;
2. preserve SQL Server and RabbitMQ data through container replacement;
3. use bounded health-based dependency sequencing rather than assuming process start means ready;
4. restart failed long-running services according to an explicit policy that avoids infinite rapid
   crash loops;
5. never run bootstrap, migration, restore, or destructive maintenance automatically at every host
   boot;
6. expose restart counts and last-start time to monitoring;
7. stop accepting edge traffic before an intentional shutdown where practicable; and
8. allow SQL Server and RabbitMQ to flush durable state within bounded shutdown timeouts.

Auto-start must be tested after a cold host reboot and after an unexpected power-loss simulation that
does not endanger the hardware. Acceptance requires application readiness, WebSocket handshake,
RabbitMQ recovery, SQL consistency checks appropriate to the event, persistent data verification,
and no operator credential left in the running service environment.

#### 3.1.4 Host maintenance and capacity

Define warning and critical thresholds for system disk, SQL data/log/tempdb, RabbitMQ, backup staging,
container layers, logs, and future media storage separately. Cleanup must target known disposable
artifacts and never use an unbounded recursive deletion against a broad production path. Before OS,
Docker, firmware, or storage maintenance:

1. confirm a recent verified off-host backup and recovery key;
2. record current image digests, Flyway version, health, and monitor state;
3. pause deployments and scheduled maintenance that could overlap;
4. perform the smallest reviewed change;
5. verify the complete HTTPS/WSS and durable-data path afterward; and
6. record any new residual risk or changed capacity.

### 3.2 Network, ingress, and administrative-access contract

#### 3.2.1 Target traffic model

The default single-host traffic model remains:

```text
remote Internet client devices
        │ HTTPS/WSS
        ▼
Cloudflare public edge
        │ outbound-established Tunnel
        ▼
cloudflared ── private authenticated HTTPS ──> NGINX edge
        │ private application port
        ▼
one ChatBackend instance
        ├── private SQL TLS ──> SQL Server
        └── private AMQP ─────> RabbitMQ

operator workstation
        ├── HTTPS/WSS synthetic checks ──> NGINX
        ├── approved host/container monitoring ──> production agent
        └── read-only SQL TLS ──> monitoring projection only
```

No production-origin port is published to the Internet. `cloudflared` establishes outbound Tunnel
connections and reaches NGINX only through the private Compose network.
SQL Server, RabbitMQ AMQP,
RabbitMQ management, ChatBackend's internal HTTP port, and container-engine control interfaces must
not be publicly reachable.

#### 3.2.2 Firewall and reachability matrix

The implementation guide must replace placeholders with exact addresses/CIDRs and retain the tested
rule inventory:

| Source | Destination | Service | Default disposition |
|---|---|---|---|
| Internet clients | Cloudflare public edge | HTTPS/WSS | Allow through the dedicated hostname |
| `cloudflared` service | NGINX | private authenticated HTTPS | Allow through the dedicated Compose network |
| Any source | ChatBackend direct port | internal HTTP/WSS | Deny |
| Internet/unapproved LAN devices | SQL Server | TCP 1433 or selected port | Deny |
| Internet/unapproved LAN devices | RabbitMQ AMQP/management | selected ports | Deny |
| ChatBackend network/identity | SQL Server | SQL TLS | Allow |
| ChatBackend network/identity | RabbitMQ | AMQP/TLS as selected | Allow |
| Operator workstation fixed address/range | monitoring SQL projection | SQL TLS | Allow privately |
| Operator workstation | monitoring agent/API | selected private port | Allow |
| Approved administrator address only | host administration | selected SSH/management path | Allow |
| Any workload | Docker socket/API | engine control | Deny unless explicitly required |

Use a DHCP reservation or static assignment for the production host and monitoring workstation where
firewall rules depend on their addresses. Administrative access should use key-based authentication,
an allowlisted source, no direct privileged login, and logged elevation. If remote access is required,
prefer a private VPN-style path over publishing administrative services.

#### 3.2.3 Accepted client-access model

Production follows ADR-0019:

- **public remote access:** native mobile, web, and other compatible clients reach only the NGINX
  HTTPS/WSS edge;
- **private internals:** ChatBackend, SQL Server, RabbitMQ, administration, monitoring SQL, and Docker
  control are not Internet-reachable;
- **initial client trust:** normal user authentication and server-side authorization are
  authoritative; the server does not attest exact client software; and
- **future required hardening:** IE-01 adds native per-installation identity and gateway mTLS; IE-02
  adds mobile-authorized browser pairing; IE-03 adds the official web companion after both backend
  foundations are accepted.

NGINX must not treat Origin, CORS, user-agent, client labels, custom headers, or an embedded shared
secret as client authentication. Public/delegated OAuth client registration remains outside X1.

The current supported production-candidate client matrix is non-browser HTTP/WebSocket clients and
operator integration tools using the ordinary authenticated contract. X1 does not claim that an
official browser or mobile client exists. Any browser Origin configured during X1 is a named test
origin, not evidence of official-client identity.

#### 3.2.4 Network acceptance evidence

Retain sanitized port scans from Internet and LAN test positions, effective router and host firewall
rules, Docker published-port inspection, DNS resolution, trusted proxy configuration,
client-IP/trace forwarding checks, and proof that the origin has no public inbound port while direct
SQL/RabbitMQ/ChatBackend/administrative access fails. Exercise authentication throttling, bootstrap
closure, session revocation, HTTP/WSS limits, and common malformed traffic through the public edge.
Evidence must not reveal reusable credentials or unnecessarily publish private topology.

#### 3.2.4.1 X1-A public-ingress and hostname walkthrough

The implementation must walk the owner through these steps and record sanitized outcomes:

1. Read the router's WAN IPv4 address and compare it with an external public-IP lookup. A mismatch,
   or a WAN address in a private/shared range, triggers a CGNAT/ISP investigation before forwarding.
2. Confirm that `192.168.0.199` remains reserved for the production host. It is the LAN destination,
   never a public DNS value.
3. Export and verify every existing Hostinger DNS record before changing authoritative nameservers;
   the existing website remains hosted at its current origin.
4. Onboard the zone to Cloudflare DNS and create only the dedicated Tunnel hostname. Do not publish
   the production origin through `A`/`AAAA` records and do not use DuckDNS.
5. Install the pinned `cloudflared` connector with its restricted credential and route only to
   private NGINX. Open no ONT, Archer, or Windows public inbound port.
6. Verify Cloudflare public TLS and authenticated private TLS between `cloudflared` and NGINX.
7. Test HTTPS and WSS from a cellular or other
   off-LAN network. Also prove that every internal/admin port is unreachable externally.

CGNAT remains the evidence for ADR-0020; it is not bypassed through router rules.

The completed comparison confirms that the Archer receives `192.168.100.7` from the Huawei ONT and
the ONT receives an address in `100.64.0.0/10`, while Internet lookup observes shared public
`196.3.x.x` space. Digicel IPv4 CGNAT is therefore confirmed.
Because the confirmed Huawei ONT carries provider-managed PPPoE, VLAN, IPTV, VoIP, and TR-069
configuration, X1 must not switch it to bridge mode or alter those settings without explicit Digicel
support. ADR-0020 avoids that need: no ONT DMZ, forwarding, UPnP, bridge, or passthrough rule is used.
A future directly routable ISP address could permit a later ADR to replace the Tunnel, but it is not
an X1 dependency.

ADR-0020 resolves public ingress for X1 by accepting Cloudflare Tunnel as a narrowly bounded external
dependency for the current text/API/WebSocket workload. `cloudflared` connects outward to Cloudflare
and routes only the dedicated API hostname to private NGINX; no ONT, Archer, or Windows inbound rule
is opened. Direct IPv4/IPv6 exposure remains unapproved. Media remains outside X1 implementation,
but its later ET-02 path is now bounded: authoritative files stay on the production host, assembled
attachments are limited to 50 MiB, resumable upload requests to 25 MiB, and the Tunnel remains only
the ingress path. This does not approve live call media, hosted Cloudflare media storage, or an
unbounded general-purpose file service.

Provider account identifiers, PPPoE usernames/passwords, ONT serial numbers, optical identifiers,
and complete public addresses are protected operational data. X1 evidence may record their presence
and sanitized suffixes/hashes but must not commit their raw values or include unredacted screenshots.

For the confirmed Archer AX73 v2.0, use **Advanced → Status → Internet** to obtain the WAN IPv4 value
and **Advanced → NAT Forwarding → Port Forwarding** for the later explicit TCP rules. Do not use DMZ,
port triggering, UPnP-created rules, Internet FTP, or remote router administration as substitutes.
Use the router's USB sharing page to enable Samba plus Secure Sharing for the backup folder while
leaving Internet FTP disabled. The TP-Link user guide is the procedural authority for the exact
firmware interface.

#### 3.2.5 Public health and WebSocket-abuse boundary

The public edge exposes a minimal liveness response that reveals no component names, database state,
queue state, configuration, or diagnostics. Detailed readiness remains private to the production host
and approved monitoring source. If the existing Quarkus readiness payload cannot be safely reduced,
NGINX must not publish it directly.

WebSocket policy must apply both a connection limit and a handshake-attempt rate limit. Acceptance
must cover rapid failed handshakes, reconnect storms, idle connections, oversized frames, distributed
source testing within proportional limits, and recovery of legitimate clients after throttling. The
test must show that source addressing is derived only through the configured immediate trusted proxy.

### 3.3 DNS and certificate-lifecycle contract

#### 3.3.1 Required certificate decisions

Record separately for the NGINX edge and SQL Server transport:

| Decision | Required detail |
|---|---|
| Names | Canonical DNS names and every required subject alternative name |
| Issuer | Public CA, private CA, or other accepted trust source |
| Validation | Cloudflare public-edge issuance and authenticated private-origin routing |
| Automation | Cloudflare-managed public certificate plus pinned `cloudflared` lifecycle |
| Storage | Certificate, private key, chain, truststore, and backup/recovery locations |
| Permissions | Owner/group/mode or equivalent ACL for every consumer |
| Renewal | Attempt interval, warning thresholds, reload behavior, and failure escalation |
| Revocation | Compromise response, replacement, client trust update, and evidence |

A publicly trusted certificate remains mandatory and is supplied at Cloudflare's public edge for the
dedicated hostname. The origin is not publicly reachable. `cloudflared` must validate an authenticated
private HTTPS route to NGINX; plain HTTP origin routing is not accepted merely because the connector
network is private.

The production implementation must use a separate Compose overlay/configuration rather than replacing
or repurposing the rehearsal artifacts under `deploy/tls/generated`. The overlay mounts the selected
public full chain and private key from a restricted host path, supplies the production hostname, and
uses a public-trust smoke path without `--cacert`. The rehearsal CA remains local test material.

#### 3.3.2 Renewal and rotation sequence

The production process must:

1. request or renew without exposing the private key to application containers;
2. validate name coverage, chain, expiry, key match, and file permissions;
3. stage material atomically so NGINX or SQL Server never reads a partial pair;
4. test configuration before reload/restart;
5. reload the smallest affected service;
6. verify HTTPS, WSS, SQL client trust, and expiry through the normal paths;
7. retain only the minimum rollback material allowed by policy; and
8. alert and fail visibly if renewal or activation does not succeed.

Exercise ordinary renewal and emergency replacement before production approval. Certificate expiry
alerts remain required at 30 and 14 days; an additional urgent seven-day alert is recommended.

#### 3.3.3 Client and Postman implications

Production Postman and future clients use the public `https://` and `wss://` endpoints and trust
the selected public issuer. Do not distribute a server private key or private-key passphrase to
clients. Browser HTTP and WebSocket origins must match the deployment allowlists. Origin checks
harden browsers but do not authenticate frontend software or replace user authorization.

### 3.4 Secret and privileged-identity lifecycle contract

#### 3.4.1 Secret inventory

The implementation guide must enumerate, owner-tag, and classify at least:

- bootstrap/operator SQL authority;
- Flyway migrator credential;
- ChatBackend runtime SQL credential;
- backup SQL credential;
- break-glass restore authority;
- ChatMonitor read-only SQL credential;
- RabbitMQ operator and ChatBackend credentials;
- bootstrap administrator application credential until bootstrap is closed;
- backup certificate/private-key recovery passphrase;
- SQL client truststore password where retained;
- edge and SQL private keys; and
- registry, deployment, alert-delivery, DNS, and off-host-storage credentials.

The ChatMonitor runtime credential is an X2-owned secret. X1 owns only the database projection and
grant boundary needed for the handoff; it must not deploy or distribute the X2 credential early.

For each, record producer, consumers, delivery path, storage location, rotation owner, expiry/cadence,
recovery path, revocation procedure, and whether downtime is required. Do not place actual values in
this document, Git, image layers, Compose defaults, CI artifacts, logs, command histories, or
ChatMonitor browser assets.

#### 3.4.2 Delivery and storage rules

The selected host secret mechanism must support service-specific readable files or an equivalently
narrow delivery method. Environment variables may be used only when the exposure and process-inspection
tradeoff is accepted and no safer supported mount exists. Secret files must be owner-restricted,
mounted read-only into only the consumer, excluded from backups unless deliberately encrypted, and
removed from temporary staging.

ChatBackend receives no bootstrap, operator, migrator, backup, restore, DNS, NAS, registry-write, or
RabbitMQ-administrator authority. Deployment jobs receive migrator authority only for the migration
window. Restore authority is break-glass, absent from routine automation, and retrieved only during an
approved recovery event.

The SQL Server and RabbitMQ images may require a bootstrap value during first startup. Production must
not leave that value as an active operator credential visible in a long-running container environment:
provision the durable operator identity, rotate or revoke the bootstrap value, and prove that any
value retained in container metadata is no longer valid. Active operator credentials remain external
to the service container and are delivered only to bounded operator commands.

The production overlay should use service-specific read-only secret files wherever supported. Where a
container or application supports only environment variables, record the accepted inspection risk,
limit the value to that service's runtime authority, and never render or archive the resolved Compose
configuration. Windows ACLs and WSL permissions must both be verified; satisfying only one boundary is
insufficient.

#### 3.4.3 Rotation state machine

Every rotatable username/password credential should follow:

```text
PREPARED_NEW
  -> VERIFIED_NEW_WITHOUT_TRAFFIC
  -> CONSUMERS_UPDATED
  -> NORMAL_PATH_VERIFIED
  -> OLD_REVOKED
  -> EVIDENCE_RECORDED
```

Where a system cannot support overlapping credentials, document the maintenance window, stop order,
credential update, restart, verification, and rollback boundary. Rotation must fail closed before old
revocation if the new credential has not been proven. Perform one non-production rehearsal and one
production rotation exercise before final acceptance for each materially different mechanism.

#### 3.4.4 Exposure response

An exposure procedure must identify affected authority, revoke/replace it, inspect relevant access
and audit evidence, redeploy consumers, verify least privilege, check source history and artifacts,
and record whether dependent secrets also require rotation. Never solve an exposure only by deleting
the visible file while leaving the credential valid.

### 3.5 Immutable release, migration, deployment, and rollback contract

#### 3.5.1 Release identity and promotion

Every production deployment must identify:

- Git commit and signed/annotated release tag as selected by repository policy;
- application image repository, immutable digest, and human-readable version;
- migration image digest;
- NGINX, SQL Server, RabbitMQ, and monitoring image digests;
- Flyway schema version before and after deployment;
- SBOM and security evidence;
- approver, operator, start/end time, and resulting health state; and
- previous compatible application digest.

Mutable tags such as `latest` may aid discovery but cannot be the deployed authority. Production
Compose input must resolve to reviewed digests. Milestone X must replace the current production
placeholder with a deliberately triggered, environment-protected workflow or an equivalently audited
operator process; a missing required production secret must fail rather than report a skipped success.

The release workflow must build the application and migration images once, scan those exact images,
push them under distinct release-scoped tags in the one private Docker Hub repository allowed by the
free Personal plan, and promote their returned registry digests. Rebuilding an image on the
production host or after approval is prohibited. Infrastructure image digests are selected and
scanned in the same release manifest. The deployment authenticates pulls and remains comfortably
inside the Personal pull-rate allowance; a paid registry plan is not an implicit fallback.

Registry authentication uses separate least-privilege credentials by purpose. CI receives a
Docker Hub token capable of pushing only through the protected GitHub secret boundary. Production
receives a pull-only token where Docker Hub supports that scope and authenticates through Docker's
credential store; the token must not be committed, placed in a Compose environment file, embedded in
the deployment bundle, or documented in README examples. If the initially created PAT has broader
scope, use it only for the bounded publisher role and create a distinct pull credential for
production. Revoking either credential must not invalidate the other role.

The release manifest must contain at least:

- release tag, Git commit, application/migration/infrastructure digests, SBOM checksums, scan result,
  and build provenance;
- Flyway schema before/after and a machine-readable minimum/maximum compatible schema range for the
  application image;
- previous verified compatible application digest and an explicit `rollbackAllowed` decision;
- deployment-bundle checksum and expected production hostname/profile; and
- approval, observation-window, smoke, and final disposition fields.

#### 3.5.2 Deployment state machine

The production flow must implement and retain evidence for:

```text
PRECHECKED
  -> BACKUP_VERIFIED
  -> IMAGES_AND_EVIDENCE_VERIFIED
  -> MIGRATION_APPROVED
  -> MIGRATED
  -> APPLICATION_ROLLED_OUT
  -> READINESS_PROVEN
  -> HTTP_WSS_SMOKE_PROVEN
  -> OBSERVATION_WINDOW_PASSED
  -> RELEASE_ACCEPTED
```

Prechecks include host capacity, current health, backup recency, monitoring availability, image
digests, secret availability, migration review, schema compatibility, and maintenance/alert
suppression. Flyway runs once with the migrator, before application rollout, and the application never
auto-migrates with runtime authority.

The post-rollout observation window must inspect readiness/liveness, durable HTTP flows, WSS policy,
SQL/RabbitMQ state, audit persistence/degradation, error/latency trends, restarts, and disk growth.
The duration is chosen from production characterization and deployment risk; it must not be silently
skipped.

#### 3.5.3 Rollback decision tree

1. If application behavior fails and the current schema is verified backward compatible, redeploy the
   last verified compatible image digest and repeat smoke/observation checks.
2. If backward compatibility is uncertain or false, do not place an older image over the newer schema.
   Keep the current image isolated as needed and implement a reviewed forward fix.
3. Use database restore only for an approved destructive/corruption recovery event where accepted data
   loss matches the RPO; restore is not routine application rollback.
4. Never rewrite or automatically reverse an applied Flyway migration.
5. Record the trigger, decision owner, schema evidence, selected path, outcome, and follow-up.

The canonical rehearsal commands remain in the
[`hardened deployment runbook`](../operations/hardened-deployment-runbook.md); production activation
must parameterize or wrap those reviewed behaviors instead of creating an unrelated deployment path.

The production rollback wrapper must read the accepted release manifest and fail closed when schema
compatibility or the previous digest is missing. Merely supplying a digest to the current rehearsal
rollback script is not production compatibility evidence.

#### 3.5.4 Production-candidate release acceptance

The first production release becomes `1.0.0` only after all X1 and X2 exit criteria and final
evidence are accepted. Finalization includes changelog promotion from Unreleased, version alignment,
release notes, image/tag provenance, known-risk register, recovery contacts, and an explicit statement
of the selected network exposure, RPO/RTO, availability objective, and unsupported capabilities.

### 3.6 SQL Server production operations contract

#### 3.6.1 Placement and resource decision

Record the SQL Server 2022 Express container image/build/CU, CPU and
memory limits, collation, timezone behavior, TLS certificate, and ownership. The Milestone 9
containerized topology is the accepted zero-cost placement for this single-host personal deployment.

The `Developer` default in the rehearsal Compose file is prohibited for production. Express is free
for production use but is bounded to its published engine limits, including a 10 GB maximum database,
four utilized cores, and approximately 1,410 MB of buffer-pool memory. It does not provide TDE,
native backup encryption, or SQL-native backup compression. X1 therefore proves an uncompressed native
`BACKUP ... WITH CHECKSUM`, `RESTORE VERIFYONLY`, restore, migration, maintenance, and capacity path,
then compresses and encrypts the verified `.bak` outside SQL Server before off-host transfer. Warn at 7.5 GB,
escalate at 8.5 GB, and prohibit planned growth beyond 9 GB without a new zero-cost architecture
decision. No paid-edition upgrade is implicit in X1.

BitLocker protects SQL data, logs, staging files, and Docker/WSL storage at rest on the Windows host.
No additional SQL-native at-rest encryption is required for this accepted private-host boundary.
SQL transport TLS remains required: it is a zero-cost connection control, not duplicate at-rest
encryption, and prevents silent trust weakening if container-network assumptions change.

Map and size data, transaction log, `tempdb`, backup staging, and diagnostic storage separately where
the host permits it. Configure bounded autogrowth in fixed increments, maximum-size/alert behavior,
and sufficient free space for maintenance and recovery. Do not rely on unlimited percentage growth.

#### 3.6.2 Principal and transport inventory

Acceptance must prove the separate operator, migrator, runtime, backup, restore, and monitoring
authorities defined by ADR-0017 and the migration baseline. Each negative-permission test must prove
what that principal cannot read, mutate, execute, create, restore, or administer. Application,
migrator, backup, and monitor connections must validate the intended SQL Server certificate; setting
`trustServerCertificate=true` is not an accepted production shortcut.

#### 3.6.3 Database maintenance

Define and schedule:

- `DBCC CHECKDB` cadence and alerting;
- backup and restore verification from section 3.8;
- statistics maintenance based on measured need;
- index-health review without habitual blanket rebuilds;
- data/log/tempdb growth and free-space checks;
- failed-login, severe-error, and long-blocking diagnostics;
- SQL Server and host patch review;
- audit-table retention and capacity enforcement; and
- Flyway history validation before and after releases.

Maintenance must use bounded windows, record duration/outcome, and avoid competing with backups,
deployments, ChatMonitor catch-up, or normal active use. Any proposal to enable
`READ_COMMITTED_SNAPSHOT`, change recovery model, alter durability, or add the monitoring index
requires explicit migration/ADR/performance evidence appropriate to its database-wide impact.

Production audit retention is initially **90 days**, preserving more than X2's 62-day detailed
catch-up floor. X1 must add a dedicated maintenance authority and a scheduled, bounded purge that:

1. never grants deletion to ChatBackend or ChatMonitor;
2. deletes only records older than the recorded UTC cutoff in small committed batches;
3. uses an evidence-backed supporting index and stops on blocking, log-growth, or time limits;
4. does not overlap backup, deployment, restore drill, or expected monitor catch-up;
5. records cutoff, rows, duration, failure, and remaining oldest event without message content; and
6. proves that an offline monitor can still catch up within the retained window.

Any reduction below 90 days requires a stakeholder decision and may never fall below 62 days while
the current X2 contract applies.

#### 3.6.4 Recovery and corruption behavior

On suspected corruption, storage failure, or accidental destructive change: restrict writes as
appropriate, preserve logs/evidence, identify the last known good point, select forward repair or
isolated restore under the recovery owner, and never experiment against the sole production copy.
Restored data is validated through `DBCC CHECKDB`, Flyway, application readiness, durable synthetic
records, tombstones, sessions, messages, and delivery/read cursors before cutover.

### 3.7 RabbitMQ production operations contract

#### 3.7.1 Role and availability boundary

RabbitMQ transports audit records; SQL Server remains authoritative for durable messaging. Broker
failure therefore remains ready-but-degraded, not an application-readiness failure. The operator must
still restore the broker before the bounded local fallback is exhausted and must prove eventual audit
persistence afterward.

#### 3.7.2 Production configuration inventory

Record the reviewed RabbitMQ image digest/version, durable volume, node name, virtual host, exchange,
queue, routing key, DLQ, application principal, operator principal, management exposure, TLS decision,
resource limits, disk/memory alarms, and definitions-provisioning path. The application principal
must not be an administrator and must have only the configure/write/read permissions required by the
accepted topology.

The management interface is private to approved administration/monitoring sources or disabled. Default
guest/administrator credentials are absent. Durable queues and persistent messages must survive
container replacement and host restart using the selected durable volume.

#### 3.7.3 Backlog, DLQ, and outage handling

Document thresholds using queue depth, oldest-message age, publish/ack rate, disk alarm, memory alarm,
local fallback utilization, and measured audit throughput. A DLQ count above zero triggers
investigation; it does not trigger blind replay.

DLQ handling must:

1. inspect a sanitized sample and identify the failure class;
2. correct schema/configuration/consumer causes first;
3. preserve event identifiers and idempotency;
4. replay only a bounded reviewed batch;
5. verify durable SQL audit insertion and absence of repeated dead-lettering; and
6. retain evidence of discarded events and explicit justification if replay is unsafe.

Exercise broker stop, restart, persistent-volume recovery, backlog drain, DLQ handling, credential
rotation, and bounded local-fallback pressure before production acceptance. Broker data is operational
transport state, not a substitute for SQL backup.

### 3.8 Backup, off-host transfer, and disaster-recovery contract

#### 3.8.1 Recovery objectives and covered assets

The proportional initial objectives are:

- database RPO: **24 hours**;
- scheduled successful off-host backup interval: **no more than 24 hours**;
- isolated database restore after recovery capacity is ready: **60 minutes**;
- complete-service RTO after an ordinary software/configuration failure: **2 hours**; and
- complete-service RTO after total host loss: **8 hours**, excluding a documented external delay in
  obtaining replacement hardware.

The stakeholder must accept or replace these values before X1 exit. The final record must distinguish:

- database recovery point and recovery time;
- complete service recovery time;
- maximum acceptable audit loss during fail-open degradation;
- monitoring-history recovery expectations; and
- archive-passphrase or secret loss scenarios that can make encrypted packages unusable.

At minimum protect the externally compressed and client-side-encrypted backup package,
checksum/metadata manifest, required
recovery instructions, production configuration,
secret-reconstruction inventory, image digests, Flyway version, and monitoring configuration. Store
the backup artifact and the material needed to decrypt it under separately controlled failure domains
where practical.

The encrypted package and manifest must each be checksummed. The manifest itself
must be authenticated by a detached signature or MAC stored under the recovery authority, so replacing
both a package and its plain checksum is detectable. Use a free, scriptable, reviewed archive tool
to compress the verified `.bak` and apply AES-256 encryption with encrypted filenames (7-Zip is the
initial candidate). Compression reduces transfer/storage size; encryption protects confidentiality;
neither substitutes for SQL checksum and restore verification. The authenticated
manifest supplies independent tamper detection. The
archive passphrase must not be stored on the router drive or share its sole recovery failure domain
with both the package and production host.

#### 3.8.2 Backup and transfer lifecycle

```text
SCHEDULED
  -> NATIVE_BACKUP_CREATED_WITH_CHECKSUM
  -> VERIFYONLY_PASSED
  -> MANIFEST_RECORDED
  -> EXTERNALLY_COMPRESSED_AND_ENCRYPTED_PACKAGE_CREATED
  -> OFF_HOST_TRANSFERRED
  -> DESTINATION_CHECKSUM_VERIFIED
  -> RETENTION_CONFIRMED
  -> MONITOR_HEARTBEAT_UPDATED
```

Failure at any state leaves a visible failed/overdue status and retry path. A local backup does not
satisfy disaster recovery. Staging capacity is bounded and cleanup occurs only after destination
verification and retention requirements are satisfied.

The router-drive record must specify the actual protocol/version, share path, credentials, capacity,
retention tiers, deletion authority, and recovery when the operator workstation is unavailable. SMB
2/3 is preferred and SMB1 is prohibited. The backup package is compressed and encrypted before it
crosses the LAN,
so confidentiality does not depend on share-level encryption. A retrieved package must pass SHA-256
verification, authentication/decryption, `RESTORE VERIFYONLY`, and isolated restore. The accepted
single-router-drive design does not claim snapshots, immutability, fire/theft separation, or
protection from a share administrator deleting every copy.

#### 3.8.3 Retention and drill schedule

Final retention follows measured size and accepted RPO, with a documented grandfather-father-son or
similarly understandable schedule. As a planning baseline—not a final mandate—consider daily copies
for 14 days, weekly copies for 8 weeks, and monthly copies for 6–12 months when capacity permits.
Deletion must never remove the last known-good independently verified recovery point.

Run an isolated restore from the **retrieved off-host copy**, not the local staging file, on the
accepted schedule and after material changes to encryption, SQL version, storage, or restore tooling.
The canonical restore behavior remains in the
[`backup and restore runbook`](../operations/backup-and-restore-runbook.md). Record artifact identity,
retrieval time, restore duration, integrity results, application-level durable-state checks, achieved
RPO/RTO, cleanup, and findings.

#### 3.8.4 Total-host-loss exercise

Before activation, perform or tabletop a production-host-loss sequence detailed enough to execute:
obtain replacement capacity, install pinned prerequisites, restore configuration and trust, retrieve
images by digest, provision principals, retrieve/decrypt backup, restore into an isolated target,
validate, establish DNS/certificate routing, reconnect monitoring, and admit clients. Identify every
step that currently depends on knowledge or material available only on the failed host and remove or
accept that dependency.

### 3.9 Logs, diagnostics, incident response, and privacy contract

#### 3.9.1 Log inventory and retention

Inventory NGINX access/error, ChatBackend application/audit, container, SQL Server, RabbitMQ, host,
deployment, backup, restore, monitoring, and security-gate logs. For each define owner, location,
format, rotation, maximum storage, retention, access, backup status, sensitive fields, and deletion
method.

Keep request/trace correlation available, but do not add message bodies, tokens, credentials, raw
headers, unrestricted query values, or high-cardinality identifiers to routine logs or metrics. Host
clock synchronization is mandatory for cross-system timelines. Production logs must not grow without
bound or silently consume SQL/RabbitMQ/backup capacity.

#### 3.9.2 Diagnostic bundle

Define an operator command or checklist that collects a timestamped, sanitized bundle containing
service/image state, health output, recent bounded logs, restart counts, resource state, queue/DLQ
state, backup/restore heartbeat, certificate metadata without private keys, Flyway version, and
monitor freshness. It must redact secrets, tokens, message content, sensitive audit context, and
private keys; default to a short time window; and avoid mutating production.

#### 3.9.3 Incident lifecycle

Use a lightweight lifecycle proportional to personal operation:

```text
DETECTED -> TRIAGED -> CONTAINED -> RECOVERED -> VERIFIED -> REVIEWED
```

Record start/end time, detection source, affected capability/data, severity, actions, evidence,
recovery verification, residual risk, and follow-up. Required exercises cover application failure,
database unavailability, RabbitMQ degradation, certificate-renewal failure, disk pressure, failed
deployment, lost/rotated credential, backup overdue, and monitoring outage. Security or privacy
incidents additionally require credential scope review and evidence preservation.

### 3.10 Production characterization, objectives, and capacity contract

#### 3.10.1 Workload model

Because this is a private personal deployment, characterize the expected workload rather than an
enterprise-scale hypothetical. Record expected users, active devices, simultaneous sockets,
conversations, messages/day, typical and maximum message length, retained-history size, audit-event
rate, backup growth, and future media-storage assumptions. Use synthetic identities and content for
testing.

#### 3.10.2 Characterization sequence

1. Record host/container resource limits, image digests, database size, fixture size, and network path.
2. Run the existing characterization workload from the
   [`load-test baseline`](../operations/load-test-baseline.md) using valid production-like TLS against
   an approved non-production or maintenance-isolated target.
3. Add scenarios only for demonstrated critical paths; do not turn Milestone X into a speculative
   load-testing platform.
4. Observe API latency/error rate, WSS checks, CPU, memory, disk I/O, SQL waits/blocking, datasource
   behavior, RabbitMQ/audit behavior, restarts, and the safe monitoring projection under a bounded
   synthetic read; ChatMonitor collector impact belongs to X2.
5. Run the accepted threshold phase, verify durable state afterward, and retain sanitized results.
6. Establish normal operating ranges and alerts from evidence rather than copying laptop numbers.

#### 3.10.3 Required objectives

Before activation, the stakeholder accepts values for:

- availability expectation and planned maintenance treatment;
- API p95 and failure-rate threshold for representative operations;
- WebSocket handshake/ping success and acceptable signaling delay;
- maximum collector lag and audit backlog age;
- RPO and complete-service RTO;
- disk reserve and growth warning horizon;
- backup and restore-drill freshness; and
- maximum simultaneous client/device target for the installed host.

These are operator objectives, not public SLA promises. Failure to meet them blocks activation only
where the stakeholder marks the objective as a release gate; otherwise the deviation requires an
explicit accepted risk and follow-up.

### 3.11 Production readiness, evidence, and ownership contract

#### 3.11.1 Responsibility matrix

Even when one person fills every role, Milestone X must name the acting owner for:

| Responsibility | Required ownership |
|---|---|
| Product/release approval | Accept scope, objectives, residual risks, and `1.0.0` |
| Host/network | Hardware, OS, firewall, DNS, power, and patching |
| Deployment | Registry, image promotion, migration, rollout, rollback, and evidence |
| Database | SQL provisioning, maintenance, backup, restore, and capacity |
| Security | Secrets, certificates, scans, findings, and incident review |
| Monitoring | Uptime/host tools, ChatMonitor, alerts, retention, and workstation recovery |
| Recovery | Off-host artifacts, key recovery, drills, and disaster decision |

No requirement disappears because the same stakeholder owns multiple columns.

#### 3.11.2 Production evidence manifest

Maintain a sanitized manifest that links rather than embeds sensitive evidence. It records production
host/profile identifier, release/image digests, schema version, deployment result, smoke result,
certificate expiry/renewal exercise, principal tests, firewall verification, backup identity and
off-host verification, restore drill, load characterization, monitoring/alert exercises, unresolved
risks, approvals, and timestamps. Evidence with secrets, private topology, raw logs, or production data
remains in access-controlled operator storage, not Git.

#### 3.11.3 Readiness review

The final review must classify every entry decision and finding as resolved, explicitly accepted with
reason/owner/review date, or production-blocking. It must confirm documentation matches the deployed
state, recovery is executable without chat history, the client responsibility guide remains accurate,
and no completion claim relies only on the local Milestone 9 rehearsal.

---

## 4. Ordered implementation slices and verification gates

X1 remains one increment, but implementation must proceed through bounded slices. A later slice may
not hide a failed earlier gate.

### X1-A — Decision freeze and Windows host baseline

Implement the X1-D01 Cloudflare Tunnel ingress and record the accepted X1-D02 through X1-D04 implementation
values, validate the host facts in section 2.1, select exact versions, create the
restricted directory/ACL model, install the WSL/PowerShell prerequisite checker, and capture sanitized
baseline evidence. Gate: the dedicated hostname and outbound-only Tunnel are proven;
DNS, network-share protocol, and recovery-key facts are recorded; and the
cold-reboot prerequisite check succeeds. Private-stack implementation may proceed while ingress is
blocked, but X1-D and production-candidate status may not.

### X1-B — Build, registry, release manifest, and deployment control

Implement tagged release build/scan/push, the single free private Docker Hub repository, digest manifest,
production-environment approval, deployment bundle, workstation runner, SSH transport, concurrency,
and schema-aware rollback preflight. Gate: deploy and rollback the same immutable candidate twice in a
non-production target without rebuilding images or exposing production secrets.

### X1-C — Private production stack and authority hardening

Create the production Compose overlay, SQL Server Express configuration, persistent volumes, production SQL TLS,
RabbitMQ topology, service-specific secrets, bootstrap revocation, migration path, audit-retention
maintenance, and Windows lifecycle tasks. Keep router ingress closed. Gate: clean deploy, permission
negative tests, reboot recovery, broker degradation/drain, retention dry run, and container inspection.

### X1-D — Public DNS, TLS, and edge controls

Implement Cloudflare DNS onboarding, Tunnel/public TLS, connector credential rotation, private
origin TLS, public hostname, production NGINX policy,
minimal public liveness, private detailed readiness, WebSocket handshake rate limiting, host firewall,
and zero public origin ports. Gate: external public-trust HTTPS/WSS tests plus proof that every internal and
administrative port remains unreachable.

### X1-E — Off-host backup and recovery

Implement scheduled checksum backup, externally compressed/client-side-encrypted package,
authenticated manifest, router-drive transfer, retention,
destination checksum, recovery-key separation, overdue/failure state, retrieved restore drill, and
total-host-loss procedure. Gate: a retrieved off-host artifact passes isolated restore, application
data verification, and the accepted RPO/RTO objectives.

### X1-F — Characterization, operational foundations, and handoff

Run representative characterization, capacity thresholds, diagnostics, incident exercises, evidence
manifest review, documentation reconciliation, and X2 projection handoff. Gate: all X1 exit criteria
below are resolved or explicitly accepted with owner and review date; no X2 implementation is claimed.

### 4.1 Command and evidence acceptance matrix

The implementation may introduce Windows/WSL wrappers, but each row must finish with a stable
repository command or documented PowerShell entrypoint and sanitized evidence:

| Concern | Required executable acceptance |
|---|---|
| Repository | `./mvnw clean verify`, Postman validation when contracts change, SBOM/dependency/image/secret gates |
| Release | Build once, push, pull by digest, verify manifest/checksums/provenance, reject mutable authority |
| Host | Windows prerequisite check, Docker/Compose readiness, cold reboot, sign-in startup, power-loss recovery |
| Database | Clean and upgrade migration, exact-edition permission negatives, TLS rejection, audit purge, `DBCC CHECKDB` |
| Edge | Public CA trust, HTTPS/WSS, Origin/token policy, handshake/request/connection limits, port scans from Internet and LAN |
| Queue | Runtime permission negatives, restart persistence, ready-but-degraded outage, fallback pressure, drain, DLQ procedure |
| Backup | Scheduled creation, signed/MACed manifest, secure transfer, remote checksum, retention, retrieval, isolated restore |
| Rollback | Compatible-image success and incompatible/unknown-schema fail-closed evidence |
| Capacity | Characterization and threshold run with durable-state verification and resource evidence |
| Documentation | Architecture, threat model, runbooks, changelog, client-guide audit, evidence manifest, X2 handoff |

---

## 5. X1 Exit Criteria

X1 is complete only when:

- the production host and private network boundary are provisioned and ownership is recorded;
- HTTPS/WSS uses the selected trusted certificate path and renewal/rotation is exercised;
- only the hardened NGINX HTTPS/WSS edge is public, while internal and administrative services remain
  unreachable from the Internet;
- SQL Server and RabbitMQ remain private and least-privilege inventories match the hardened model;
- SQL Server Express and its size limits are enforced, and checksum backup/restore plus external
  package compression and encryption are proven on that exact edition;
- secret delivery and rotation, immutable image promotion, migration-before-rollout, and
  schema-aware rollback are proven;
- Windows sign-in startup, cold reboot, power-loss recovery, audit retention, minimal public health,
  and WebSocket abuse controls are proven;
- a retrieved off-host encrypted backup restores within the accepted RTO and meets the accepted RPO;
- production-representative characterization passes the approved X1 thresholds;
- the incident, diagnostic, evidence-manifest, and responsibility foundations are usable without
  relying on chat history;
- the threat model, architecture, operations, client responsibility guide, and changelog match the
  deployed candidate; and
- every X1 risk is resolved, explicitly accepted with owner and review date, or marked as an X2/final
  production blocker.

Passing X1 means **secure and recoverable production candidate**, not production approval.

## 6. Handoff to X2

The X1 handoff must provide sanitized identifiers or protected references for the deployed release,
image digests, schema version, firewall and certificate evidence, runtime principals, backup and
restore evidence, characterization baseline, unresolved risks, and all endpoints or projections X2
needs for monitoring. X2 must not infer these values from developer-machine rehearsal defaults.

## 7. Implementation-readiness audit resolution ledger

The 2026-08-30 senior technical audit produced no required application-feature changes. Its findings
are resolved in this plan as follows:

| Audit finding | Planning resolution |
|---|---|
| Windows/WSL2 execution model absent | Confirmed host profile and section 2.4 Windows execution-plane contract |
| Public DNS/CGNAT/certificate decisions unresolved | Accepted zero-cost hostname/certificate direction in X1-D01/X1-D04; X1-A performs environment discovery and sections 3.2–3.3 implement it |
| Production SQL edition unresolved | X1-D02 selects free SQL Server Express; exact-edition limits and verification are in section 3.6 |
| Production release/deployment workflow absent | Section 2.3 tagged build-once artifact-only release model and X1-B |
| Rehearsal secret boundary unsuitable for production | Section 3.4 bootstrap revocation and service-specific delivery contract |
| Secure off-host backup path absent | X1-D03 accepts the router drive with pre-transfer compressed/encrypted packages and recorded deletion risk; section 3.8 defines retrieval and restore |
| Rehearsal TLS/configuration hard-coded | Production overlay and public-trust requirements in section 3.3 |
| Authoritative audit retention absent | 90-day bounded maintenance contract in section 3.6.3 |
| Rollback compatibility was a human assertion | Machine-readable release manifest and fail-closed rollback in section 3.5 |
| X1/X2 monitoring scope overlapped | X1 supplies only projection/grant prerequisites; X2 owns runtime credential and collector |
| Recovery objectives were ambiguous | Separate RPO, isolated-restore, ordinary-failure, and host-loss objectives in section 3.8.1 |
| Public readiness and socket-handshake controls underspecified | Minimal liveness/private readiness and handshake-rate contract in section 3.2.5 |

The guide is planning-complete and ready for implementation. X1-A owns the remaining discovery rather
than treating it as stakeholder homework. This does not claim that any production control has already
been built or verified.
