#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
REMOTE_SCRIPT="$ROOT_DIR/deploy/remote/activate-admin.sh"

fail() {
  echo "deploy-admin: $*" >&2
  exit 1
}

quote() {
  printf '%q' "$1"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    fail "missing required command: sha256sum or shasum"
  fi
}

usage() {
  cat <<'EOF'
Usage:
  ADMIN_DEPLOY_HOST=deploy.example.test \
  ADMIN_DEPLOY_USER=deployer \
  ADMIN_DEPLOY_PATH=/srv/rabbit-admin \
  ADMIN_BUNDLE=release/rabbit-admin.tar.gz \
  ADMIN_BUNDLE_SHA256=<sha256> \
  ADMIN_RELEASE_ID=<release-id> \
  ADMIN_SOURCE_SHA=<full-git-sha> \
  ADMIN_PUBLIC_URL=https://admin.example.test \
  ADMIN_NGINX_CONFIG=/etc/nginx/nginx.conf \
  ./deploy/scripts/deploy-admin.sh [--dry-run]

Required environment variables:
  ADMIN_DEPLOY_HOST        SSH host.
  ADMIN_DEPLOY_USER        SSH user.
  ADMIN_DEPLOY_PATH        Remote release root. Nginx must serve <path>/current.
  ADMIN_BUNDLE             Local Admin tar.gz artifact.
  ADMIN_BUNDLE_SHA256      Expected SHA-256 of the artifact.
  ADMIN_RELEASE_ID         Immutable release identifier.
  ADMIN_SOURCE_SHA         Full source Git SHA.
  ADMIN_PUBLIC_URL         HTTPS Admin origin used for post-activation probes.
  ADMIN_NGINX_CONFIG       Remote Nginx main configuration path.

Optional environment variables:
  ADMIN_DEPLOY_PORT=22
  ADMIN_DEPLOY_SSH_KEY     Local SSH key path.
  ADMIN_DEPLOY_EVIDENCE_FILE=admin-deploy-evidence.txt
  ADMIN_DEPLOY_RUN_ID      Safe run identifier; defaults to release ID plus UTC time.
EOF
}

DRY_RUN=0
while (($# > 0)); do
  case "$1" in
  --dry-run)
    DRY_RUN=1
    ;;
  --help | -h)
    usage
    exit 0
    ;;
  *)
    usage >&2
    fail "unknown argument: $1"
    ;;
  esac
  shift
done

ADMIN_DEPLOY_HOST="${ADMIN_DEPLOY_HOST:-}"
ADMIN_DEPLOY_USER="${ADMIN_DEPLOY_USER:-}"
ADMIN_DEPLOY_PATH="${ADMIN_DEPLOY_PATH:-}"
ADMIN_BUNDLE="${ADMIN_BUNDLE:-}"
ADMIN_BUNDLE_SHA256="${ADMIN_BUNDLE_SHA256:-}"
ADMIN_RELEASE_ID="${ADMIN_RELEASE_ID:-}"
ADMIN_SOURCE_SHA="${ADMIN_SOURCE_SHA:-}"
ADMIN_PUBLIC_URL="${ADMIN_PUBLIC_URL:-}"
ADMIN_NGINX_CONFIG="${ADMIN_NGINX_CONFIG:-}"
ADMIN_DEPLOY_PORT="${ADMIN_DEPLOY_PORT:-22}"
ADMIN_DEPLOY_EVIDENCE_FILE="${ADMIN_DEPLOY_EVIDENCE_FILE:-admin-deploy-evidence.txt}"
ADMIN_DEPLOY_RUN_ID="${ADMIN_DEPLOY_RUN_ID:-${ADMIN_RELEASE_ID}-$(date -u +%Y%m%dT%H%M%SZ)}"

for name in \
  ADMIN_DEPLOY_HOST ADMIN_DEPLOY_USER ADMIN_DEPLOY_PATH ADMIN_BUNDLE \
  ADMIN_BUNDLE_SHA256 ADMIN_RELEASE_ID ADMIN_SOURCE_SHA ADMIN_PUBLIC_URL ADMIN_NGINX_CONFIG; do
  [[ -n "${!name}" ]] || fail "$name is required"
done

