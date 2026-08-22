#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"

cd "$ROOT_DIR"

git diff --check
node scripts/ci/check-markdown-links.mjs
bash -n scripts/ci/*.sh deploy/scripts/*.sh deploy/remote/*.sh

APP_JWT_SECRET=ci-only-app-jwt-secret-0123456789abcdef \
APP_ADMIN_JWT_SECRET=ci-only-admin-jwt-secret-fedcba9876543210 \
APP_PHONE_HASH_SECRET=ci-only-phone-hash-secret-0123456789abcdef \
APP_NFC_TAG_SIGNING_KEYS=1=Y2ktb25seS1uZmMtc2lnbmluZy1rZXktMDEyMzQ1Njc4OWFiY2RlZg== \
docker compose config --quiet

if command -v actionlint >/dev/null 2>&1; then
  actionlint -color
else
  docker run --rm \
    --volume "$ROOT_DIR:/repo" \
    --workdir /repo \
    rhysd/actionlint:1.7.12 -color
fi
