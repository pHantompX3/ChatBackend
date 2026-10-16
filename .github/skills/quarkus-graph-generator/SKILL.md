---
name: quarkus-graph-generator
description: Scan a Quarkus Maven repository and generate or incrementally update its Excalidraw architecture, dependency, and canonical-documentation navigation map plus an SVG preview. Use when asked to update the codebase graph, generate a project map, visualize architecture, or refresh the map after represented source, dependency, deployment, or documentation-route changes.
---

# Quarkus Architecture Graph Generator

## Outcome

Keep `project-architecture.excalidraw` synchronized with the repository's implemented structure and
dependencies. Also generate `project-architecture.svg` as a reviewable preview and report scan totals,
detected Quarkus capabilities, validation results, and an ISO-8601 UTC update timestamp.

The graph is evidence derived from source and configuration. Do not infer components merely because
they are common in Quarkus projects.

## Scan

1. Read repository instructions and inspect an existing graph before scanning. Preserve intentional
   zones, labels, and spatial familiarity where they remain accurate.
2. Inspect `pom.xml`, module POMs, `src/main/java`, `src/main/resources/application.properties` or
   YAML equivalents, `src/main/docker`, Compose/deployment files, migrations, any repository-owned
   frontend source, and the canonical documentation routed from `AGENTS.md`. Include documentation
   as a navigation map—not as one node per Markdown file. Exclude Maven wrappers, generated output,
   vendored code, and ordinary test fixtures.
3. Catalog primary packages and production classes. Classify using source evidence, including:
   - HTTP/interface: `@Path`, `@Provider`, filters, exception mappers, and WebSocket endpoints;
   - application/service: CDI scopes, observers, use-case services, transaction boundaries, and
     constructor/field injection;
   - domain: aggregates, value objects, domain events, policies, and ports/interfaces;
   - persistence: Jakarta entities, Panache types when present, JDBC repositories, datasources,
     migrations, and SQL Server or other database boundaries;
   - integration: messaging annotations, RabbitMQ/Kafka channels, REST clients, schedulers, and
     external services;
   - edge/operations: NGINX, Cloudflare, Docker/Compose, health, telemetry, and deployment surfaces.
   - repository knowledge: root orientation, architecture/ADR sources, domain and evolution specs,
     implementation/client guides, and operations/security runbooks.
4. Derive relationships from imports, constructor/field injection, implemented interfaces, method
   calls where needed, configuration wiring, messaging channels, and durable data ownership. Record
   confidence and omit a relationship when evidence is insufficient.
5. Detect capabilities from resolved Maven dependencies and configuration, not names alone. Examples
   include RESTEasy Reactive/Quarkus REST, JDBC, Hibernate/Panache, Flyway, OpenAPI, WebSockets,
   RabbitMQ/Reactive Messaging, OIDC, health, and container-image support.

Use `rg`/`rg --files` for discovery. Never include secret values from properties, environment files,
Compose secrets, credentials, tokens, or certificates in either artifact.

## Graph design

Use stable element identifiers derived from source paths or durable logical component keys. Organize
the canvas into the smallest useful set of zones:

- inbound clients and edge/ingress: neutral gray;
- HTTP, WebSocket, filters, and interface adapters: light blue;
- application services and domain logic: light green;
- persistence, repositories, migrations, and durable stores: light yellow;
- messaging and external/runtime dependencies: light purple;
- deployment and operational support: light orange when needed.
- documentation and agent navigation: light teal.

Group related classes or documents into a component/module node when item-level nodes would make the
map noisy. The documentation zone must let a cold agent navigate from `AGENTS.md` to the canonical
source for architecture, behavior, active planning, client integration, operations, and security.
Include representative class/document names and source paths in the node text or metadata. Use explicit arrows
for request/data flow and CDI dependencies; label arrows such as `HTTP`, `injects`, `implements`,
`JDBC`, `publishes`, `consumes`, or `WSS`. Use bidirectional arrows only when the implemented protocol
is genuinely bidirectional.

If `project-architecture.excalidraw` exists, parse it and retain positions, dimensions, colors, and
zone boundaries for surviving stable identifiers. Add new nodes near their related zone and remove
nodes whose source evidence no longer exists. Do not overwrite a hand-positioned graph with an
unrelated full redraw solely for cosmetic consistency.

## Artifacts

Write both files at the workspace root unless the repository already keeps architecture artifacts in
`.idx/`:

- `project-architecture.excalidraw`: valid Excalidraw JSON with `type: "excalidraw"`, supported
  version/source fields, elements, and app state;
- `project-architecture.svg`: a clean preview matching the same nodes, zones, labels, and arrows.

The SVG is a derived review artifact; the Excalidraw file remains the editable source. Do not use a
network-dependent renderer when a local renderer or direct SVG generation is sufficient. When the
environment supports an artifact/interface preview, open the SVG there after validation.

## Validation and report

Before completion:

1. parse the Excalidraw file as JSON and the SVG as XML;
2. confirm every source-path label still exists or is explicitly a runtime/external component;
3. confirm no secrets or machine-specific absolute paths were copied;
4. compare scanned modules/classes with represented component groups and explain deliberate
   aggregation;
5. inspect the rendered SVG for overlaps, clipped text, unreadable arrows, and excessive detail;
6. update `CHANGELOG.md` when the architecture map materially changes, following repository policy.

Report:

| Measure | Required value |
| --- | --- |
| Production packages | Count |
| Production classes | Count |
| Represented component nodes | Count |
| Detected capabilities | Evidence-backed list |
| Updated at | ISO-8601 UTC timestamp |

Also summarize material graph changes, excluded boilerplate, validation performed, and any uncertain
relationship that needs human confirmation.
