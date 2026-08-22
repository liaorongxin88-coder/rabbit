#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"

cd "$ROOT_DIR"

docker build --tag rabbit-backend:harness backend
corepack pnpm --dir admin build
(
  cd app
  ./rabbit apk test --release
)
