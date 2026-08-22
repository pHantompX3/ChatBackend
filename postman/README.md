# Postman Integration

This folder contains the version-controlled Postman artifacts for ChatBackend.

## Source of Truth

The contract direction is Java resources and DTOs to generated OpenAPI to Postman:

- application routes and DTOs in `src/main/java` generate the API description,
- normalized snapshots are committed under `docs/api`,
- discovery and validation compare the main collection with the OpenAPI operation inventory,
- curated stateful flow scripts remain owned by the Postman collection.

## Repository Layout

- postman/collections/chat-backend.postman_collection.json
- postman/environments/local.example.postman_environment.json
- postman/environments/devdocker.example.postman_environment.json
- postman/environments/production.example.postman_environment.json
- postman/config.properties.example
- scripts/postman/validate-postman.sh
- scripts/postman/sync-postman.sh
- scripts/postman/inspect-postman.sh
- scripts/postman/discover-postman.sh

## Postman Native Git / Local View

Use Postman desktop one-time setup:

1. Open Postman desktop.
2. Use Native Git or Local View to connect this repository folder.
3. Point Postman to files under postman/collections and postman/environments.
4. Keep edits in these files and commit them directly to Git.

One-time desktop action is required; this repository cannot complete GUI connection programmatically.

## Local Config (Ignored)

Create local config for Postman Cloud sync:

1. Copy postman/config.properties.example to postman/config.properties.
2. Fill real values for:
   - postman-api-key
   - postman-workspace-id
   - optional postman-collection-id
   - optional postman-flow-collection-id
   - optional postman-local-environment-id
   - optional postman-dev-environment-id
   - optional postman-prod-environment-id

Backward compatibility:

- `postman-environment-id` is still supported and treated as the Local environment target.

postman/config.properties is gitignored and must never be committed.

You can also override by environment variables:

- POSTMAN_API_KEY
- POSTMAN_WORKSPACE_ID
- POSTMAN_COLLECTION_ID
- POSTMAN_FLOW_COLLECTION_ID
- POSTMAN_ENVIRONMENT_ID
- POSTMAN_LOCAL_ENVIRONMENT_ID
- POSTMAN_DEV_ENVIRONMENT_ID
- POSTMAN_PROD_ENVIRONMENT_ID
- POSTMAN_CONFIG_FILE (optional custom properties file path)

## Validate Locally (No Cloud Credential Required)

Discover and merge APIs first (preserves protected Quarkus health checks and adds missing endpoint stubs/examples):

```bash
./scripts/postman/discover-postman.sh
```

Run:

```bash
./scripts/postman/validate-postman.sh
```

Validation covers:

- JSON parseability
- basic collection/environment required structure
- duplicate request names
- unresolved variables
- hardcoded absolute URLs instead of {{base_url}}
- obvious secret markers in committed artifacts
- required Quarkus health requests (`/q/health/live`, `/q/health/ready`) so discovery cannot accidentally remove them
- Run-all smoke guardrails: every request under Run-all API smoke journey must include a test assertion named with `Expected:` and an explicit HTTP status assertion

By default validation runs against all three committed environment templates:

- postman/environments/local.example.postman_environment.json
- postman/environments/devdocker.example.postman_environment.json
- postman/environments/production.example.postman_environment.json

## WebSocket Signaling Validation

Postman collection schema v2.1 and Newman validate the authoritative HTTP recovery journey; they do
not encode a runnable WebSocket request. In Postman desktop, create a WebSocket request using
`ws://localhost:8080/api/v1/ws?token={{member_session_token}}` (or the equivalent environment host),
then verify `{"action":"ping"}` returns `{"type":"pong"}`. Delivery and read commands use
`{"action":"delivery.ack","conversationId":"<uuid>","sequence":<n>}` and the corresponding
`read.ack` action. After reconnecting, run the existing reconnect-reconciliation journey to recover
missed durable messages over REST before advancing a cursor.

Network-level WebSocket integration is currently an explicit manual validation responsibility owned
by the project owner. A separate Postman Desktop WebSocket collection may reuse these environment
variables and scenarios, but Newman, Postman CLI collection runs, monitors, and the repository's
current CI workflow do not execute it. Automated repository tests validate the underlying
authentication, registry, fan-out/privacy, acknowledgement, revocation, and REST reconciliation
components; they are not a live handshake-and-frame CI test.

## Smoke Flow Authoring Requirements

These are required for all newly created flow requests and all new additions to Run-all API smoke journey:

