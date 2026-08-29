#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_PATH="${1:-}"
BUNDLE_FILE="${2:-}"
EXPECTED_SHA256="${3:-}"
RELEASE_ID="${4:-}"
SOURCE_SHA="${5:-}"
PUBLIC_URL="${6:-}"
NGINX_CONFIG="${7:-}"

fail() {
  echo "activate-admin: $*" >&2
  exit 1
}

sha256_file() {
  sha256sum "$1" | awk '{print $1}'
}

archive_is_safe() {
  local entry
  while IFS= read -r entry; do
    case "$entry" in
    "" | /* | ../* | */../* | ..)
      return 1
      ;;
    esac
  done < <(tar -tzf "$BUNDLE_FILE")

  tar -tvzf "$BUNDLE_FILE" | awk '
    substr($1, 1, 1) != "-" && substr($1, 1, 1) != "d" { exit 1 }
  '
}

nginx_has_api_proxy() {
  awk '
    function brace_delta(line, opens, closes) {
      opens = gsub(/\{/, "{", line)
      closes = gsub(/\}/, "}", line)
      return opens - closes
    }
    {
      sub(/#.*/, "")
      if (!in_location) {
        if ($0 ~ /^[[:space:]]*location[[:space:]]+(\^~[[:space:]]+)?\/api\/?[[:space:]]*\{/) {
          in_location = 1
          depth = brace_delta($0)
          has_proxy = 0
        }
        next
      }
      if ($0 ~ /proxy_pass[[:space:]]+[^;]+;/) {
        has_proxy = 1
      }
      depth += brace_delta($0)
      if (depth <= 0) {
        if (has_proxy) {
          found = 1
          exit
        }
        in_location = 0
      }
    }
    END { exit !found }
  ' "$NGINX_CONFIG"
}

for command in awk curl dirname gzip grep install ln mkdir mv nginx readlink rm sha256sum tar tee; do
  command -v "$command" >/dev/null 2>&1 || fail "missing required command: $command"
done

