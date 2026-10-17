#!/usr/bin/env sh
set -eu
npm run container:config
npm run container:up
npm run container:status
