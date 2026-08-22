#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"

cd "$ROOT_DIR"

mvn --batch-mode --no-transfer-progress --file backend/pom.xml test
corepack pnpm --dir admin install --frozen-lockfile
corepack pnpm --dir admin lint
corepack pnpm --dir admin test
corepack pnpm --dir admin build
(
  cd app
  ./rabbit check
)
