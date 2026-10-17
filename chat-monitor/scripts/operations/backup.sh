#!/usr/bin/env sh
set -eu
mkdir -p var/backups
docker compose exec -T monitor node scripts/operations/backup-monitor-db.js \
  /var/lib/chat-monitor/chat-monitor.sqlite /var/backups