- Include at least one post-response `pm.test(...)` assertion per request.
- Name status assertions with an explicit expectation prefix: `Expected: ...`.
- Assert HTTP response status explicitly (for example `pm.response.to.have.status(200)` or an explicit allowed set for multi-outcome steps).
- For identity-creating steps (for example invitation redeem/user creation), generate a unique username per run to avoid collisions in repeat runs.
- Ensure query params expected to be numeric (for example `message_limit`) resolve to concrete values; the current run-all flow applies a fallback of `50` when globals are absent.

## Inspect Existing Cloud Assets

List accessible Postman workspaces with collection/environment IDs:

```bash
./scripts/postman/inspect-postman.sh
```

Use this output to set deterministic sync targets in local ignored config.

## Synchronize to Postman Cloud

Dry-run first:

```bash
./scripts/postman/sync-postman.sh --dry-run
```

Real sync:

```bash
./scripts/postman/sync-postman.sh
```

Optional create when IDs are not configured:

```bash
./scripts/postman/sync-postman.sh --create-missing
```

Optional skip environment sync:

```bash
./scripts/postman/sync-postman.sh --skip-environment
```

Strict cloud drift check (fails when cloud differs from repository artifacts):

```bash
./scripts/postman/sync-postman.sh --check-drift
```

Behavior summary:

- validates required configuration
- verifies workspace exists
- updates configured collection IDs and environment IDs deterministically
- refuses overwrite when configured resource is not found in configured workspace
- only creates missing resources when --create-missing is provided
- stores newly created non-secret IDs back into local ignored config
- updates existing collections/environments in place so desktop Postman receives latest requests without delete/import cycles
- supports strict drift checks for CI gating against configured cloud targets

Environment sync targets:

- Local artifact: postman/environments/local.example.postman_environment.json
- DevDocker artifact: postman/environments/devdocker.example.postman_environment.json
- Production artifact: postman/environments/production.example.postman_environment.json

## GitHub Actions Workflows

- postman-validate.yml runs local artifact validation on pull requests.
- postman-validate.yml runs discovery first and fails when generated collection updates are not committed.
- postman-validate.yml also runs strict cloud drift check when required POSTMAN\_\* secrets are configured.
- postman-sync.yml is manual only (workflow_dispatch) and supports dry-run, sync, and drift-check modes.
- postman-sync.yml runs discovery first and fails when generated collection updates are not committed.
- postman-sync.yml fails early if required secrets are missing for the selected mode.

## Running Collections Across Local And DevDocker

Collections are shared across environments. Use the same collection files with different environment files:

- Local host-run app environment: postman/environments/local.example.postman_environment.json (base_url http://localhost:8080)
- DevDocker app environment: postman/environments/devdocker.example.postman_environment.json (base_url http://localhost:8081)

This keeps request generation/discovery identical while allowing runtime-specific targets via base_url only.

### Environment-specific globals convention

Credential and identity defaults should be namespaced by environment and referenced from each Postman environment file:

- Local globals: `WLAdminUser_Local`, `WLAdminPass_Local`, `WLMemberUser_Local`, `WLMemberPass_Local`, `WLAuthUser_Local`, `WLAuthPass_Local`, `WLActorUserId_Local`, `WLTargetUserId_Local`
- Dev globals: `WLAdminUser_Dev`, `WLAdminPass_Dev`, `WLMemberUser_Dev`, `WLMemberPass_Dev`, `WLAuthUser_Dev`, `WLAuthPass_Dev`, `WLActorUserId_Dev`, `WLTargetUserId_Dev`
- Prod globals: `WLAdminUser_Prod`, `WLAdminPass_Prod`, `WLMemberUser_Prod`, `WLMemberPass_Prod`, `WLAuthUser_Prod`, `WLAuthPass_Prod`, `WLActorUserId_Prod`, `WLTargetUserId_Prod`

`WLTargetUserId_<Env>` must contain the UUID of the user whose sessions an administrator intends to revoke; it is distinct from the member username stored in `WLMemberUser_<Env>`.

Recommended shared defaults by environment namespace:

- `WLConversationId_<Env>`
- `WLSenderUserId_<Env>`
- `WLMessageLimit_<Env>`
- `message_sequence` (a concrete non-negative example value for generated delivery/read requests)

The run-all journey also maintains its own synthetic `flow_message_sequence`,
`flow_second_message_sequence`, and `flow_ahead_sequence` values from server responses. Those values
are generated during a run and must not be populated with production message metadata.

## Security Rules

- Never commit API keys, tokens, passwords, cookies, or private URLs with credentials.
- Never paste secrets into collection examples or environment templates.
- Keep postman/config.properties local only.
- Do not enable shell tracing that could print secrets.

## Postman Access Requirements

You need a Postman API key with access to the target workspace and permission to read and update collections/environments.
