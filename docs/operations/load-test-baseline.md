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

### Local hardened rehearsal — 2026-08-27 UTC

This is a reproducible local regression baseline, not a production capacity claim.

- Environment: Docker Desktop on Apple silicon (`arm64`), 10 host CPUs, 8.32 GB host memory, and a
  7.75 GiB Docker memory limit.
- Tested application image: `sha256:3f2abf8369694babf17aeaa0c5d7859336fd179c8e52b9392f266c2c32d6adfb`.
- Supporting images: SQL Server
  `sha256:ba4c8329f48fb8f02e1416be6a930ebfd71268caee78aa985f3af4315e457c89`, RabbitMQ
  `sha256:eb5295d083325da5929a5ade766684d4019ffd2bce8bc7e43d6f9a05cafc8646`, NGINX
  `sha256:93722936b82ec8a1178d48448e619226680d2de3706a1640800e186cd5fa7fd3`, and migration image
  `sha256:0bf667ac5c3d4ccbe066b76a60f7644b0e09d98de61004b39758941212c135db`.
- Load generator: Grafana k6 2.0.0,
  `sha256:a33a0cfdc4d2483d6b7a3a22e726a499ff2831a671a49239104cd34a9937523c`.
- Starting data: 144 MB database with two synthetic users, one direct conversation, and no messages.
  The workload was intentionally read-only apart from WebSocket ping/pong signaling.
- Characterization: 96/96 checks passed; 87 HTTP requests, 0% failed, HTTP p95 46.4646 ms; nine
  WebSocket sessions.
- Regression: 650/650 checks passed; 590 HTTP requests, 0% failed, HTTP p95 53.2566 ms; 60 WebSocket
  sessions. All committed thresholds passed without adjustment.
- Post-run verification: the exact synthetic fixture was removed and contained zero durable messages,
  confirming that the read-only scenario did not mutate message state.

The container used `insecureSkipTLSVerify=true` only because it reached the host as
`host.docker.internal` while the local rehearsal certificate names `localhost`. Separate HTTPS/WSS
proxy checks validate the generated CA and hostname through `localhost`; production load execution
must use a certificate valid for its tested hostname.

The hardened WebSocket close policy is independently reproducible with:

```text
WL_CHAT_K6_IMAGE=<reviewed-k6-image@sha256:...> \
WL_CHAT_LOAD_WS_BASE_URL=wss://host.docker.internal \
WL_CHAT_WEBSOCKET_ALLOWED_ORIGIN=https://localhost \
WL_CHAT_LOAD_INSECURE_TLS=true \
scripts/deploy/verify-websocket-policy.sh
```

That probe verified `4403` for a disallowed Origin and `4401` for both missing credentials and the
disabled query-token transport. A production baseline still requires the selected production host,
representative data, approved thresholds, and valid public TLS.
