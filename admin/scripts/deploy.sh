#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ADMIN_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
DIST_DIR="${ADMIN_DIR}/dist"
ENV_FILE="${DEPLOY_ENV_FILE:-${ADMIN_DIR}/.env.deploy}"

if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

DEPLOY_USER="${DEPLOY_USER:-root}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
DEPLOY_HOST="${DEPLOY_HOST:-}"
DEPLOY_PATH="${DEPLOY_PATH:-}"
DEPLOY_SKIP_BUILD="${DEPLOY_SKIP_BUILD:-0}"

usage() {
  cat <<'EOF'
Usage:
  DEPLOY_HOST=server.example.com DEPLOY_PATH=/var/www/rabbit-admin pnpm --dir admin deploy

Optional environment variables:
  DEPLOY_USER=root                 SSH user, defaults to root.
  DEPLOY_PORT=22                   SSH port.
  DEPLOY_SSH_KEY=~/.ssh/id_ed25519 SSH private key path.
  DEPLOY_API_BASE_URL=https://...  Build-time VITE_API_BASE_URL.
  DEPLOY_SKIP_BUILD=1              Upload existing admin/dist without rebuilding.
  DEPLOY_ENV_FILE=admin/.env.deploy Load a custom env file before deploying.

The target directory is synchronized with admin/dist by rsync --delete-delay.
Host-managed files such as /.user.ini are preserved.
EOF
}

fail() {
  echo "deploy: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

quote() {
  printf "%q" "$1"
}

require_command pnpm
require_command ssh
require_command rsync

[[ -n "${DEPLOY_HOST}" ]] || {
  usage
  fail "DEPLOY_HOST is required"
}

[[ -n "${DEPLOY_PATH}" ]] || {
  usage
  fail "DEPLOY_PATH is required"
}

[[ "${DEPLOY_PATH}" == /* ]] || fail "DEPLOY_PATH must be an absolute server path"
[[ "${DEPLOY_PATH}" != *[[:space:]]* ]] || fail "DEPLOY_PATH must not contain whitespace"

case "${DEPLOY_PATH%/}" in
  "" | "/" | "/bin" | "/boot" | "/dev" | "/etc" | "/home" | "/lib" | "/lib64" | "/opt" | "/root" | "/sbin" | "/tmp" | "/usr" | "/var" | "/var/www")
    fail "DEPLOY_PATH is too broad: ${DEPLOY_PATH}"
    ;;
esac

SSH_OPTS=(-p "${DEPLOY_PORT}")
SSH_CMD="ssh -p $(quote "${DEPLOY_PORT}")"

if [[ -n "${DEPLOY_SSH_KEY:-}" ]]; then
  SSH_OPTS+=(-i "${DEPLOY_SSH_KEY}")
  SSH_CMD+=" -i $(quote "${DEPLOY_SSH_KEY}")"
fi

REMOTE="${DEPLOY_USER}@${DEPLOY_HOST}"
REMOTE_PATH="${DEPLOY_PATH%/}"
REMOTE_PATH_QUOTED="$(quote "${REMOTE_PATH}")"
RSYNC_PROTECTED_FILTERS=(
  "--exclude=/.user.ini"
)

echo "deploy: admin dir  ${ADMIN_DIR}"
echo "deploy: target     ${REMOTE}:${REMOTE_PATH}/"

if [[ "${DEPLOY_SKIP_BUILD}" != "1" ]]; then
  if [[ -n "${DEPLOY_API_BASE_URL:-}" ]]; then
    echo "deploy: build      VITE_API_BASE_URL=${DEPLOY_API_BASE_URL}"
    VITE_API_BASE_URL="${DEPLOY_API_BASE_URL}" pnpm --dir "${ADMIN_DIR}" build
  else
    echo "deploy: build      same-origin /api"
    pnpm --dir "${ADMIN_DIR}" build
  fi
else
  echo "deploy: build      skipped"
fi

[[ -f "${DIST_DIR}/index.html" ]] || fail "missing ${DIST_DIR}/index.html; run pnpm --dir admin build first"

echo "deploy: prepare    ${REMOTE_PATH}"
ssh "${SSH_OPTS[@]}" "${REMOTE}" "mkdir -p ${REMOTE_PATH_QUOTED}"

echo "deploy: upload     rsync --delete-delay"
rsync \
  -az \
  --delete-delay \
  --human-readable \
  --info=stats2 \
  --chmod=D755,F644 \
  "${RSYNC_PROTECTED_FILTERS[@]}" \
  -e "${SSH_CMD}" \
  "${DIST_DIR}/" \
  "${REMOTE}:${REMOTE_PATH}/"

echo "deploy: verify     index.html"
ssh "${SSH_OPTS[@]}" "${REMOTE}" "test -f ${REMOTE_PATH_QUOTED}/index.html"

echo "deploy: done       ${REMOTE}:${REMOTE_PATH}/"