[[ "$ADMIN_DEPLOY_HOST" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "ADMIN_DEPLOY_HOST contains unsupported characters"
[[ "$ADMIN_DEPLOY_USER" =~ ^[A-Za-z0-9._-]+$ ]] || fail "ADMIN_DEPLOY_USER contains unsupported characters"
[[ "$ADMIN_DEPLOY_PORT" =~ ^[0-9]+$ ]] || fail "ADMIN_DEPLOY_PORT must be numeric"
[[ "$ADMIN_DEPLOY_PATH" == /* && "$ADMIN_DEPLOY_PATH" != *[[:space:]]* ]] ||
  fail "ADMIN_DEPLOY_PATH must be an absolute path without spaces"
[[ "$ADMIN_NGINX_CONFIG" == /* && "$ADMIN_NGINX_CONFIG" != *[[:space:]]* ]] ||
  fail "ADMIN_NGINX_CONFIG must be an absolute path without spaces"
case "${ADMIN_DEPLOY_PATH%/}" in
"" | / | /bin | /boot | /dev | /etc | /home | /lib | /lib64 | /opt | /root | /sbin | /tmp | /usr | /var)
  fail "ADMIN_DEPLOY_PATH is too broad"
  ;;
esac
[[ -f "$ADMIN_BUNDLE" ]] || fail "ADMIN_BUNDLE does not exist"
[[ "$ADMIN_BUNDLE" == *.tar.gz ]] || fail "ADMIN_BUNDLE must be a .tar.gz file"
[[ "$ADMIN_BUNDLE_SHA256" =~ ^[a-f0-9]{64}$ ]] || fail "ADMIN_BUNDLE_SHA256 must be lowercase SHA-256"
[[ "$ADMIN_RELEASE_ID" =~ ^[A-Za-z0-9._-]{1,64}$ ]] || fail "ADMIN_RELEASE_ID is invalid"
[[ "$ADMIN_DEPLOY_RUN_ID" =~ ^[A-Za-z0-9._-]{1,128}$ ]] || fail "ADMIN_DEPLOY_RUN_ID is invalid"
[[ "$ADMIN_SOURCE_SHA" =~ ^[a-f0-9]{40}$ ]] || fail "ADMIN_SOURCE_SHA must be a full Git SHA"
[[ "$ADMIN_PUBLIC_URL" =~ ^https://[^[:space:]]+$ ]] || fail "ADMIN_PUBLIC_URL must use HTTPS"

actual_sha256="$(sha256_file "$ADMIN_BUNDLE")"
[[ "$actual_sha256" == "$ADMIN_BUNDLE_SHA256" ]] || fail "ADMIN_BUNDLE_SHA256 does not match ADMIN_BUNDLE"

{
  printf 'admin_deploy=%s\n' "$([[ "$DRY_RUN" == "1" ]] && printf 'dry-run' || printf 'requested')"
  printf 'release_id=%s\n' "$ADMIN_RELEASE_ID"
  printf 'source_sha=%s\n' "$ADMIN_SOURCE_SHA"
  printf 'bundle_sha256=%s\n' "$actual_sha256"
  printf 'public_url=%s\n' "$ADMIN_PUBLIC_URL"
} >"$ADMIN_DEPLOY_EVIDENCE_FILE"

if [[ "$DRY_RUN" == "1" ]]; then
  printf 'remote_path=%s\n' "$ADMIN_DEPLOY_PATH" | tee -a "$ADMIN_DEPLOY_EVIDENCE_FILE"
  printf 'nginx_config=%s\n' "$ADMIN_NGINX_CONFIG" | tee -a "$ADMIN_DEPLOY_EVIDENCE_FILE"
  exit 0
fi

for command in ssh scp tee; do
  command -v "$command" >/dev/null 2>&1 || fail "missing required command: $command"
done
[[ -f "$REMOTE_SCRIPT" ]] || fail "missing $REMOTE_SCRIPT"

ssh_options=(-p "$ADMIN_DEPLOY_PORT" -o BatchMode=yes)
scp_options=(-P "$ADMIN_DEPLOY_PORT" -o BatchMode=yes)
if [[ -n "${ADMIN_DEPLOY_SSH_KEY:-}" ]]; then
  [[ -f "$ADMIN_DEPLOY_SSH_KEY" ]] || fail "ADMIN_DEPLOY_SSH_KEY does not exist"
  ssh_options+=(-i "$ADMIN_DEPLOY_SSH_KEY")
  scp_options+=(-i "$ADMIN_DEPLOY_SSH_KEY")
fi

remote="$ADMIN_DEPLOY_USER@$ADMIN_DEPLOY_HOST"
incoming_dir="${ADMIN_DEPLOY_PATH%/}/.deploy/incoming/$ADMIN_DEPLOY_RUN_ID"
remote_script="$incoming_dir/activate-admin.sh"
remote_bundle="$incoming_dir/admin-bundle.tar.gz"
remote_created=0

cleanup_remote() {
  local status=$?
  if [[ "$remote_created" == "1" ]]; then
    set +e
    # shellcheck disable=SC2029
    ssh "${ssh_options[@]}" "$remote" "rm -rf $(quote "$incoming_dir")" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap cleanup_remote EXIT

# shellcheck disable=SC2029
ssh "${ssh_options[@]}" "$remote" "mkdir -p $(quote "$incoming_dir")"
remote_created=1
scp "${scp_options[@]}" "$REMOTE_SCRIPT" "$remote:$remote_script"
scp "${scp_options[@]}" "$ADMIN_BUNDLE" "$remote:$remote_bundle"

remote_command="bash $(quote "$remote_script") $(quote "$ADMIN_DEPLOY_PATH") $(quote "$remote_bundle") $(quote "$actual_sha256") $(quote "$ADMIN_RELEASE_ID") $(quote "$ADMIN_SOURCE_SHA") $(quote "$ADMIN_PUBLIC_URL") $(quote "$ADMIN_NGINX_CONFIG")"
# shellcheck disable=SC2029
ssh "${ssh_options[@]}" "$remote" "$remote_command" | tee -a "$ADMIN_DEPLOY_EVIDENCE_FILE"
