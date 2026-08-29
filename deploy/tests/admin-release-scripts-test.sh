#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
TEMP_DIR="$(mktemp -d)"
BIN_DIR="$TEMP_DIR/bin"
SITE_DIR="$TEMP_DIR/site"
SOURCE_SHA="0123456789abcdef0123456789abcdef01234567"

cleanup() {
    rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

fail() {
    echo "admin-release-scripts-test: $*" >&2
    exit 1
}

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

make_bundle() {
    local content="$1"
    local bundle="$2"
    local build_dir="$TEMP_DIR/build-${content}"
    mkdir -p "$build_dir"
    printf '<!doctype html><title>%s</title>' "$content" >"$build_dir/index.html"
    tar -C "$build_dir" -czf "$bundle" .
}

mkdir -p "$BIN_DIR" "$SITE_DIR"

cat >"$BIN_DIR/nginx" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

cat >"$BIN_DIR/mv" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "-Tf" ]]; then
  shift
  exec /bin/mv -f "$@"
fi
if [[ "${1:-}" == "-T" ]]; then
  shift
fi
exec /bin/mv "$@"
EOF

cat >"$BIN_DIR/tar" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
args=()
while (($# > 0)); do
  case "$1" in
    --extract)
      args+=(-x)
      ;;
    --create)
      args+=(-c)
      ;;
    --gzip)
      args+=(-z)
      ;;
    --file)
      args+=(-f "$2")
      shift
      ;;
    --directory)
      args+=(-C "$2")
      shift
      ;;
    --no-same-owner|--no-same-permissions)
      ;;
    *)
      args+=("$1")
      ;;
  esac
  shift
done
exec /usr/bin/tar "${args[@]}"
EOF

