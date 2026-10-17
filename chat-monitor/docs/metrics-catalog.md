# Metrics Catalog

This catalog is the semantic authority for ChatMonitor measurements. X2 persists source-neutral
events and hourly/daily aggregates and exposes bounded environment-specific query APIs.

Every metric and health state is scoped to one configured environment ID. Cross-environment totals
must never be produced implicitly: a future comparison view must name every included environment,
preserve classifications, and keep series visually distinct. Changing the dashboard selector does
not alter collection or aggregate generation.

| Metric | Unit | Source | Current status | Interpretation |
|---|---:|---|---|---|
| `monitor.state` | enum | ChatMonitor | Implemented | `CURRENT`, `CATCHING_UP`, `SOURCE_UNAVAILABLE`, `COLLECTION_FAILED`, or `HISTORY_GAP` |
| `monitor.status.generated_at` | UTC timestamp | ChatMonitor | Implemented | Time the local status response was created; not proof of source freshness |
| `collector.last_success_at` | UTC timestamp | Collector | Implemented | Last successful bounded collection cycle |
| `collector.lag_seconds` | seconds | Collector | Implemented | Difference between collection time and the committed persistence cursor |
| `http.request.count` | count | SQL projection | Implemented | `http.request.completed` events in each hourly/daily dimensional bucket |
| `http.request.duration` | milliseconds | SQL projection | Implemented average/max | Server-observed request duration for the explicit selected window |
| `http.expected_4xx.count` | count | SQL projection | Implemented | Expected/auth/client outcomes, deliberately excluded from server failures |
| `http.failure.count` | count | SQL projection | Implemented | `5xx` count only |
| `http.failure.rate` | ratio | Aggregates | Implemented | `5xx / all requests` for the explicit selected window; zero when no requests exist |
| `monitor.alert.state` | enum | Alert engine | Implemented | `PENDING`, `FIRING`, `ACKNOWLEDGED`, or `RECOVERED` |

`CURRENT` means the source was reachable and the collector exhausted all currently available bounded
pages. `CATCHING_UP` means the per-cycle page limit was reached and later cycles must continue. The
generated-at time is never a substitute for `lastSuccessfulCollectionAt`.

Query windows are bounded to 1–2160 hours, operation results to 50 rows, and sanitized failures to
100 rows. Current dashboard choices are 1 hour, 24 hours, 7 days, and 30 days. No implicit
cross-environment aggregate exists. Aggregate reads align the requested cutoff to the start of the
selected hour/day bucket so a partially overlapping bucket is included instead of silently dropping
valid events from the requested window.
