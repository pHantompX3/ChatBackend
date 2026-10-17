# Environment registry

Copy `environments.example.json` to the ignored `environments.json`, replace the example connection
coordinates, and set `CHAT_MONITOR_ENVIRONMENTS_FILE=/run/environments/environments.json` for the
Docker runtime. Environment identifiers are durable persistence keys: rename one only when you
intend the replacement to start with a new cursor and isolated history.

Use `mode: "planned"` only for a known environment that is intentionally not deployed yet. A
planned profile remains visible in the selector but reports `SOURCE_UNAVAILABLE`, starts no
collector, accepts no connection coordinates, and cannot be mistaken for operational telemetry.
Replace it with a complete `mode: "sql"` profile when the environment is actually deployed.

The registry may reference password files under `/run/secrets` by using paths relative to this
directory, such as `../secrets/local-monitor-password`. It must never contain passwords, tokens,
private keys, or CA private material. Editing the registry requires a container restart; all listed
collectors run concurrently after startup, while the dashboard selector changes only the displayed
environment.
