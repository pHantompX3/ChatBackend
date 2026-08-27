# Load-test baseline

## Reproducible workload

`load-test/chat-backend.js` performs authenticated durable message-history reads and periodic
authenticated WebSocket ping/pong exchanges. The characterization phase uses three virtual users for
30 seconds. The regression phase uses ten virtual users for 60 seconds and gates on less than 1%
failed HTTP requests, HTTP p95 below 500 ms, and more than 99% successful checks.

Run characterization first with an isolated seeded conversation and dedicated test session:

```text
scripts/ci/run-load-test.sh --phase characterization
scripts/ci/run-load-test.sh --phase regression
```

The runner requires a reviewed digest-pinned k6 image and explicit HTTP/WSS URLs, bearer token, and
conversation ID. Raw results are ignored. Never use a production user's token or test against an
unapproved production database.

## Baseline status

No production baseline is claimed until the hardened rehearsal is deployed, seeded, both phases run,
the generated summaries are reviewed, and the environment is reset. Record host resources, image
digests, database size, scenario variables (excluding secrets), result summary, and accepted threshold
changes here after that run.