[[ "$DEPLOY_PATH" == /* && -d "$DEPLOY_PATH" ]] || fail "invalid DEPLOY_PATH"
[[ "$BUNDLE_FILE" == /* && -f "$BUNDLE_FILE" ]] || fail "invalid BUNDLE_FILE"
[[ "$EXPECTED_SHA256" =~ ^[a-f0-9]{64}$ ]] || fail "invalid expected SHA-256"
[[ "$RELEASE_ID" =~ ^[A-Za-z0-9._-]{1,64}$ ]] || fail "invalid release ID"
[[ "$SOURCE_SHA" =~ ^[a-f0-9]{40}$ ]] || fail "invalid source SHA"
[[ "$PUBLIC_URL" =~ ^https://[^[:space:]]+$ ]] || fail "PUBLIC_URL must use HTTPS"
[[ "$NGINX_CONFIG" == /* && -f "$NGINX_CONFIG" ]] || fail "invalid NGINX_CONFIG"

actual_sha256="$(sha256_file "$BUNDLE_FILE")"
[[ "$actual_sha256" == "$EXPECTED_SHA256" ]] || fail "bundle SHA-256 does not match"
archive_is_safe || fail "bundle contains unsupported archive entries"

STATE_DIR="${DEPLOY_PATH%/}/.deploy"
STATIC_RELEASES_DIR="${DEPLOY_PATH%/}/releases"
EVIDENCE_DIR="$STATE_DIR/releases"
BACKUP_DIR="$STATE_DIR/backups"
LOCK_DIR="$STATE_DIR/admin-lock"
CURRENT_LINK="${DEPLOY_PATH%/}/current"
ACTIVATED_AT="$(date -u +%Y%m%dT%H%M%SZ)"
RELEASE_DIR="$STATIC_RELEASES_DIR/$RELEASE_ID"
EVIDENCE_FILE="$EVIDENCE_DIR/$RELEASE_ID-$ACTIVATED_AT.txt"
BACKUP_FILE="$BACKUP_DIR/admin-$RELEASE_ID-$ACTIVATED_AT.tar.gz"
temporary_release="$STATIC_RELEASES_DIR/.${RELEASE_ID}.tmp.$$"
previous_target=""
activated=0
index_response=""
api_response=""

restore_previous() {
  local replacement="$CURRENT_LINK.rollback.$$"
  if [[ -n "$previous_target" ]]; then
    ln -s "$previous_target" "$replacement"
    mv -Tf "$replacement" "$CURRENT_LINK"
  else
    rm -f "$CURRENT_LINK"
  fi
}

on_exit() {
  local status=$?
  trap - EXIT
  set +e
  if [[ "$status" != "0" && "$activated" == "1" ]]; then
    restore_previous
    printf 'rollback=restored-previous-static-release\n' >&2
  fi
  rm -f "$index_response" "$api_response"
  rm -rf "$temporary_release" "$LOCK_DIR"
  exit "$status"
}
trap on_exit EXIT

mkdir -p "$STATE_DIR" "$STATIC_RELEASES_DIR" "$EVIDENCE_DIR" "$BACKUP_DIR"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  fail "another admin deployment holds $LOCK_DIR"
fi

if [[ -L "$CURRENT_LINK" ]]; then
  previous_target="$(readlink "$CURRENT_LINK")"
elif [[ -e "$CURRENT_LINK" ]]; then
  fail "$CURRENT_LINK must be a symlink managed by this script"
fi

if [[ -e "$RELEASE_DIR" ]]; then
  [[ -f "$RELEASE_DIR/.rabbit-bundle.sha256" ]] || fail "release ID already exists without bundle metadata"
  [[ "$(<"$RELEASE_DIR/.rabbit-bundle.sha256")" == "$EXPECTED_SHA256" ]] ||
    fail "release ID already exists with a different bundle"
else
  mkdir "$temporary_release"
  tar --extract --gzip --file "$BUNDLE_FILE" --directory "$temporary_release" \
    --no-same-owner --no-same-permissions
  [[ -f "$temporary_release/index.html" ]] || fail "bundle does not contain index.html"
  printf '%s\n' "$EXPECTED_SHA256" >"$temporary_release/.rabbit-bundle.sha256"
  mv "$temporary_release" "$RELEASE_DIR"
fi

if [[ -n "$previous_target" ]]; then
  previous_path="$previous_target"
  if [[ "$previous_path" != /* ]]; then
    previous_path="$(dirname "$CURRENT_LINK")/$previous_path"
  fi
  [[ -d "$previous_path" ]] || fail "current release target is missing"
  temporary_backup="$BACKUP_FILE.tmp"
  tar --create --gzip --file "$temporary_backup" --directory "$previous_path" .
  gzip -t "$temporary_backup"
  mv "$temporary_backup" "$BACKUP_FILE"
else
  BACKUP_FILE="none"
fi

nginx -t -c "$NGINX_CONFIG"
nginx_has_api_proxy || fail "Nginx configuration has no /api/ location with proxy_pass"

next_link="$CURRENT_LINK.next.$$"
ln -s "$RELEASE_DIR" "$next_link"
mv -Tf "$next_link" "$CURRENT_LINK"
activated=1

index_response="$(mktemp)"
api_response="$(mktemp)"

index_status="$(curl --silent --show-error --output "$index_response" --write-out '%{http_code}' \
  --connect-timeout 5 --max-time 20 \
  --header 'Accept-Encoding: identity' --header 'Cache-Control: no-cache' \
  "${PUBLIC_URL%/}/?release=$RELEASE_ID")"
[[ "$index_status" == "200" ]] || fail "public index probe returned HTTP $index_status"
[[ "$(sha256_file "$index_response")" == "$(sha256_file "$RELEASE_DIR/index.html")" ]] ||
  fail "public index does not match the activated release"

api_status="$(curl --silent --show-error --output "$api_response" --write-out '%{http_code}' \
  --connect-timeout 5 --max-time 20 \
  --header 'Cache-Control: no-cache' \
  "${PUBLIC_URL%/}/api/houses")"
[[ "$api_status" == "200" || "$api_status" == "401" ]] ||
  fail "same-origin API probe returned HTTP $api_status"
grep -Eq '"code"[[:space:]]*:[[:space:]]*401' "$api_response" ||
  fail "same-origin API probe did not return business code 401"
rm -f "$index_response" "$api_response"
index_response=""
api_response=""

{
  printf 'admin_deploy=healthy\n'
  printf 'release_id=%s\n' "$RELEASE_ID"
  printf 'source_sha=%s\n' "$SOURCE_SHA"
  printf 'bundle_sha256=%s\n' "$EXPECTED_SHA256"
  printf 'current=%s\n' "$RELEASE_DIR"
  printf 'previous=%s\n' "${previous_target:-none}"
  printf 'backup=%s\n' "$BACKUP_FILE"
  printf 'nginx_config=%s\n' "$NGINX_CONFIG"
  printf 'index_http=%s\n' "$index_status"
  printf 'api_http=%s\n' "$api_status"
} | tee "$EVIDENCE_FILE"
