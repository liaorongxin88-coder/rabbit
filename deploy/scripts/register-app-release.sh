#!/usr/bin/env bash
set -Eeuo pipefail

fail() {
  echo "register-app-release: $*" >&2
  exit 1
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
  APP_RELEASE_API_BASE_URL=https://api.example.test \
  APP_RELEASE_ADMIN_TOKEN_FILE=/secure/path/platform-admin.jwt \
  APP_RELEASE_APK=release/rabbit-app.apk \
  APP_RELEASE_VERSION_NAME=<version-name> \
  APP_RELEASE_BUILD_NUMBER=<build-number> \
  APP_RELEASE_DOWNLOAD_URL=https://downloads.example.test/rabbit-app.apk \
  APP_RELEASE_REQUEST_ID=<idempotency-key> \
  ./deploy/scripts/register-app-release.sh [--dry-run]

Required environment variables:
  APP_RELEASE_API_BASE_URL       Existing HTTPS backend API origin.
  APP_RELEASE_ADMIN_TOKEN_FILE   File containing an existing platform-admin JWT.
  APP_RELEASE_APK                Signed APK used to derive size and SHA-256.
  APP_RELEASE_VERSION_NAME       Android version name.
  APP_RELEASE_BUILD_NUMBER       Android build number.
  APP_RELEASE_DOWNLOAD_URL       HTTPS URL for the APK.
  APP_RELEASE_REQUEST_ID         Stable idempotency key for this release attempt.

Optional environment variables:
  APP_RELEASE_NOTES_FILE         UTF-8 release notes file; defaults to empty.
  APP_RELEASE_FORCE_UPDATE=0     Set to 1 for a forced update.
  APP_RELEASE_EVIDENCE_FILE=app-release-evidence.txt
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

APP_RELEASE_API_BASE_URL="${APP_RELEASE_API_BASE_URL:-}"
APP_RELEASE_ADMIN_TOKEN_FILE="${APP_RELEASE_ADMIN_TOKEN_FILE:-}"
APP_RELEASE_APK="${APP_RELEASE_APK:-}"
APP_RELEASE_VERSION_NAME="${APP_RELEASE_VERSION_NAME:-}"
APP_RELEASE_BUILD_NUMBER="${APP_RELEASE_BUILD_NUMBER:-}"
APP_RELEASE_DOWNLOAD_URL="${APP_RELEASE_DOWNLOAD_URL:-}"
APP_RELEASE_REQUEST_ID="${APP_RELEASE_REQUEST_ID:-}"
APP_RELEASE_NOTES_FILE="${APP_RELEASE_NOTES_FILE:-}"
APP_RELEASE_FORCE_UPDATE="${APP_RELEASE_FORCE_UPDATE:-0}"
APP_RELEASE_EVIDENCE_FILE="${APP_RELEASE_EVIDENCE_FILE:-app-release-evidence.txt}"

for name in \
  APP_RELEASE_API_BASE_URL APP_RELEASE_APK APP_RELEASE_VERSION_NAME \
  APP_RELEASE_BUILD_NUMBER APP_RELEASE_DOWNLOAD_URL APP_RELEASE_REQUEST_ID; do
  [[ -n "${!name}" ]] || fail "$name is required"
done

[[ "$APP_RELEASE_API_BASE_URL" =~ ^https://[^[:space:]]+$ ]] ||
  fail "APP_RELEASE_API_BASE_URL must use HTTPS"
[[ -f "$APP_RELEASE_APK" ]] || fail "APP_RELEASE_APK does not exist"
[[ "$APP_RELEASE_VERSION_NAME" != *$'\n'* && "${#APP_RELEASE_VERSION_NAME}" -le 64 ]] ||
  fail "APP_RELEASE_VERSION_NAME is invalid"
[[ "$APP_RELEASE_BUILD_NUMBER" =~ ^[1-9][0-9]*$ ]] || fail "APP_RELEASE_BUILD_NUMBER is invalid"
[[ "$APP_RELEASE_DOWNLOAD_URL" =~ ^https://[^[:space:]]+$ && "${#APP_RELEASE_DOWNLOAD_URL}" -le 2048 ]] ||
  fail "APP_RELEASE_DOWNLOAD_URL must be an HTTPS URL of at most 2048 characters"
[[ "$APP_RELEASE_REQUEST_ID" =~ ^[A-Za-z0-9._:-]{1,64}$ ]] || fail "APP_RELEASE_REQUEST_ID is invalid"
[[ "$APP_RELEASE_FORCE_UPDATE" == "0" || "$APP_RELEASE_FORCE_UPDATE" == "1" ]] ||
  fail "APP_RELEASE_FORCE_UPDATE must be 0 or 1"

for command in jq wc; do
  command -v "$command" >/dev/null 2>&1 || fail "missing required command: $command"
done

notes_file=""
notes_file_is_temporary=0
if [[ -n "$APP_RELEASE_NOTES_FILE" ]]; then
  [[ -f "$APP_RELEASE_NOTES_FILE" ]] || fail "APP_RELEASE_NOTES_FILE does not exist"
  notes_file="$APP_RELEASE_NOTES_FILE"
else
  notes_file="$(mktemp)"
  notes_file_is_temporary=1
fi

notes_length="$(wc -m <"$notes_file" | tr -d '[:space:]')"
[[ "$notes_length" =~ ^[0-9]+$ && "$notes_length" -le 1000 ]] ||
  fail "release notes exceed 1000 characters"

apk_size_bytes="$(wc -c <"$APP_RELEASE_APK" | tr -d '[:space:]')"
[[ "$apk_size_bytes" =~ ^[1-9][0-9]*$ && "$apk_size_bytes" -le 536870912 ]] ||
  fail "APK size must be between 1 byte and 512 MB"
apk_sha256="$(sha256_file "$APP_RELEASE_APK")"
force_update=false
if [[ "$APP_RELEASE_FORCE_UPDATE" == "1" ]]; then
  force_update=true
fi

payload_file="$(mktemp)"
response_file="$(mktemp)"
cleanup() {
  rm -f "$payload_file" "$response_file"
  if [[ "$notes_file_is_temporary" == "1" ]]; then
    rm -f "$notes_file"
  fi
}
trap cleanup EXIT

jq -n \
  --arg platform ANDROID \
  --argjson buildNumber "$APP_RELEASE_BUILD_NUMBER" \
  --arg versionName "$APP_RELEASE_VERSION_NAME" \
  --arg downloadUrl "$APP_RELEASE_DOWNLOAD_URL" \
  --arg sha256 "$apk_sha256" \
  --argjson apkSizeBytes "$apk_size_bytes" \
  --rawfile releaseNotes "$notes_file" \
  --argjson forceUpdate "$force_update" \
  --arg requestId "$APP_RELEASE_REQUEST_ID" \
  '{platform: $platform, buildNumber: $buildNumber, versionName: $versionName,
    downloadUrl: $downloadUrl, sha256: $sha256, apkSizeBytes: $apkSizeBytes,
    releaseNotes: $releaseNotes, forceUpdate: $forceUpdate, requestId: $requestId}' \
  >"$payload_file"

if [[ "$DRY_RUN" == "1" ]]; then
  {
    printf 'app_release=dry-run\n'
    printf 'build_number=%s\n' "$APP_RELEASE_BUILD_NUMBER"
    printf 'version_name=%s\n' "$APP_RELEASE_VERSION_NAME"
    printf 'apk_size_bytes=%s\n' "$apk_size_bytes"
    printf 'apk_sha256=%s\n' "$apk_sha256"
    printf 'request_id=%s\n' "$APP_RELEASE_REQUEST_ID"
  } >"$APP_RELEASE_EVIDENCE_FILE"
  exit 0
fi

[[ -n "$APP_RELEASE_ADMIN_TOKEN_FILE" ]] || fail "APP_RELEASE_ADMIN_TOKEN_FILE is required"
[[ -s "$APP_RELEASE_ADMIN_TOKEN_FILE" ]] || fail "APP_RELEASE_ADMIN_TOKEN_FILE is missing or empty"
command -v curl >/dev/null 2>&1 || fail "missing required command: curl"
admin_token="$(tr -d '\r\n' <"$APP_RELEASE_ADMIN_TOKEN_FILE")"
[[ -n "$admin_token" ]] || fail "APP_RELEASE_ADMIN_TOKEN_FILE is empty"

http_status="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' \
  --connect-timeout 5 --max-time 30 \
  --request POST \
  --header "Authorization: Bearer $admin_token" \
  --header 'Content-Type: application/json' \
  --data-binary "@$payload_file" \
  "${APP_RELEASE_API_BASE_URL%/}/api/admin/app-updates")"
[[ "$http_status" == "200" ]] || fail "app release API returned HTTP $http_status"

jq -e \
  --argjson buildNumber "$APP_RELEASE_BUILD_NUMBER" \
  --arg versionName "$APP_RELEASE_VERSION_NAME" \
  --arg downloadUrl "$APP_RELEASE_DOWNLOAD_URL" \
  --arg sha256 "$apk_sha256" \
  --argjson apkSizeBytes "$apk_size_bytes" \
  --argjson forceUpdate "$force_update" \
  --arg requestId "$APP_RELEASE_REQUEST_ID" \
  '.code == 0 and
   .data.buildNumber == $buildNumber and
   .data.versionName == $versionName and
   .data.downloadUrl == $downloadUrl and
   (.data.sha256 | ascii_downcase) == $sha256 and
   .data.apkSizeBytes == $apkSizeBytes and
   .data.forceUpdate == $forceUpdate and
   .data.requestId == $requestId' \
  "$response_file" >/dev/null ||
  fail "app release API response does not match the requested release"

{
  printf 'app_release=registered\n'
  printf 'release_id=%s\n' "$(jq -r '.data.id' "$response_file")"
  printf 'build_number=%s\n' "$APP_RELEASE_BUILD_NUMBER"
  printf 'version_name=%s\n' "$APP_RELEASE_VERSION_NAME"
  printf 'apk_size_bytes=%s\n' "$apk_size_bytes"
  printf 'apk_sha256=%s\n' "$apk_sha256"
  printf 'request_id=%s\n' "$APP_RELEASE_REQUEST_ID"
  printf 'http=%s\n' "$http_status"
} >"$APP_RELEASE_EVIDENCE_FILE"
