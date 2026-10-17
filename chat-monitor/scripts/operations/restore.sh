#!/usr/bin/env sh
set -eu
backup=${1:?Usage: npm run restore -- var/backups/chat-monitor-<timestamp>.sqlite}
case "$backup" in
  var/backups/*.sqlite) ;;
  *) echo "Backup must be a .sqlite file under var/backups" >&2; exit 2 ;;
esac
test -f "$backup"
test -f "$backup.sha256"
name=$(basename "$backup")
docker compose stop monitor
docker compose run --rm --no-deps --entrypoint node monitor scripts/operations/restore-monitor-db.js \
  "/var/backups/$name" /var/lib/chat-monitor/chat-monitor.sqlite
docker compose up --detach --wait monitor
