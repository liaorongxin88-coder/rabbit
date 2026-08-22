#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
REMOTE_SCRIPT="$ROOT_DIR/deploy/remote/activate-backend.sh"
COMPOSE_OVERLAY="$ROOT_DIR/deploy/compose.backend-image.yml"

fail() {
  echo "deploy-backend: $*" >&2
  exit 1
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required"
}

quote() {
  printf '%q' "$1"
}

DEPLOY_HOST="${DEPLOY_HOST:-}"
DEPLOY_PATH="${DEPLOY_PATH:-}"
BACKEND_IMAGE="${BACKEND_IMAGE:-}"
SOURCE_SHA="${SOURCE_SHA:-}"
DEPLOY_USER="${DEPLOY_USER:-root}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
DEPLOY_PUBLIC_URL="${DEPLOY_PUBLIC_URL:-}"
DEPLOY_ROLLBACK_MODE="${DEPLOY_ROLLBACK_MODE:-manual}"
DEPLOY_BACKUP_PATH="${DEPLOY_BACKUP_PATH:-${DEPLOY_PATH%/}/backups}"
DEPLOY_EVIDENCE_FILE="${DEPLOY_EVIDENCE_FILE:-deploy-evidence.txt}"
RUN_ID="${GITHUB_RUN_ID:-manual}-$(date -u +%Y%m%dT%H%M%SZ)"

[[ -n "$DEPLOY_HOST" ]] || fail "DEPLOY_HOST is required"
[[ -n "$DEPLOY_PATH" ]] || fail "DEPLOY_PATH is required"
[[ -n "$BACKEND_IMAGE" ]] || fail "BACKEND_IMAGE is required"
[[ -n "$SOURCE_SHA" ]] || fail "SOURCE_SHA is required"
[[ "$DEPLOY_USER" =~ ^[A-Za-z0-9._-]+$ ]] || fail "DEPLOY_USER contains unsupported characters"
[[ "$DEPLOY_HOST" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "DEPLOY_HOST contains unsupported characters"
[[ "$DEPLOY_PORT" =~ ^[0-9]+$ ]] || fail "DEPLOY_PORT must be numeric"
[[ "$DEPLOY_PATH" == /* && "$DEPLOY_PATH" != *[[:space:]]* ]] || fail "DEPLOY_PATH must be an absolute path without spaces"
[[ "$DEPLOY_BACKUP_PATH" == /* && "$DEPLOY_BACKUP_PATH" != *[[:space:]]* ]] || fail "DEPLOY_BACKUP_PATH must be an absolute path without spaces"
case "${DEPLOY_PATH%/}" in
  "" | / | /bin | /boot | /dev | /etc | /home | /lib | /lib64 | /opt | /root | /sbin | /tmp | /usr | /var)
    fail "DEPLOY_PATH is too broad"
    ;;
esac
[[ "$BACKEND_IMAGE" =~ ^ghcr\.io/[a-z0-9._/-]+@sha256:[a-f0-9]{64}$ ]] || fail "BACKEND_IMAGE must be a lowercase GHCR image pinned by sha256 digest"
[[ "$SOURCE_SHA" =~ ^[a-f0-9]{40}$ ]] || fail "SOURCE_SHA must be a full Git commit SHA"
[[ "$DEPLOY_ROLLBACK_MODE" == "manual" || "$DEPLOY_ROLLBACK_MODE" == "code" ]] || fail "DEPLOY_ROLLBACK_MODE must be manual or code"
if [[ -n "$DEPLOY_PUBLIC_URL" ]]; then
  [[ "$DEPLOY_PUBLIC_URL" =~ ^https://[^[:space:]]+$ ]] || fail "DEPLOY_PUBLIC_URL must use HTTPS"
fi

for command in ssh scp tee; do
  command -v "$command" >/dev/null 2>&1 || fail "missing required command: $command"
done
[[ -f "$REMOTE_SCRIPT" ]] || fail "missing $REMOTE_SCRIPT"
[[ -f "$COMPOSE_OVERLAY" ]] || fail "missing $COMPOSE_OVERLAY"

ssh_options=(-p "$DEPLOY_PORT" -o BatchMode=yes)
scp_options=(-P "$DEPLOY_PORT" -o BatchMode=yes)
if [[ -n "${DEPLOY_SSH_KEY:-}" ]]; then
  [[ -f "$DEPLOY_SSH_KEY" ]] || fail "DEPLOY_SSH_KEY does not exist"
  ssh_options+=(-i "$DEPLOY_SSH_KEY")
  scp_options+=(-i "$DEPLOY_SSH_KEY")
fi

remote="$DEPLOY_USER@$DEPLOY_HOST"
incoming_dir="${DEPLOY_PATH%/}/.deploy/incoming/$RUN_ID"
remote_script="$incoming_dir/activate-backend.sh"
remote_overlay="$incoming_dir/compose.backend-image.yml"

cleanup_remote() {
  set +e
  # Client-side quoting is intentional; only the quoted value is sent to SSH.
  # shellcheck disable=SC2029
  ssh "${ssh_options[@]}" "$remote" "rm -rf $(quote "$incoming_dir")" >/dev/null 2>&1 || true
}
trap cleanup_remote EXIT

# shellcheck disable=SC2029
ssh "${ssh_options[@]}" "$remote" "mkdir -p $(quote "$incoming_dir")"
scp "${scp_options[@]}" "$REMOTE_SCRIPT" "$remote:$remote_script"
scp "${scp_options[@]}" "$COMPOSE_OVERLAY" "$remote:$remote_overlay"

if [[ -n "${DEPLOY_REGISTRY_TOKEN:-}" || -n "${DEPLOY_REGISTRY_USERNAME:-}" ]]; then
  require_env DEPLOY_REGISTRY_TOKEN
  require_env DEPLOY_REGISTRY_USERNAME
  # shellcheck disable=SC2029
  printf '%s' "$DEPLOY_REGISTRY_TOKEN" | ssh "${ssh_options[@]}" "$remote" \
    "docker login ghcr.io --username $(quote "$DEPLOY_REGISTRY_USERNAME") --password-stdin >/dev/null"
fi

remote_command="bash $(quote "$remote_script") $(quote "$DEPLOY_PATH") $(quote "$BACKEND_IMAGE") $(quote "$DEPLOY_PUBLIC_URL") $(quote "$DEPLOY_ROLLBACK_MODE") $(quote "$SOURCE_SHA") $(quote "$remote_overlay") $(quote "$DEPLOY_BACKUP_PATH")"
# shellcheck disable=SC2029
ssh "${ssh_options[@]}" "$remote" "$remote_command" | tee "$DEPLOY_EVIDENCE_FILE"
