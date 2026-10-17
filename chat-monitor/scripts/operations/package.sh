#!/usr/bin/env sh
set -eu
npm run verify
mkdir -p var/packages
archive="var/packages/chat-monitor-$(node -p "require('./package.json').version").tgz"
tar --exclude='./node_modules' --exclude='./dist' --exclude='./var' \
  --exclude='./test-results' --exclude='./playwright-report' \
  --exclude='./.env' --exclude='./.env.local' --exclude='./.env.*.local' --exclude='./secrets' \
  --exclude='./environments/environments.json' -czf "$archive" .
if tar -tzf "$archive" | grep -Eq '(^|/)\.env$|(^|/)\.env\.local$|(^|/)\.env\.[^/]+\.local$|(^|/)secrets(/|$)|(^|/)environments/environments\.json$'; then
  printf '%s\n' 'Package validation failed: archive contains an active environment or secret path.' >&2
  exit 1
fi
shasum -a 256 "$archive" > "$archive.sha256"
printf '%s\n' "$archive"
