#!/usr/bin/env sh
set -eu
npm run verify
mkdir -p var/packages
archive="var/packages/chat-monitor-$(node -p "require('./package.json').version").tgz"
tar --exclude='./node_modules' --exclude='./dist' --exclude='./var' \
  --exclude='./test-results' --exclude='./playwright-report' -czf "$archive" .
shasum -a 256 "$archive" > "$archive.sha256"
printf '%s\n' "$archive"