cat >"$BIN_DIR/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
output=""
data_binary=""
args=("$@")
for ((index = 0; index < ${#args[@]}; index++)); do
  case "${args[$index]}" in
    --output)
      output="${args[$((index + 1))]}"
      index=$((index + 1))
      ;;
    --data-binary)
      data_binary="${args[$((index + 1))]}"
      index=$((index + 1))
      ;;
  esac
done
url="${args[$((${#args[@]} - 1))]}"
[[ -n "$output" ]] || exit 1
if [[ "$url" == */api/houses ]]; then
  printf '%s' "${FAKE_API_RESPONSE:-{\"code\":401}}" > "$output"
elif [[ "$url" == */api/admin/app-updates ]]; then
  [[ "$data_binary" == @* ]] || exit 1
  cp "${data_binary#@}" "$FAKE_REGISTER_PAYLOAD"
  printf '%s' "$FAKE_REGISTER_RESPONSE" > "$output"
else
  cp "$FAKE_INDEX_FILE" "$output"
fi
printf '200'
EOF

chmod +x "$BIN_DIR/nginx" "$BIN_DIR/mv" "$BIN_DIR/tar" "$BIN_DIR/curl"

nginx_config="$TEMP_DIR/nginx.conf"
printf 'server {\n  location /api/ {\n    proxy_pass http://backend;\n  }\n}\n' >"$nginx_config"

bundle_one="$TEMP_DIR/admin-one.tar.gz"
make_bundle one "$bundle_one"
FAKE_INDEX_FILE="$TEMP_DIR/build-one/index.html" \
    PATH="$BIN_DIR:$PATH" \
    bash "$ROOT_DIR/deploy/remote/activate-admin.sh" \
    "$SITE_DIR" "$bundle_one" "$(sha256_file "$bundle_one")" release-one "$SOURCE_SHA" \
    https://admin.example.test "$nginx_config" >"$TEMP_DIR/activation-one.txt"

[[ -L "$SITE_DIR/current" ]] || fail "first activation did not create current symlink"
grep -q '<title>one</title>' "$SITE_DIR/current/index.html" || fail "first activation has wrong content"
grep -q 'admin_deploy=healthy' "$TEMP_DIR/activation-one.txt" || fail "first activation has no evidence"

bundle_two="$TEMP_DIR/admin-two.tar.gz"
make_bundle two "$bundle_two"
if FAKE_INDEX_FILE="$TEMP_DIR/build-two/index.html" \
    FAKE_API_RESPONSE='{"code":500}' \
    PATH="$BIN_DIR:$PATH" \
    bash "$ROOT_DIR/deploy/remote/activate-admin.sh" \
    "$SITE_DIR" "$bundle_two" "$(sha256_file "$bundle_two")" release-two "$SOURCE_SHA" \
    https://admin.example.test "$nginx_config" >"$TEMP_DIR/activation-two.txt" 2>&1; then
    fail "activation with a failed same-origin probe unexpectedly succeeded"
fi
grep -q '<title>one</title>' "$SITE_DIR/current/index.html" || fail "failed activation did not restore current"

client_evidence="$TEMP_DIR/admin-client-dry-run.txt"
ADMIN_DEPLOY_HOST=deploy.example.test \
    ADMIN_DEPLOY_USER=deployer \
    ADMIN_DEPLOY_PATH=/srv/rabbit-admin \
    ADMIN_BUNDLE="$bundle_one" \
    ADMIN_BUNDLE_SHA256="$(sha256_file "$bundle_one")" \
    ADMIN_RELEASE_ID=release-one \
    ADMIN_SOURCE_SHA="$SOURCE_SHA" \
    ADMIN_PUBLIC_URL=https://admin.example.test \
    ADMIN_NGINX_CONFIG=/etc/nginx/nginx.conf \
    ADMIN_DEPLOY_EVIDENCE_FILE="$client_evidence" \
    bash "$ROOT_DIR/deploy/scripts/deploy-admin.sh" --dry-run
grep -q 'admin_deploy=dry-run' "$client_evidence" || fail "client dry run has no evidence"

apk_file="$TEMP_DIR/rabbit.apk"
notes_file="$TEMP_DIR/notes.txt"
token_file="$TEMP_DIR/admin.jwt"
register_evidence="$TEMP_DIR/app-release-evidence.txt"
register_payload="$TEMP_DIR/app-release-payload.json"
printf 'signed fixture APK' >"$apk_file"
printf 'Fixture release notes' >"$notes_file"
printf 'fixture-admin-token\n' >"$token_file"
apk_sha256="$(sha256_file "$apk_file")"
apk_size="$(wc -c <"$apk_file" | tr -d '[:space:]')"
FAKE_REGISTER_RESPONSE="$(jq -nc \
    --arg sha "$apk_sha256" \
    --argjson size "$apk_size" \
    '{code: 0, data: {id: 7, buildNumber: 501, versionName: "fixture", downloadUrl: "https://downloads.example.test/rabbit.apk", sha256: $sha, apkSizeBytes: $size, forceUpdate: false, requestId: "fixture-ota-501"}}')"

for _ in 1 2; do
    FAKE_REGISTER_PAYLOAD="$register_payload" \
        FAKE_REGISTER_RESPONSE="$FAKE_REGISTER_RESPONSE" \
        PATH="$BIN_DIR:$PATH" \
        APP_RELEASE_API_BASE_URL=https://api.example.test \
        APP_RELEASE_ADMIN_TOKEN_FILE="$token_file" \
        APP_RELEASE_APK="$apk_file" \
        APP_RELEASE_VERSION_NAME=fixture \
        APP_RELEASE_BUILD_NUMBER=501 \
        APP_RELEASE_DOWNLOAD_URL=https://downloads.example.test/rabbit.apk \
        APP_RELEASE_REQUEST_ID=fixture-ota-501 \
        APP_RELEASE_NOTES_FILE="$notes_file" \
        APP_RELEASE_EVIDENCE_FILE="$register_evidence" \
        bash "$ROOT_DIR/deploy/scripts/register-app-release.sh"
done

jq -e \
    --arg sha "$apk_sha256" \
    '.platform == "ANDROID" and .buildNumber == 501 and .sha256 == $sha and .requestId == "fixture-ota-501"' \
    "$register_payload" >/dev/null || fail "OTA payload is not safely serialized as expected"
grep -q 'app_release=registered' "$register_evidence" || fail "OTA registration has no evidence"

printf 'admin release script fixtures passed\n'
