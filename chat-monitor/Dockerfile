ARG NODE_IMAGE=node:22.17.0-bookworm-slim@sha256:b04ce4ae4e95b522112c2e5c52f781471a5cbc3b594527bcddedee9bc48c03a0

FROM ${NODE_IMAGE} AS builder

WORKDIR /workspace
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/* \
    && npm install --global npm@11.6.0
COPY package.json package-lock.json ./
RUN npm ci

COPY config config
COPY migrations migrations
COPY scripts scripts
COPY src src
COPY test/fixtures test/fixtures
COPY vite.config.js ./
RUN npm run build \
    && npm prune --omit=dev

FROM ${NODE_IMAGE}

ARG APP_VERSION=0.1.0
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="ChatMonitor" \
      org.opencontainers.image.description="Local operational monitor for ChatBackend" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

RUN groupadd --gid 10001 chatmonitor \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin chatmonitor \
    && mkdir -p /app /var/lib/chat-monitor \
    && chown -R 10001:10001 /app /var/lib/chat-monitor

WORKDIR /app
COPY --from=builder --chown=10001:10001 /workspace/package.json /workspace/package-lock.json ./
COPY --from=builder --chown=10001:10001 /workspace/node_modules node_modules
COPY --from=builder --chown=10001:10001 /workspace/config config
COPY --from=builder --chown=10001:10001 /workspace/migrations migrations
COPY --from=builder --chown=10001:10001 /workspace/scripts scripts
COPY --from=builder --chown=10001:10001 /workspace/src src
COPY --from=builder --chown=10001:10001 /workspace/test/fixtures test/fixtures
COPY --from=builder --chown=10001:10001 /workspace/dist dist

ENV NODE_ENV=production \
    CHAT_MONITOR_DEPLOYMENT_MODE=container \
    CHAT_MONITOR_HOST=0.0.0.0 \
    CHAT_MONITOR_PORT=8090 \
    CHAT_MONITOR_SQLITE_PATH=/var/lib/chat-monitor/chat-monitor.sqlite

EXPOSE 8090
USER 10001:10001

ENTRYPOINT ["node", "/app/src/server/main.js"]
