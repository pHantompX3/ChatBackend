# Postman Integration

This folder contains the version-controlled Postman artifacts for ChatBackend.

## Source of Truth

There is currently no OpenAPI document in this repository.

Current contract flow:

- Application routes and DTOs in src/main/java are the API contract source.
- The committed collection in postman/collections mirrors those implemented routes.

When OpenAPI is introduced later, document and enforce one direction only (OpenAPI to Postman, or Postman from routes).

## Repository Layout

- postman/collections/chat-backend.postman_collection.json
- postman/environments/local.example.postman_environment.json
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
   - optional postman-environment-id

postman/config.properties is gitignored and must never be committed.

You can also override by environment variables:

- POSTMAN_API_KEY
- POSTMAN_WORKSPACE_ID
- POSTMAN_COLLECTION_ID
- POSTMAN_ENVIRONMENT_ID
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
- updates configured collection and environment IDs deterministically
- refuses overwrite when configured resource is not found in configured workspace
- only creates missing resources when --create-missing is provided
- stores newly created non-secret IDs back into local ignored config
- updates existing collection/environment in place so desktop Postman receives latest requests without delete/import cycles
- supports strict drift checks for CI gating against configured cloud targets

## GitHub Actions Workflows

- postman-validate.yml runs local artifact validation on pull requests.
- postman-validate.yml runs discovery first and fails when generated collection updates are not committed.
- postman-validate.yml also runs strict cloud drift check when required POSTMAN\_\* secrets are configured.
- postman-sync.yml is manual only (workflow_dispatch) and supports dry-run, sync, and drift-check modes.
- postman-sync.yml runs discovery first and fails when generated collection updates are not committed.
- postman-sync.yml fails early if required secrets are missing for the selected mode.

## Running Collection Against Local App

Start app locally, then use environment base_url:

- default base_url is http://localhost:8080
- update local environment inside Postman as needed for your runtime port

## Security Rules

- Never commit API keys, tokens, passwords, cookies, or private URLs with credentials.
- Never paste secrets into collection examples or environment templates.
- Keep postman/config.properties local only.
- Do not enable shell tracing that could print secrets.

## Postman Access Requirements

You need a Postman API key with access to the target workspace and permission to read and update collections/environments.
