#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"

cd "$ROOT_DIR"

CACHE_IT_HOST="${CACHE_IT_HOST:-127.0.0.1}"
CACHE_IT_PORT="${CACHE_IT_PORT:-6379}"

mvn --batch-mode --no-transfer-progress --file backend/pom.xml -Pe2e \
  "-Dcache.it.host=$CACHE_IT_HOST" \
  "-Dcache.it.port=$CACHE_IT_PORT" \
  verify
