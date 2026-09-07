#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
APP_DIR="$REPO_DIR/app"
ADMIN_DIR="$REPO_DIR/admin"
BASELINE_FIXTURE_SQL="$REPO_DIR/backend/src/test/resources/fixtures/batch_statistics_acceptance_fixture.sql"
BASELINE_CLEANUP_SQL="$REPO_DIR/backend/src/test/resources/fixtures/batch_statistics_acceptance_fixture_cleanup.sql"
COMPLEX_FIXTURE_SQL="$REPO_DIR/backend/src/test/resources/fixtures/batch_statistics_complex_matrix_fixture.sql"
COMPLEX_CLEANUP_SQL="$REPO_DIR/backend/src/test/resources/fixtures/batch_statistics_complex_matrix_cleanup.sql"
COMPLEX_CATALOG="$REPO_DIR/backend/src/test/resources/fixtures/batch_statistics_complex_matrix.json"
FIXTURE_SQL="$BASELINE_FIXTURE_SQL"
CLEANUP_SQL="$BASELINE_CLEANUP_SQL"
VALIDATOR="$SCRIPT_DIR/batch-statistics-cross-client-validate.mjs"
APP_ID="com.rabbit.app.flutter.dev"
XLSX_MEDIA_TYPE="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
PNPM_VERSION="11.22.0"

CONTAINER_ENGINE=()
COMPOSE=()
compose_project_name=""
HOST_API_URL="${RABBIT_BATCH_STATISTICS_HOST_API_URL:-http://127.0.0.1:8080}"
DEVICE_API_URL="${RABBIT_ANDROID_E2E_DEVICE_API_URL:-}"
DB_NAME="${RABBIT_BATCH_STATISTICS_DB_NAME:-rabbit_app}"
DB_USER="${RABBIT_BATCH_STATISTICS_DB_USER:-root}"
DB_PASSWORD="${RABBIT_BATCH_STATISTICS_DB_PASSWORD:-rabbit_root}"
KEEP_FIXTURE="${RABBIT_BATCH_STATISTICS_KEEP_FIXTURE:-0}"
SUITE="${RABBIT_BATCH_STATISTICS_SUITE:-baseline}"
READY_TIMEOUT_SECONDS="${RABBIT_BATCH_STATISTICS_READY_TIMEOUT_SECONDS:-240}"
DEVICE_ID="${RABBIT_ANDROID_E2E_DEVICE_ID:-}"
ADB_BIN="${RABBIT_ANDROID_E2E_ADB:-}"

run_id=""
artifact_root=""
runtime_defines_file=""
db_container_id=""
fixture_attempted=0
fixture_loaded=0
backend_touched=0
backend_existed=0
backend_originally_running=0
backend_original_captcha=""
backend_original_bind=""
backend_original_cors=""
backend_container_id=""
device_touched=0
android_app_touched=0
original_accelerometer_rotation=""
original_user_rotation=""
original_stay_on_while_plugged_in=""
api_validated=0
xlsx_validated=0
admin_validated=0
android_validated=0
database_validated=0
security_validated=0
secret_scan_validated=0

die() {
  echo "$*" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Run the shared MySQL fixture through the batch statistics API, Admin, and Android.

Optional environment:
  RABBIT_BATCH_STATISTICS_SUITE            baseline (default) or complex.
  RABBIT_ANDROID_E2E_DEVICE_ID             Select one ready adb device. Required when more than one is ready.
  RABBIT_ANDROID_E2E_DEVICE_API_URL         API origin reachable from the selected device. Physical devices default to the host LAN address.
  RABBIT_BATCH_STATISTICS_HOST_API_URL      Host API origin. Default: http://127.0.0.1:8080.
  RABBIT_BATCH_STATISTICS_DB_NAME           Fixture database. Default: rabbit_app.
  RABBIT_BATCH_STATISTICS_DB_USER           Fixture database user. Default: root.
  RABBIT_BATCH_STATISTICS_DB_PASSWORD       Fixture database password. Never written to artifacts.
  RABBIT_BATCH_STATISTICS_KEEP_FIXTURE      Set to 1 to retain fixture rows. Default: 0.
  RABBIT_BATCH_STATISTICS_READY_TIMEOUT_SECONDS
                                            Host and device readiness timeout. Default: 240.
  RABBIT_CHROME_BIN                         Explicit Google Chrome executable.
  ADMIN_DEV_PORT                            Vite port; defaults to an available loopback port.
  RABBIT_ANDROID_E2E_ADB                    Explicit adb executable.
  RABBIT_ANDROID_E2E_JAVA_HOME              JDK override for this device run.

Flutter and Android SDK discovery also accepts the variables documented by
app/scripts/toolchain_env.sh, including RABBIT_FLUTTER_BIN,
RABBIT_FLUTTER_HOME, RABBIT_JAVA_HOME, and RABBIT_ANDROID_SDK_ROOT.
USAGE
}

configure_suite() {
  case "$SUITE" in
  baseline)
    FIXTURE_SQL="$BASELINE_FIXTURE_SQL"
    CLEANUP_SQL="$BASELINE_CLEANUP_SQL"
    ;;
  complex)
    FIXTURE_SQL="$COMPLEX_FIXTURE_SQL"
    CLEANUP_SQL="$COMPLEX_CLEANUP_SQL"
    ;;
  *)
    echo "RABBIT_BATCH_STATISTICS_SUITE must be baseline or complex" >&2
    return 64
    ;;
  esac
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 69
  fi
}

configure_container_runtime() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    CONTAINER_ENGINE=(docker)
    COMPOSE=(docker compose)
    return
  fi
  if command -v podman >/dev/null 2>&1 && command -v podman-compose >/dev/null 2>&1; then
    CONTAINER_ENGINE=(podman)
    COMPOSE=(podman-compose)
    return
  fi
  die "Docker Compose or podman-compose is required"
}

compose_run() {
  (cd "$REPO_DIR" && "${COMPOSE[@]}" "$@")
}

strip_optional_quotes() {
  local value="$1"
  case "$value" in
  \"*\")
    value="${value#\"}"
    value="${value%\"}"
    ;;
  \'*\')
    value="${value#\'}"
    value="${value%\'}"
    ;;
  esac
  printf '%s\n' "$value"
}

dotenv_value() {
  local key="$1"
  local value=""
  if [[ -f "$REPO_DIR/.env" ]]; then
    value="$(awk -F= -v key="$key" '
      $0 !~ /^[[:space:]]*#/ && $1 == key {
        sub(/^[^=]*=/, "")
        found = $0
      }
      END { if (found != "") print found }
    ' "$REPO_DIR/.env")"
  fi
  strip_optional_quotes "$value"
}

resolve_compose_project_name() {
  local container_id=""
  local project_name=""

  container_id="$(compose_run ps --quiet 2>/dev/null | awk 'NF { print; exit }')"
  if [[ -n "$container_id" ]]; then
    project_name="$("${CONTAINER_ENGINE[@]}" inspect "$container_id" | jq -r '
      .[0].Config.Labels["com.docker.compose.project"] //
      .[0].Config.Labels["io.podman.compose.project"] // empty
    ')"
  fi
  if [[ -z "$project_name" ]]; then
    if [[ ${COMPOSE_PROJECT_NAME+x} ]]; then
      project_name="$COMPOSE_PROJECT_NAME"
    else
      project_name="$(dotenv_value COMPOSE_PROJECT_NAME)"
    fi
  fi
  compose_project_name="${project_name:-$(basename "$REPO_DIR")}"
  [[ -n "$compose_project_name" ]] || die "Cannot resolve the Compose project name"
}

compose_service_container_id() {
  local service="$1"
  local include_stopped="${2:-0}"
  local args=(ps)
  local container_id=""
  if [[ "$include_stopped" == "1" ]]; then
    args+=(--all)
  fi
  container_id="$("${CONTAINER_ENGINE[@]}" "${args[@]}" \
    --filter "label=com.docker.compose.project=$compose_project_name" \
    --filter "label=com.docker.compose.service=$service" \
    --format '{{.ID}}' | awk 'NF { print; exit }')"
  if [[ -z "$container_id" ]]; then
    container_id="$("${CONTAINER_ENGINE[@]}" "${args[@]}" \
      --filter "label=io.podman.compose.project=$compose_project_name" \
      --filter "label=io.podman.compose.service=$service" \
      --format '{{.ID}}' | awk 'NF { print; exit }')"
  fi
  printf '%s\n' "$container_id"
}

validate_base_url() {
  node - "$1" "$2" <<'NODE'
const [value, name] = process.argv.slice(2);
let parsed;
try {
  parsed = new URL(value);
} catch {
  console.error(`${name} must be a valid URL`);
  process.exit(64);
}
if (!["http:", "https:"].includes(parsed.protocol) ||
    parsed.username || parsed.password || parsed.pathname !== "/" ||
    parsed.search || parsed.hash) {
  console.error(`${name} must be an HTTP(S) origin without credentials, path, query, or fragment`);
  process.exit(64);
}
process.stdout.write(parsed.toString().replace(/\/$/, ""));
NODE
}

resolve_chrome() {
  local candidate=""
  if [[ -n "${RABBIT_CHROME_BIN:-}" ]]; then
    [[ -x "$RABBIT_CHROME_BIN" ]] ||
      die "RABBIT_CHROME_BIN is not executable: $RABBIT_CHROME_BIN"
    printf '%s\n' "$RABBIT_CHROME_BIN"
    return
  fi
  for candidate in \
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    "$(command -v google-chrome 2>/dev/null || true)" \
    "$(command -v google-chrome-stable 2>/dev/null || true)"; do
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done
  die "Google Chrome was not found. Install Chrome or set RABBIT_CHROME_BIN."
}

resolve_host_lan_ip() {
  local interface_name=""
  local address=""
  if command -v route >/dev/null 2>&1 && command -v ipconfig >/dev/null 2>&1; then
    interface_name="$(route -n get default 2>/dev/null | awk '/interface:/{print $2; exit}')"
    if [[ -n "$interface_name" ]]; then
      address="$(ipconfig getifaddr "$interface_name" 2>/dev/null || true)"
    fi
  elif command -v hostname >/dev/null 2>&1; then
    address="$(hostname -I 2>/dev/null | awk '{print $1}')"
  fi
  printf '%s\n' "$address"
}

resolve_admin_dev_port() {
  local configured_port="${ADMIN_DEV_PORT:-}"
  node - "$configured_port" <<'NODE'
const net = require("node:net");
const configured = process.argv[2];
const port = configured === "" ? 0 : Number(configured);
if (!Number.isSafeInteger(port) || port < 0 || port > 65535 ||
    (configured !== "" && port === 0)) {
  console.error("ADMIN_DEV_PORT must be an integer from 1 to 65535");
  process.exit(64);
}
const server = net.createServer();
server.unref();
server.once("error", () => process.exit(1));
server.listen({ host: "127.0.0.1", port }, () => {
  const address = server.address();
  if (!address || typeof address === "string") process.exit(1);
  const selected = address.port;
  server.close((error) => {
    if (error) process.exit(1);
    process.stdout.write(String(selected));
  });
});
NODE
}

select_device() {
  local ready_devices=""
  local count="0"
  "$ADB_BIN" start-server >/dev/null
  ready_devices="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  if [[ -n "$DEVICE_ID" ]]; then
    if ! printf '%s\n' "$ready_devices" |
      awk -v wanted="$DEVICE_ID" '$0 == wanted { found = 1 } END { exit !found }'; then
      die "RABBIT_ANDROID_E2E_DEVICE_ID is not a ready adb device: $DEVICE_ID"
    fi
  else
    count="$(printf '%s\n' "$ready_devices" | awk 'NF { count++ } END { print count + 0 }')"
    if [[ "$count" != "1" ]]; then
      die "Expected exactly one ready Android device, found $count. Set RABBIT_ANDROID_E2E_DEVICE_ID to select one."
    fi
    DEVICE_ID="$(printf '%s\n' "$ready_devices" | awk 'NF { print; exit }')"
  fi
  [[ "$($ADB_BIN -s "$DEVICE_ID" get-state 2>/dev/null)" == "device" ]] ||
    die "Android device is not ready: $DEVICE_ID"
}

capture_backend_state() {
  local container_id=""
  local inspected_captcha=""
  local inspected_bind=""
  local inspected_cors=""

  container_id="$(compose_service_container_id backend 1)"
  if [[ -n "$container_id" ]]; then
    backend_existed=1
    if [[ "$("${CONTAINER_ENGINE[@]}" inspect "$container_id" | jq -r '.[0].State.Running // false')" == "true" ]]; then
      backend_originally_running=1
    fi
    inspected_captcha="$("${CONTAINER_ENGINE[@]}" inspect "$container_id" | jq -r '
      [.[0].Config.Env[]? | select(startswith("APP_CAPTCHA_ENABLED=")) |
       split("=")[1:] | join("=")][0] // empty
    ')"
    inspected_bind="$("${CONTAINER_ENGINE[@]}" inspect "$container_id" | jq -r '
      .[0].HostConfig.PortBindings["8080/tcp"][0].HostIp // empty
    ')"
    inspected_cors="$("${CONTAINER_ENGINE[@]}" inspect "$container_id" | jq -r '
      [.[0].Config.Env[]? | select(startswith("APP_CORS_ALLOWED_ORIGINS=")) |
       split("=")[1:] | join("=")][0] // empty
    ')"
  fi

  if [[ -n "$inspected_captcha" ]]; then
    backend_original_captcha="$inspected_captcha"
  elif [[ ${APP_CAPTCHA_ENABLED+x} ]]; then
    backend_original_captcha="$APP_CAPTCHA_ENABLED"
  else
    backend_original_captcha="$(dotenv_value APP_CAPTCHA_ENABLED)"
    backend_original_captcha="${backend_original_captcha:-true}"
  fi
  backend_original_captcha="$(printf '%s' "$backend_original_captcha" | tr '[:upper:]' '[:lower:]')"
  case "$backend_original_captcha" in
  true | false) ;;
  *) die "Cannot safely restore APP_CAPTCHA_ENABLED: expected true or false" ;;
  esac

  if [[ -n "$inspected_bind" ]]; then
    backend_original_bind="$inspected_bind"
  elif [[ ${BACKEND_BIND_ADDRESS+x} ]]; then
    backend_original_bind="$BACKEND_BIND_ADDRESS"
  else
    backend_original_bind="$(dotenv_value BACKEND_BIND_ADDRESS)"
    backend_original_bind="${backend_original_bind:-127.0.0.1}"
  fi
  [[ -n "$backend_original_bind" ]] || die "Cannot safely restore BACKEND_BIND_ADDRESS"

  if [[ -n "$inspected_cors" ]]; then
    backend_original_cors="$inspected_cors"
  elif [[ ${APP_CORS_ALLOWED_ORIGINS+x} ]]; then
    backend_original_cors="$APP_CORS_ALLOWED_ORIGINS"
  else
    backend_original_cors="$(dotenv_value APP_CORS_ALLOWED_ORIGINS)"
    backend_original_cors="${backend_original_cors:-http://localhost:5173,http://127.0.0.1:5173,https://admin.dzht.top}"
  fi
  [[ -n "$backend_original_cors" && "$backend_original_cors" != *$'\n'* ]] ||
    die "Cannot safely restore APP_CORS_ALLOWED_ORIGINS"
}

mysql_exec() {
  MYSQL_PWD="$DB_PASSWORD" "${CONTAINER_ENGINE[@]}" exec -e MYSQL_PWD -i "$db_container_id" \
    mysql --default-character-set=utf8mb4 -N -B -u"$DB_USER" "$DB_NAME" "$@"
}

wait_for_host_backend_code() {
  local expected_code="$1"
  local deadline=$(($(date +%s) + READY_TIMEOUT_SECONDS))
  local response=""
  while [[ $(date +%s) -lt $deadline ]]; do
    response="$(curl -fsS --max-time 5 "$HOST_API_URL/api/auth/captcha" 2>/dev/null || true)"
    if [[ -n "$response" ]] &&
      printf '%s' "$response" |
      jq -e --argjson expected "$expected_code" '.code == $expected' >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

wait_for_host_backend() {
  wait_for_host_backend_code 501 ||
    die "Backend did not expose a captcha-disabled API at $HOST_API_URL within ${READY_TIMEOUT_SECONDS}s"
}

container_environment_value() {
  local container_id="$1"
  local key="$2"
  "${CONTAINER_ENGINE[@]}" inspect "$container_id" | jq -r --arg key "$key" '
    [.[0].Config.Env[]? | select(startswith($key + "=")) |
     split("=")[1:] | join("=")][0] // empty
  '
}

assert_backend_database_matches_fixture() {
  local datasource_url=""
  local datasource_user=""
  local datasource_password=""

  backend_container_id="$(compose_service_container_id backend)"
  [[ -n "$backend_container_id" ]] ||
    die "Compose did not report a running backend service container"
  datasource_url="$(container_environment_value "$backend_container_id" SPRING_DATASOURCE_URL)"
  datasource_user="$(container_environment_value "$backend_container_id" SPRING_DATASOURCE_USERNAME)"
  datasource_password="$(container_environment_value "$backend_container_id" SPRING_DATASOURCE_PASSWORD)"

  if ! printf '%s' "$datasource_url" | grep -Fq "/$DB_NAME?" ||
    [[ "$datasource_user" != "$DB_USER" || "$datasource_password" != "$DB_PASSWORD" ]]; then
    die "Fixture database settings do not match the temporary backend data source"
  fi
}

wait_for_device_backend() {
  local deadline=$(($(date +%s) + READY_TIMEOUT_SECONDS))
  local response=""
  while [[ $(date +%s) -lt $deadline ]]; do
    response="$($ADB_BIN -s "$DEVICE_ID" shell \
      "curl -fsS --max-time 5 '$DEVICE_API_URL/api/auth/captcha'" 2>/dev/null | tr -d '\r' || true)"
    if [[ -n "$response" ]] &&
      printf '%s' "$response" | jq -e '.code == 501' >/dev/null 2>&1; then
      return
    fi
    sleep 2
  done
  die "Android device $DEVICE_ID did not reach the captcha-disabled API at $DEVICE_API_URL within ${READY_TIMEOUT_SECONDS}s"
}

clear_android_app() {
  local phase="$1"
  local output=""
  if "$ADB_BIN" -s "$DEVICE_ID" shell pm path "$APP_ID" >/dev/null 2>&1; then
    output="$($ADB_BIN -s "$DEVICE_ID" shell pm clear "$APP_ID" 2>&1 | tr -d '\r')" || return 1
    [[ "$output" == *Success* ]] || return 1
    printf '%s\n' "$output" >"$artifact_root/android/device-clear-${phase}.txt"
  else
    printf '%s\n' "package-not-installed" >"$artifact_root/android/device-clear-${phase}.txt"
  fi
}

capture_database_assertions() {
  local raw_file="$artifact_root/database-assertions.raw.json"
  local query_status=0
  [[ "$fixture_loaded" == "1" ]] || return 0

  mysql_exec >"$raw_file" <<SQL
SET @fixture_run_id = '$run_id';
SET @actor = CONCAT('bsf_', @fixture_run_id, '_owner');
SET @user_id = (SELECT user_id FROM sys_user WHERE user_name = @actor);
SET @house_id = (SELECT id FROM rabbit_houses WHERE request_id = CONCAT('bsf-house-', @fixture_run_id, '-target'));
SET @isolation_house_id = (SELECT id FROM rabbit_houses WHERE request_id = CONCAT('bsf-house-', @fixture_run_id, '-isolation'));
SET @batch_id = (SELECT id FROM batches WHERE request_id = CONCAT('bsf-batch-', @fixture_run_id, '-target'));
SET @isolation_batch_id = (SELECT id FROM batches WHERE request_id = CONCAT('bsf-batch-', @fixture_run_id, '-isolation'));
SELECT JSON_OBJECT(
  'run_id', @fixture_run_id,
  'cycle', JSON_OBJECT(
    'count', (SELECT COUNT(*) FROM breeding_cycles WHERE house_id = @house_id AND batch_id = @batch_id),
    'mating_first', (SELECT DATE_FORMAT(MIN(mating_date), '%Y-%m-%d') FROM breeding_cycles WHERE house_id = @house_id AND batch_id = @batch_id),
    'mating_last', (SELECT DATE_FORMAT(MAX(mating_date), '%Y-%m-%d') FROM breeding_cycles WHERE house_id = @house_id AND batch_id = @batch_id)
  ),
  'pregnancy', JSON_OBJECT(
    'cycle_count', (SELECT COUNT(*) FROM breeding_cycles WHERE house_id = @house_id AND batch_id = @batch_id AND pregnancy_result = '怀孕'),
    'doe_count', (SELECT COUNT(DISTINCT mother_rabbit_id) FROM breeding_cycles WHERE house_id = @house_id AND batch_id = @batch_id AND pregnancy_result = '怀孕')
  ),
  'buck', JSON_OBJECT(
    'distinct_count', (SELECT COUNT(DISTINCT male_rabbit_id) FROM breeding_cycles WHERE house_id = @house_id AND batch_id = @batch_id)
  ),
  'abortion', JSON_OBJECT(
    'cycle_count', (SELECT COUNT(*) FROM breeding_cycles WHERE house_id = @house_id AND batch_id = @batch_id AND result = 'ABORTED'),
    'event_count', (SELECT COUNT(*) FROM repro_events WHERE house_id = @house_id AND batch_id = @batch_id AND event_type = 'ABORTION')
  ),
  'litter', JSON_OBJECT(
    'count', (SELECT COUNT(*) FROM litters WHERE house_id = @house_id AND batch_id = @batch_id),
    'total_kits', (SELECT SUM(total_kits) FROM litters WHERE house_id = @house_id AND batch_id = @batch_id),
    'live_kits', (SELECT SUM(live_kits) FROM litters WHERE house_id = @house_id AND batch_id = @batch_id),
    'kept_kits', (SELECT SUM(kept_kits) FROM litters WHERE house_id = @house_id AND batch_id = @batch_id),
    'weaned_count', (SELECT SUM(weaned_count) FROM litters WHERE house_id = @house_id AND batch_id = @batch_id),
    'weaning_total_weight_kg', (SELECT SUM(weaning_total_weight_kg) FROM litters WHERE house_id = @house_id AND batch_id = @batch_id)
  ),
  'sold', JSON_OBJECT(
    'allocation_rows', (SELECT COUNT(*) FROM sale_order_batch_allocations WHERE house_id = @house_id AND batch_id = @batch_id),
    'rabbit_count', (SELECT SUM(rabbit_count) FROM sale_order_batch_allocations WHERE house_id = @house_id AND batch_id = @batch_id),
    'actual_weight_kg', (SELECT SUM(actual_weight_kg) FROM sale_order_batch_allocations WHERE house_id = @house_id AND batch_id = @batch_id),
    'amount', (SELECT SUM(amount) FROM sale_order_batch_allocations WHERE house_id = @house_id AND batch_id = @batch_id),
    'item_count', (SELECT COUNT(*) FROM sale_order_items WHERE batch_id_snapshot = @batch_id)
  ),
  'replacement', JSON_OBJECT(
    'allocation_rows', (SELECT COUNT(*) FROM replacement_batch_allocations WHERE house_id = @house_id AND source_batch_id = @batch_id),
    'rabbit_count', (SELECT SUM(rabbit_count) FROM replacement_batch_allocations WHERE house_id = @house_id AND source_batch_id = @batch_id),
    'total_weight_kg', (SELECT SUM(total_weight_kg) FROM replacement_batch_allocations WHERE house_id = @house_id AND source_batch_id = @batch_id)
  ),
  'feed', JSON_OBJECT(
    'breeding_kg', (SELECT SUM(amount_kg) FROM feed_log_batch_allocations WHERE house_id = @house_id AND batch_id = @batch_id AND phase = 'BREEDING'),
    'fattening_kg', (SELECT SUM(amount_kg) FROM feed_log_batch_allocations WHERE house_id = @house_id AND batch_id = @batch_id AND phase = 'FATTENING')
  ),
  'carcass', JSON_OBJECT(
    'version_count', (SELECT COUNT(*) FROM batch_carcass_yield_versions WHERE house_id = @house_id AND batch_id = @batch_id),
    'yield_rate', (SELECT MAX(yield_rate) FROM batch_carcass_yield_versions WHERE house_id = @house_id AND batch_id = @batch_id),
    'source_unit', (SELECT MAX(source_unit) FROM batch_carcass_yield_versions WHERE house_id = @house_id AND batch_id = @batch_id),
    'measured_date', (SELECT DATE_FORMAT(MAX(measured_date), '%Y-%m-%d') FROM batch_carcass_yield_versions WHERE house_id = @house_id AND batch_id = @batch_id)
  ),
  'tenant_ownership', JSON_OBJECT(
    'owner_memberships', (SELECT COUNT(*) FROM house_users WHERE user_id = @user_id AND house_id IN (@house_id, @isolation_house_id) AND role = 'OWNER' AND status = 'ENABLED'),
    'target_batch_owned', (SELECT COUNT(*) FROM batches WHERE id = @batch_id AND house_id = @house_id),
    'isolation_batch_owned', (SELECT COUNT(*) FROM batches WHERE id = @isolation_batch_id AND house_id = @isolation_house_id)
  ),
  'isolation', JSON_OBJECT(
    'house_rows', (SELECT COUNT(*) FROM rabbit_houses WHERE id = @isolation_house_id),
    'batch_rows', (SELECT COUNT(*) FROM batches WHERE id = @isolation_batch_id),
    'rabbit_rows', (SELECT COUNT(*) FROM rabbits WHERE house_id = @isolation_house_id),
    'business_rows', (
      (SELECT COUNT(*) FROM breeding_cycles WHERE house_id = @isolation_house_id OR batch_id = @isolation_batch_id) +
      (SELECT COUNT(*) FROM repro_events WHERE house_id = @isolation_house_id OR batch_id = @isolation_batch_id) +
      (SELECT COUNT(*) FROM litters WHERE house_id = @isolation_house_id OR batch_id = @isolation_batch_id) +
      (SELECT COUNT(*) FROM feed_log_batch_allocations WHERE house_id = @isolation_house_id OR batch_id = @isolation_batch_id) +
      (SELECT COUNT(*) FROM sale_order_batch_allocations WHERE house_id = @isolation_house_id OR batch_id = @isolation_batch_id) +
      (SELECT COUNT(*) FROM sale_order_items item INNER JOIN sale_orders sale ON sale.id = item.sale_order_id WHERE sale.house_id = @isolation_house_id OR item.batch_id_snapshot = @isolation_batch_id) +
      (SELECT COUNT(*) FROM replacement_batch_allocations WHERE house_id = @isolation_house_id OR source_batch_id = @isolation_batch_id) +
      (SELECT COUNT(*) FROM batch_carcass_yield_versions WHERE house_id = @isolation_house_id OR batch_id = @isolation_batch_id)
    )
  )
);
SQL
  query_status=$?
  [[ "$query_status" == "0" ]] || return 1

  if ! node "$VALIDATOR" database "$raw_file" "$run_id" \
    "$artifact_root/database-assertions.json"; then
    return 1
  fi
  rm -f "$raw_file" || return 1
  database_validated=1
}

cleanup_fixture() {
  local cleanup_output_file=""
  local cleanup_manifest=""
  local pipeline_status=()
  [[ "$fixture_attempted" == "1" ]] || return 0

  if [[ "$KEEP_FIXTURE" == "1" ]]; then
    jq -n --arg run_id "$run_id" '{run_id:$run_id,kept:true}' >"$artifact_root/cleanup-result.json"
    echo "Fixture $run_id was kept. Cleanup command:" >&2
    printf '  cd %q && export MYSQL_PWD="${RABBIT_BATCH_STATISTICS_DB_PASSWORD:?set the fixture database password}" && { printf %q; cat %q; } | ' \
      "$REPO_DIR" "SET @fixture_run_id = '$run_id';\n" "$CLEANUP_SQL" >&2
    printf '%q ' "${COMPOSE[@]}" >&2
    printf 'exec -T -e MYSQL_PWD mysql mysql --default-character-set=utf8mb4 -N -B -u%q %q; unset MYSQL_PWD\n' \
      "$DB_USER" "$DB_NAME" >&2
    return 0
  fi

  cleanup_output_file="$artifact_root/cleanup-output.txt"
  {
    printf "SET @fixture_run_id = '%s';\n" "$run_id"
    cat "$CLEANUP_SQL"
  } | mysql_exec >"$cleanup_output_file"
  pipeline_status=("${PIPESTATUS[@]}")
  [[ "${pipeline_status[0]}" == "0" && "${pipeline_status[1]}" == "0" ]] || return 1
  cleanup_manifest="$(awk 'NF { value = $0 } END { print value }' "$cleanup_output_file")"
  printf '%s\n' "$cleanup_manifest" | jq '.' >"$artifact_root/cleanup-result.json" || return 1
  if ! jq -e --arg run_id "$run_id" --arg suite "$SUITE" '
    .run_id == $run_id and
    .remaining_users == 0 and
    .remaining_houses == 0 and
    .remaining_batches == 0 and
    ($suite != "complex" or
      (.remaining_dedup == 0 and .remaining_events == 0))
  ' "$artifact_root/cleanup-result.json" >/dev/null; then
    return 1
  fi
}

restore_backend() {
  [[ "$backend_touched" == "1" ]] || return 0
  if [[ "$backend_existed" == "1" ]]; then
    (
      cd "$REPO_DIR" || exit 1
      APP_CAPTCHA_ENABLED="$backend_original_captcha" \
        BACKEND_BIND_ADDRESS="$backend_original_bind" \
        APP_CORS_ALLOWED_ORIGINS="$backend_original_cors" \
        "${COMPOSE[@]}" up -d --no-build --force-recreate backend || exit 1
      if [[ "$backend_originally_running" == "1" ]]; then
        if [[ "$backend_original_captcha" == "true" ]]; then
          wait_for_host_backend_code 0 || exit 1
        else
          wait_for_host_backend_code 501 || exit 1
        fi
      else
        "${COMPOSE[@]}" stop backend || exit 1
      fi
    ) >"$artifact_root/backend-restore.log" 2>&1 || return 1
  else
    compose_run stop backend >"$artifact_root/backend-restore.log" 2>&1 || return 1
    backend_container_id="$(compose_service_container_id backend 1)"
    if [[ -n "$backend_container_id" ]]; then
      "${CONTAINER_ENGINE[@]}" rm -f "$backend_container_id" \
        >>"$artifact_root/backend-restore.log" 2>&1 || return 1
    fi
  fi
}

restore_device() {
  local failed=0
  [[ "$device_touched" == "1" ]] || return 0

  if [[ "$android_app_touched" == "1" ]]; then
    clear_android_app after || failed=1
  fi
  if [[ "$original_accelerometer_rotation" == "null" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" shell settings delete system accelerometer_rotation >/dev/null 2>&1 || failed=1
  else
    "$ADB_BIN" -s "$DEVICE_ID" shell settings put system accelerometer_rotation \
      "$original_accelerometer_rotation" >/dev/null 2>&1 || failed=1
  fi
  if [[ "$original_user_rotation" == "null" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" shell settings delete system user_rotation >/dev/null 2>&1 || failed=1
  else
    "$ADB_BIN" -s "$DEVICE_ID" shell settings put system user_rotation \
      "$original_user_rotation" >/dev/null 2>&1 || failed=1
  fi
  if [[ "$original_stay_on_while_plugged_in" == "null" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" shell settings delete global stay_on_while_plugged_in >/dev/null 2>&1 || failed=1
  else
    "$ADB_BIN" -s "$DEVICE_ID" shell settings put global stay_on_while_plugged_in \
      "$original_stay_on_while_plugged_in" >/dev/null 2>&1 || failed=1
  fi
  return "$failed"
}

redact_stream() {
  local secret=""
  local line=""
  local redacted=""
  while IFS= read -r line || [[ -n "$line" ]]; do
    redacted="$line"
    for secret in "$@"; do
      if [[ -n "$secret" ]]; then
        redacted="${redacted//$secret/[REDACTED]}"
      fi
    done
    printf '%s\n' "$redacted"
  done
}

sanitize_android_result() {
  node - "$1" <<'NODE'
const fs = require("node:fs");
const file = process.argv[2];
const result = JSON.parse(fs.readFileSync(file, "utf8"));
if (!Array.isArray(result.screenshots) || !Array.isArray(result.screenshotNames)) {
  throw new Error("Android result lacks screenshot evidence");
}
const embeddedNames = result.screenshots.map((entry) => entry.screenshotName);
if (JSON.stringify(embeddedNames) !== JSON.stringify(result.screenshotNames)) {
  throw new Error("Android embedded screenshots do not match screenshotNames");
}
delete result.screenshots;
fs.writeFileSync(file, `${JSON.stringify(result, null, 2)}\n`);
NODE
}

bool_json() {
  [[ "$1" == "1" ]] && printf 'true\n' || printf 'false\n'
}

write_manifest() {
  local final_status="$1"
  local final_code="$2"
  local cleanup_ok="$3"
  local backend_restore_ok="$4"
  local device_restore_ok="$5"
  local scenario_validations='{}'
  if [[ "$SUITE" == "complex" && -s "$artifact_root/validation-proof.json" ]]; then
    scenario_validations="$(jq -c '.scenarios // {}' "$artifact_root/validation-proof.json")"
  fi
  jq -n \
    --arg run_id "$run_id" \
    --arg suite "$SUITE" \
    --arg status "$final_status" \
    --argjson exit_code "$final_code" \
    --argjson fixture_kept "$(bool_json "$KEEP_FIXTURE")" \
    --argjson backend_originally_running "$(bool_json "$backend_originally_running")" \
    --argjson api "$(bool_json "$api_validated")" \
    --argjson xlsx "$(bool_json "$xlsx_validated")" \
    --argjson admin "$(bool_json "$admin_validated")" \
    --argjson android "$(bool_json "$android_validated")" \
    --argjson database "$(bool_json "$database_validated")" \
    --argjson security "$(bool_json "$security_validated")" \
    --argjson secret_scan "$(bool_json "$secret_scan_validated")" \
    --argjson scenarios "$scenario_validations" \
    --argjson cleanup "$cleanup_ok" \
    --argjson backend_restored "$backend_restore_ok" \
    --argjson device_restored "$device_restore_ok" '
    ({
      schemaVersion:1,
      runId:$run_id,
      suite:$suite,
      status:$status,
      exitCode:$exit_code,
      fixtureKept:$fixture_kept,
      backendOriginallyRunning:$backend_originally_running,
      validations:({api:$api,xlsx:$xlsx,admin:$admin,android:$android,database:$database} +
        (if $suite == "complex" then {security:$security,secretScan:$secret_scan} else {} end))
    } + (if $suite == "complex" then {scenarioValidations:$scenarios} else {} end) + {
      cleanup:{fixture:$cleanup,backendRestored:$backend_restored,deviceRestored:$device_restored}
    })
  ' >"$artifact_root/manifest.json"
}

write_checksums() {
  local checksum_tmp="$artifact_root/../.${run_id}.SHA256SUMS.tmp"
  (
    cd "$artifact_root"
    find . -type f ! -name SHA256SUMS -print | LC_ALL=C sort | while IFS= read -r file; do
      if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file"
      else
        sha256sum "$file"
      fi
    done
  ) >"$checksum_tmp" || {
    rm -f "$checksum_tmp"
    return 1
  }
  mv "$checksum_tmp" "$artifact_root/SHA256SUMS"
}

on_exit() {
  local original_status=$?
  local final_status="$original_status"
  local cleanup_ok=true
  local backend_restore_ok=true
  local device_restore_ok=true
  local result="failed"
  local final_scan_status=()

  trap - EXIT INT TERM
  set +e
  if [[ -n "$runtime_defines_file" ]]; then
    if ! rm -f "$runtime_defines_file"; then
      [[ "$final_status" -ne 0 ]] || final_status=1
    fi
    runtime_defines_file=""
  fi
  if [[ "$SUITE" == "baseline" && "$fixture_loaded" == "1" && ! -s "$artifact_root/database-assertions.json" ]]; then
    capture_database_assertions
    if [[ $? -ne 0 && "$final_status" -eq 0 ]]; then
      final_status=1
    fi
  fi
  cleanup_fixture
  if [[ $? -ne 0 ]]; then
    cleanup_ok=false
    [[ "$final_status" -ne 0 ]] || final_status=1
    if [[ ! -s "$artifact_root/cleanup-result.json" ]]; then
      printf '%s\n' '{"cleanup":"failed"}' >"$artifact_root/cleanup-result.json"
    fi
  fi
  restore_backend
  if [[ $? -ne 0 ]]; then
    backend_restore_ok=false
    [[ "$final_status" -ne 0 ]] || final_status=1
  fi
  restore_device
  if [[ $? -ne 0 ]]; then
    device_restore_ok=false
    [[ "$final_status" -ne 0 ]] || final_status=1
  fi

  [[ "$final_status" -ne 0 ]] || result="passed"
  if ! write_manifest "$result" "$final_status" "$cleanup_ok" "$backend_restore_ok" "$device_restore_ok"; then
    [[ "$final_status" -ne 0 ]] || final_status=1
    result="failed"
  fi
  if [[ "$SUITE" == "complex" ]]; then
    printf '%s' '["123456"]' |
      node "$VALIDATOR" complex-secret-scan "$artifact_root" "$artifact_root/secret-scan.json"
    final_scan_status=("${PIPESTATUS[@]}")
    if [[ "${final_scan_status[0]}" != "0" || "${final_scan_status[1]}" != "0" ]]; then
      secret_scan_validated=0
      [[ "$final_status" -ne 0 ]] || final_status=1
      result="failed"
      write_manifest "$result" "$final_status" "$cleanup_ok" "$backend_restore_ok" "$device_restore_ok" || true
    fi
  fi
  if ! write_checksums; then
    [[ "$final_status" -ne 0 ]] || final_status=1
    result="failed"
    write_manifest "$result" "$final_status" "$cleanup_ok" "$backend_restore_ok" "$device_restore_ok" || true
  fi

  if [[ "$final_status" -eq 0 ]]; then
    echo "Batch statistics cross-client E2E passed. Artifacts: $artifact_root"
  else
    echo "Batch statistics cross-client E2E failed (status $final_status). Artifacts: $artifact_root" >&2
  fi
  exit "$final_status"
}

trap_interrupt() {
  exit "$1"
}

header_value() {
  local name="$1"
  local file="$2"
  awk -v wanted="$name" '
    {
      line = $0
      sub(/\r$/, "", line)
      lower = tolower(line)
      prefix = tolower(wanted) ":"
      if (index(lower, prefix) == 1) {
        sub(/^[^:]*:[[:space:]]*/, "", line)
        value = line
      }
    }
    END { print value }
  ' "$file"
}

fixture_password_for_profile() {
  case "$1" in
  e2e-default) printf '%s' '123456' ;;
  *) die "Unsupported fixture credential profile: $1" ;;
  esac
}

complex_login() {
  local user_name="$1"
  local password="$2"
  local payload=""
  local response=""
  payload="$(LOGIN_USER_NAME="$user_name" LOGIN_PASSWORD="$password" jq -nc \
    '{userName:env.LOGIN_USER_NAME,password:env.LOGIN_PASSWORD}')"
  response="$(curl -fsS --max-time 20 -X POST "$HOST_API_URL/api/auth/login" \
    -H 'Content-Type: application/json' --data-binary @- <<<"$payload")"
  jq -er 'select(.code == 0) | .data.token | select(type == "string" and length > 0)' \
    <<<"$response"
}

complex_request() {
  local token="$1"
  local house_id="$2"
  local method="$3"
  local url="$4"
  local output="$5"
  local body="${6:-}"
  local headers_file="${7:-}"
  local config=""
  local curl_args=(-sS --max-time 60 -X "$method" -o "$output" -w '%{http_code}')
  config="$(printf 'header = "Authorization: Bearer %s"\nheader = "X-House-Id: %s"\n' \
    "$token" "$house_id")"
  if [[ -n "$headers_file" ]]; then
    curl_args+=(-D "$headers_file")
  fi
  if [[ -n "$body" ]]; then
    curl_args+=(-H 'Content-Type: application/json' --data-binary @-)
    curl --config /dev/fd/3 "${curl_args[@]}" "$url" 3<<<"$config" <<<"$body"
  else
    curl --config /dev/fd/3 "${curl_args[@]}" "$url" 3<<<"$config"
  fi
}

complex_business_code() {
  jq -er '.code | select(type == "number")' "$1"
}

run_complex_suite() {
  local chrome_bin="$1"
  local admin_dev_port="$2"
  local admin_origin="$3"
  local device_characteristics="$4"
  local flyway_version="$5"
  local pnpm_version="$6"
  local fixture_output_file="$artifact_root/fixture-output.txt"
  local fixture_manifest_raw="$artifact_root/fixture-manifest.raw.json"
  local fixture_manifest_enriched="$artifact_root/fixture-manifest.enriched.json"
  local fixture_manifest="$artifact_root/fixture-manifest.json"
  local fixture_password=""
  local scenarios_json=""
  local users_json=""
  local owner_name=""
  local read_only_name=""
  local outsider_name=""
  local account_name=""
  local owner_id=""
  local read_only_id=""
  local outsider_id=""
  local owner_password=""
  local read_only_password=""
  local outsider_password=""
  local house_id=""
  local house_name=""
  local isolation_house_id=""
  local isolation_house_name=""
  local owner_token=""
  local read_only_token=""
  local outsider_token=""
  local scenario_id=""
  local batch_id=""
  local batch_code=""
  local scenario_dir=""
  local http_status=""
  local pipeline_status=()
  local admin_status=0
  local drive_status=0
  local security_dir="$artifact_root/security"
  local security_batch_id=""
  local replay_payload=""
  local conflict_payload=""
  local replay_request_id=""
  local before_version_count=""
  local before_dedup_count=""
  local first_version_count=""
  local first_dedup_count=""
  local after_version_count=""
  local after_dedup_count=""
  local read_only_export_http_status=""
  local read_only_export_media_type=""
  local read_only_export_size=""
  local security_result="$security_dir/result.json"
  local database_raw="$artifact_root/database-assertions.raw.json"
  local api_scenarios='{}'
  local xlsx_scenarios='{}'
  local scenario_validations='{}'

  [[ -f "$COMPLEX_CATALOG" ]] || die "Complex catalog not found: $COMPLEX_CATALOG"
  mkdir -p "$artifact_root/scenarios" "$artifact_root/roles" "$security_dir"

  fixture_attempted=1
  set +e
  {
    printf "SET @fixture_run_id = '%s';\n" "$run_id"
    cat "$FIXTURE_SQL"
  } | mysql_exec >"$fixture_output_file"
  pipeline_status=("${PIPESTATUS[@]}")
  set -e
  [[ "${pipeline_status[0]}" == "0" ]] || die "Failed to stream the complex fixture SQL"
  [[ "${pipeline_status[1]}" == "0" ]] || die "Complex fixture SQL failed"
  awk 'NF { value = $0 } END { print value }' "$fixture_output_file" >"$fixture_manifest_raw"
  jq -e --arg run_id "$run_id" '
    .run_id == $run_id and (.target_house_id | type == "number") and
    (.isolation_house_id | type == "number") and (.accounts | length == 3)
  ' "$fixture_manifest_raw" >/dev/null || die "Complex SQL manifest is malformed"
  house_id="$(jq -er '.target_house_id' "$fixture_manifest_raw")"
  isolation_house_id="$(jq -er '.isolation_house_id' "$fixture_manifest_raw")"
  house_name="$(mysql_exec -e "SELECT name FROM rabbit_houses WHERE id = $house_id;")"
  isolation_house_name="$(mysql_exec -e "SELECT name FROM rabbit_houses WHERE id = $isolation_house_id;")"
  [[ -n "$house_name" ]] || die "Complex target house name is missing"
  [[ -n "$isolation_house_name" ]] || die "Complex isolation house name is missing"
  owner_name="$(jq -er '.accounts[] | select(.role == "OWNER") | .user_name' "$fixture_manifest_raw")"
  read_only_name="$(jq -er '.accounts[] | select(.role == "READ_ONLY") | .user_name' "$fixture_manifest_raw")"
  outsider_name="$(jq -er '.accounts[] | select(.role == "OUTSIDER") | .user_name' "$fixture_manifest_raw")"
  for account_name in "$owner_name" "$read_only_name" "$outsider_name"; do
    [[ "$account_name" =~ ^[A-Za-z0-9_]+$ ]] || die "Complex fixture username is unsafe"
  done
  owner_id="$(mysql_exec -e "SELECT user_id FROM sys_user WHERE user_name = '$owner_name';")"
  read_only_id="$(mysql_exec -e "SELECT user_id FROM sys_user WHERE user_name = '$read_only_name';")"
  outsider_id="$(mysql_exec -e "SELECT user_id FROM sys_user WHERE user_name = '$outsider_name';")"
  jq --arg house_name "$house_name" --arg isolation_house_name "$isolation_house_name" \
    --argjson target "$house_id" --argjson isolation "$isolation_house_id" \
    --argjson owner_id "$owner_id" --argjson read_only_id "$read_only_id" --argjson outsider_id "$outsider_id" '
    . + {house_name:$house_name,isolation_house_name:$isolation_house_name} |
    .accounts |= map(. + {
      user_id:(if .role == "OWNER" then $owner_id elif .role == "READ_ONLY" then $read_only_id else $outsider_id end),
      house_id:(if .role == "OUTSIDER" then $isolation else $target end),
      house_name:(if .role == "OUTSIDER" then $isolation_house_name else $house_name end)
    })
  ' "$fixture_manifest_raw" >"$fixture_manifest_enriched"
  node "$VALIDATOR" complex-manifest "$fixture_manifest_enriched" "$COMPLEX_CATALOG" \
    "$run_id" "$fixture_manifest"
  fixture_loaded=1

  house_id="$(jq -er '.houseId' "$fixture_manifest")"
  house_name="$(jq -er '.houseName' "$fixture_manifest")"
  isolation_house_id="$(jq -er '.isolationHouseId' "$fixture_manifest")"
  fixture_password="$(fixture_password_for_profile "$(jq -er '.credentialProfile' "$fixture_manifest")")"
  owner_name="$(jq -er '.users[] | select(.role == "OWNER") | .userName' "$fixture_manifest")"
  read_only_name="$(jq -er '.users[] | select(.role == "READ_ONLY") | .userName' "$fixture_manifest")"
  outsider_name="$(jq -er '.users[] | select(.role == "UNRELATED_HOUSE") | .userName' "$fixture_manifest")"
  owner_password="$fixture_password"
  read_only_password="$fixture_password"
  outsider_password="$fixture_password"

  owner_token="$(complex_login "$owner_name" "$owner_password")" || die "Complex OWNER login failed"
  read_only_token="$(complex_login "$read_only_name" "$read_only_password")" || die "Complex READ_ONLY login failed"
  outsider_token="$(complex_login "$outsider_name" "$outsider_password")" || die "Complex OUTSIDER login failed"

  for scenario_id in \
    complex-available mixed-data-quality mixed-batch-rounding \
    time-and-cycle-boundaries mixed-batch-rounding-support; do
    scenario_dir="$artifact_root/scenarios/$scenario_id"
    mkdir -p "$scenario_dir/api" "$scenario_dir/xlsx"
    batch_id="$(jq -er --arg id "$scenario_id" '.scenarios[] | select(.id == $id) | .batchId' "$fixture_manifest")"
    batch_code="$(jq -er --arg id "$scenario_id" '.scenarios[] | select(.id == $id) | .batchCode' "$fixture_manifest")"
    http_status="$(complex_request "$owner_token" "$house_id" GET \
      "$HOST_API_URL/api/batches/$batch_id/statistics" "$scenario_dir/api/response.json")"
    [[ "$http_status" == "200" ]] || die "$scenario_id statistics returned HTTP $http_status"
    node "$VALIDATOR" complex-api "$scenario_dir/api/response.json" "$scenario_id" \
      "$batch_id" "$COMPLEX_CATALOG" "$scenario_dir/api/validation.json"

    http_status="$(complex_request "$owner_token" "$house_id" GET \
      "$HOST_API_URL/api/reports/batches/$batch_id/statistics.xlsx" \
      "$scenario_dir/xlsx/batch-statistics.xlsx" "" "$scenario_dir/xlsx/headers.txt")"
    [[ "$http_status" == "200" ]] || die "$scenario_id workbook returned HTTP $http_status"
    [[ "$(dd if="$scenario_dir/xlsx/batch-statistics.xlsx" bs=1 count=2 2>/dev/null)" == "PK" ]] ||
      die "$scenario_id workbook is not an OOXML ZIP"
    unzip -t "$scenario_dir/xlsx/batch-statistics.xlsx" >"$scenario_dir/xlsx/unzip.txt"
    node "$VALIDATOR" complex-xlsx "$scenario_dir/xlsx/batch-statistics.xlsx" \
      "$scenario_dir/xlsx/headers.txt" "$scenario_id" "$batch_code" "$COMPLEX_CATALOG" \
      "$scenario_dir/xlsx/validation.json"
  done

  security_batch_id="$(jq -er '.scenarios[] | select(.id == "security-and-retry") | .batchId' "$fixture_manifest")"
  complex_request "$owner_token" "$house_id" GET \
    "$HOST_API_URL/api/batches/$security_batch_id/statistics" "$security_dir/owner-statistics.json" >/dev/null
  complex_request "$read_only_token" "$house_id" GET \
    "$HOST_API_URL/api/batches/$security_batch_id/statistics" "$security_dir/read-only-statistics.json" >/dev/null
  read_only_export_http_status="$(complex_request "$read_only_token" "$house_id" GET \
    "$HOST_API_URL/api/reports/batches/$security_batch_id/statistics.xlsx" \
    "$security_dir/read-only-export.xlsx" "" "$security_dir/read-only-export.headers.txt")"
  [[ "$read_only_export_http_status" == "200" ]] ||
    die "READ_ONLY workbook returned HTTP $read_only_export_http_status"
  read_only_export_media_type="$(header_value Content-Type "$security_dir/read-only-export.headers.txt")"
  read_only_export_media_type="${read_only_export_media_type%%;*}"
  [[ "$read_only_export_media_type" == "$XLSX_MEDIA_TYPE" ]] ||
    die "READ_ONLY workbook has unexpected Content-Type: $read_only_export_media_type"
  [[ "$(dd if="$security_dir/read-only-export.xlsx" bs=1 count=2 2>/dev/null)" == "PK" ]] ||
    die "READ_ONLY workbook is not an OOXML ZIP"
  unzip -t "$security_dir/read-only-export.xlsx" >"$security_dir/read-only-export-unzip.txt"
  read_only_export_size="$(wc -c <"$security_dir/read-only-export.xlsx" | tr -d '[:space:]')"
  complex_request "$read_only_token" "$house_id" GET \
    "$HOST_API_URL/api/batches/$security_batch_id/carcass-yields" "$security_dir/read-only-history.json" >/dev/null
  complex_request "$outsider_token" "$house_id" GET \
    "$HOST_API_URL/api/batches/$security_batch_id/statistics" "$security_dir/outsider-statistics.json" >/dev/null

  replay_request_id="bsx-carcass-$run_id-security"
  replay_payload="$(jq -nc --arg request_id "$replay_request_id" '
    {yieldRate:0.58,sourceUnit:"复杂矩阵测试场",measuredDate:"2024-08-20",
     reportNumber:"SECURITY-RETRY",evidenceFileId:null,remark:"幂等验证",
     changeReason:"复杂矩阵首次录入",requestId:$request_id}')"
  before_version_count="$(mysql_exec -e "SELECT COUNT(*) FROM batch_carcass_yield_versions WHERE house_id = $house_id AND batch_id = $security_batch_id;")"
  before_dedup_count="$(mysql_exec -e "SELECT COUNT(*) FROM request_dedup WHERE house_id = $house_id AND api = 'batch:carcass-yield' AND request_id = '$replay_request_id';")"
  complex_request "$read_only_token" "$house_id" POST \
    "$HOST_API_URL/api/batches/$security_batch_id/carcass-yields" "$security_dir/read-only-edit.json" "$replay_payload" >/dev/null
  complex_request "$owner_token" "$house_id" POST \
    "$HOST_API_URL/api/batches/$security_batch_id/carcass-yields" "$security_dir/replay-first.json" "$replay_payload" >/dev/null
  first_version_count="$(mysql_exec -e "SELECT COUNT(*) FROM batch_carcass_yield_versions WHERE house_id = $house_id AND batch_id = $security_batch_id;")"
  first_dedup_count="$(mysql_exec -e "SELECT COUNT(*) FROM request_dedup WHERE house_id = $house_id AND api = 'batch:carcass-yield' AND request_id = '$replay_request_id';")"
  complex_request "$owner_token" "$house_id" POST \
    "$HOST_API_URL/api/batches/$security_batch_id/carcass-yields" "$security_dir/replay-second.json" "$replay_payload" >/dev/null
  conflict_payload="$(jq -c '.remark = ((.remark // "") + " changed")' <<<"$replay_payload")"
  complex_request "$owner_token" "$house_id" POST \
    "$HOST_API_URL/api/batches/$security_batch_id/carcass-yields" "$security_dir/replay-conflict.json" "$conflict_payload" >/dev/null
  after_version_count="$(mysql_exec -e "SELECT COUNT(*) FROM batch_carcass_yield_versions WHERE house_id = $house_id AND batch_id = $security_batch_id;")"
  after_dedup_count="$(mysql_exec -e "SELECT COUNT(*) FROM request_dedup WHERE house_id = $house_id AND api = 'batch:carcass-yield' AND request_id = '$replay_request_id';")"
  jq -n \
    --arg run_id "$run_id" --arg scenario_id security-and-retry \
    --slurpfile owner "$security_dir/owner-statistics.json" \
    --slurpfile read_only "$security_dir/read-only-statistics.json" \
    --slurpfile read_only_edit "$security_dir/read-only-edit.json" \
    --slurpfile read_only_history "$security_dir/read-only-history.json" \
    --slurpfile outsider "$security_dir/outsider-statistics.json" \
    --slurpfile first "$security_dir/replay-first.json" \
    --slurpfile second "$security_dir/replay-second.json" \
    --slurpfile conflict "$security_dir/replay-conflict.json" \
    --arg read_only_export_media_type "$read_only_export_media_type" \
    --argjson read_only_export_http_status "$read_only_export_http_status" \
    --argjson read_only_export_house_id "$house_id" \
    --argjson read_only_export_size "$read_only_export_size" \
    --argjson before_versions "$before_version_count" --argjson first_versions "$first_version_count" --argjson after_versions "$after_version_count" \
    --argjson before_dedup "$before_dedup_count" --argjson first_dedup "$first_dedup_count" --argjson after_dedup "$after_dedup_count" '
    {
      runId:$run_id,scenarioId:$scenario_id,
      ownerStatistics:$owner[0],readOnlyStatistics:$read_only[0],
      readOnlyExport:{allowed:true,httpStatus:$read_only_export_http_status,
        houseId:$read_only_export_house_id,mediaType:$read_only_export_media_type,
        file:"security/read-only-export.xlsx",headersFile:"security/read-only-export.headers.txt",
        byteLength:$read_only_export_size,zipSignature:"PK"},
      readOnlyEdit:$read_only_edit[0],
      readOnlyHistory:$read_only_history[0],unrelatedStatistics:$outsider[0],
      replay:{first:$first[0],second:$second[0],conflict:$conflict[0],
        beforeVersionCount:$before_versions,firstVersionCount:$first_versions,afterVersionCount:$after_versions,
        beforeDedupCount:$before_dedup,firstDedupCount:$first_dedup,afterDedupCount:$after_dedup}
    }' >"$security_result"
  node "$VALIDATOR" complex-security "$security_result" "$run_id" security-and-retry \
    "$house_id" "$artifact_root" "$security_dir/validation.json"
  security_validated=1

  scenario_id="security-and-retry"
  scenario_dir="$artifact_root/scenarios/$scenario_id"
  mkdir -p "$scenario_dir/api" "$scenario_dir/xlsx"
  batch_code="$(jq -er --arg id "$scenario_id" '.scenarios[] | select(.id == $id) | .batchCode' "$fixture_manifest")"
  http_status="$(complex_request "$owner_token" "$house_id" GET \
    "$HOST_API_URL/api/batches/$security_batch_id/statistics" "$scenario_dir/api/response.json")"
  [[ "$http_status" == "200" ]] || die "$scenario_id statistics returned HTTP $http_status"
  node "$VALIDATOR" complex-api "$scenario_dir/api/response.json" "$scenario_id" \
    "$security_batch_id" "$COMPLEX_CATALOG" "$scenario_dir/api/validation.json"
  http_status="$(complex_request "$owner_token" "$house_id" GET \
    "$HOST_API_URL/api/reports/batches/$security_batch_id/statistics.xlsx" \
    "$scenario_dir/xlsx/batch-statistics.xlsx" "" "$scenario_dir/xlsx/headers.txt")"
  [[ "$http_status" == "200" ]] || die "$scenario_id workbook returned HTTP $http_status"
  [[ "$(dd if="$scenario_dir/xlsx/batch-statistics.xlsx" bs=1 count=2 2>/dev/null)" == "PK" ]] ||
    die "$scenario_id workbook is not an OOXML ZIP"
  unzip -t "$scenario_dir/xlsx/batch-statistics.xlsx" >"$scenario_dir/xlsx/unzip.txt"
  node "$VALIDATOR" complex-xlsx "$scenario_dir/xlsx/batch-statistics.xlsx" \
    "$scenario_dir/xlsx/headers.txt" "$scenario_id" "$batch_code" "$COMPLEX_CATALOG" \
    "$scenario_dir/xlsx/validation.json"
  api_validated=1
  xlsx_validated=1

  scenarios_json="$(jq -c '.scenarios | map({id,batchRole,houseId,batchId,batchCode,metrics})' "$fixture_manifest")"
  users_json="$(OWNER_PASSWORD="$owner_password" READ_ONLY_PASSWORD="$read_only_password" \
    OUTSIDER_PASSWORD="$outsider_password" jq -c '
      .users | map({
        role:(if .role == "UNRELATED_HOUSE" then "OUTSIDER" else .role end),
        userName:.userName,
        password:(if .role == "OWNER" then env.OWNER_PASSWORD elif .role == "READ_ONLY" then env.READ_ONLY_PASSWORD else env.OUTSIDER_PASSWORD end),
        houseId,houseName
      })' "$fixture_manifest")"
  runtime_defines_file="$(mktemp "${TMPDIR:-/tmp}/rabbit-batch-statistics-defines.XXXXXX")" ||
    die "Cannot create the temporary client define file"
  chmod 600 "$runtime_defines_file"
  RABBIT_DEFINES_PASSWORD="$owner_password" RABBIT_DEFINES_USERS="$users_json" \
    jq -n --arg build_env dev --arg api_base_url "$DEVICE_API_URL" --arg run_id "$run_id" \
    --arg suite complex --arg scenarios "$scenarios_json" \
    --arg user_name "$owner_name" --arg house_name "$house_name" \
    --argjson house_id "$house_id" --argjson batch_id "$security_batch_id" \
    --argjson isolation_house_id "$isolation_house_id" '
    {
      RABBIT_BUILD_ENV:$build_env,RABBIT_API_BASE_URL:$api_base_url,
      RABBIT_E2E_RUN_ID:$run_id,RABBIT_E2E_SUITE:$suite,
      RABBIT_E2E_SCENARIOS_JSON:$scenarios,RABBIT_E2E_USERS_JSON:env.RABBIT_DEFINES_USERS,
      RABBIT_E2E_USERNAME:$user_name,RABBIT_E2E_PASSWORD:env.RABBIT_DEFINES_PASSWORD,
      RABBIT_E2E_HOUSE_NAME:$house_name,RABBIT_E2E_HOUSE_ID:$house_id,
      RABBIT_E2E_BATCH_ID:$batch_id,RABBIT_E2E_ISOLATION_HOUSE_ID:$isolation_house_id
    }' >"$runtime_defines_file"
  [[ "$(stat -f '%Lp' "$runtime_defines_file" 2>/dev/null || stat -c '%a' "$runtime_defines_file")" == "600" ]] ||
    die "Complex define file must have mode 0600"

  printf 'suite=complex\nrun_id=%s\nhost_api_url=%s\ndevice_api_url=%s\nadmin_origin=%s\ndevice_id=%s\ndevice_characteristics=%s\nflyway_version=%s\npnpm_version=%s\nflutter_bin=%s\nadb_bin=%s\nchrome_bin=%s\n' \
    "$run_id" "$HOST_API_URL" "$DEVICE_API_URL" "$admin_origin" "$DEVICE_ID" \
    "${device_characteristics:-unknown}" "$flyway_version" "$pnpm_version" \
    "$RABBIT_FLUTTER_BIN" "$ADB_BIN" "$chrome_bin" >"$artifact_root/environment-sanitized.txt"

  set +e
  env -i PATH="$PATH" HOME="$HOME" TMPDIR="${TMPDIR:-/tmp}" COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
    RABBIT_API_BASE_URL="$HOST_API_URL" ADMIN_DEV_PORT="$admin_dev_port" \
    RABBIT_E2E_DEFINES_FILE="$runtime_defines_file" RABBIT_ADMIN_E2E_ARTIFACT_DIR="$artifact_root" \
    RABBIT_CHROME_BIN="$chrome_bin" corepack pnpm --dir "$ADMIN_DIR" e2e:browser:batch-statistics:real \
    2>&1 | redact_stream "$owner_password" "$read_only_password" "$outsider_password" | tee "$artifact_root/admin/browser-e2e.log"
  pipeline_status=("${PIPESTATUS[@]}")
  admin_status="${pipeline_status[0]}"
  set -e
  [[ "$admin_status" == "0" && "${pipeline_status[1]}" == "0" && "${pipeline_status[2]}" == "0" ]] ||
    die "Complex Admin pipeline failed: ${pipeline_status[*]}"
  node "$VALIDATOR" complex-admin "$artifact_root/result.json" "$run_id" \
    "$fixture_manifest" "$artifact_root" "$artifact_root/admin/validation.json"
  admin_validated=1

  android_app_touched=1
  clear_android_app before || die "Failed to clear $APP_ID before Flutter drive"
  export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_root/android"
  cd "$APP_DIR"
  set +e
  "$RABBIT_FLUTTER_BIN" drive --driver=test_driver/android_e2e_driver.dart \
    --target=integration_test/batches/statistics_android_test.dart --device-id="$DEVICE_ID" \
    --flavor=dev --dart-define-from-file="$runtime_defines_file" \
    2>&1 | redact_stream "$owner_password" "$read_only_password" "$outsider_password" | tee "$artifact_root/android/flutter-drive.log"
  pipeline_status=("${PIPESTATUS[@]}")
  drive_status="${pipeline_status[0]}"
  set -e
  cd "$REPO_DIR"
  [[ "$drive_status" == "0" && "${pipeline_status[1]}" == "0" && "${pipeline_status[2]}" == "0" ]] ||
    die "Complex Flutter pipeline failed: ${pipeline_status[*]}"
  rm -f "$runtime_defines_file" || die "Failed to remove the temporary client define file"
  runtime_defines_file=""
  node - "$artifact_root/android/android_e2e_result.json" "$artifact_root" <<'NODE'
const fs = require("node:fs");
const path = require("node:path");
const [resultFile, artifactRoot] = process.argv.slice(2);
const result = JSON.parse(fs.readFileSync(resultFile, "utf8"));
const scenarioIds = new Set([
  "complex-available", "mixed-data-quality", "mixed-batch-rounding",
  "time-and-cycle-boundaries", "security-and-retry", "mixed-batch-rounding-support",
]);
const safeName = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;
for (const scenario of result.scenarioResults ?? []) {
  if (!scenarioIds.has(scenario.id)) throw new Error(`Unsafe scenario id: ${scenario.id}`);
  const destination = path.join(artifactRoot, "scenarios", scenario.id, "android");
  fs.mkdirSync(destination, { recursive: true });
  for (const name of scenario.screenshotNames ?? []) {
    if (!safeName.test(name)) throw new Error(`Unsafe screenshot name: ${name}`);
    fs.copyFileSync(
      path.join(artifactRoot, "android", `${name}.png`),
      path.join(destination, `${name}.png`),
    );
  }
}
const roles = path.join(artifactRoot, "roles", "android");
fs.mkdirSync(roles, { recursive: true });
for (const evidence of [result.securityEvidence?.readOnly, result.securityEvidence?.outsider]) {
  if (evidence?.screenshotName) {
    if (!safeName.test(evidence.screenshotName)) {
      throw new Error(`Unsafe role screenshot name: ${evidence.screenshotName}`);
    }
    fs.copyFileSync(
      path.join(artifactRoot, "android", `${evidence.screenshotName}.png`),
      path.join(roles, `${evidence.screenshotName}.png`),
    );
  }
}
NODE
  sanitize_android_result "$artifact_root/android/android_e2e_result.json"
  node "$VALIDATOR" complex-android "$artifact_root/android/android_e2e_result.json" "$run_id" \
    "$fixture_manifest" "$artifact_root" "$artifact_root/android/validation.json"
  android_validated=1

  mysql_exec >"$database_raw" <<SQL
SELECT JSON_OBJECT(
  'run_id', '$run_id',
  'target_house_rows', (SELECT COUNT(*) FROM rabbit_houses WHERE id = $house_id),
  'isolation_house_rows', (SELECT COUNT(*) FROM rabbit_houses WHERE id = $isolation_house_id),
  'scenario_batch_rows', (SELECT COUNT(*) FROM batches WHERE id IN ($(jq -r '[.scenarios[].batchId] | join(",")' "$fixture_manifest"))),
  'batch_ids', (SELECT JSON_ARRAYAGG(id) FROM batches WHERE id IN ($(jq -r '[.scenarios[].batchId] | join(",")' "$fixture_manifest"))),
  'fixture_user_rows', (SELECT COUNT(*) FROM sys_user WHERE user_id IN ($(jq -r '[.users[].userId] | join(",")' "$fixture_manifest"))),
  'owner_memberships', (SELECT COUNT(*) FROM house_users WHERE user_id = $(jq -r '.users[] | select(.role == "OWNER") | .userId' "$fixture_manifest") AND house_id = $house_id),
  'read_only_memberships', (SELECT COUNT(*) FROM house_users WHERE user_id = $(jq -r '.users[] | select(.role == "READ_ONLY") | .userId' "$fixture_manifest") AND house_id = $house_id),
  'unrelated_memberships', (SELECT COUNT(*) FROM house_users WHERE user_id = $(jq -r '.users[] | select(.role == "UNRELATED_HOUSE") | .userId' "$fixture_manifest") AND house_id = $isolation_house_id),
  'security_version_rows', (SELECT COUNT(*) FROM batch_carcass_yield_versions WHERE house_id = $house_id AND batch_id = $security_batch_id),
  'security_dedup_rows', (SELECT COUNT(*) FROM request_dedup WHERE house_id = $house_id AND api = 'batch:carcass-yield' AND request_id = '$replay_request_id')
);
SQL
  node "$VALIDATOR" complex-database "$database_raw" "$run_id" "$fixture_manifest" \
    "$artifact_root/database-assertions.json"
  database_validated=1

  printf '%s\0%s\0%s' "$owner_password" "$read_only_password" "$outsider_password" |
    node -e 'const fs=require("node:fs"); const values=fs.readFileSync(0,"utf8").split("\\0"); process.stdout.write(JSON.stringify(values));' |
    node "$VALIDATOR" complex-secret-scan "$artifact_root" "$artifact_root/secret-scan.json"
  pipeline_status=("${PIPESTATUS[@]}")
  [[ "${pipeline_status[0]}" == "0" && "${pipeline_status[1]}" == "0" && "${pipeline_status[2]}" == "0" ]] ||
    die "Complex secret scan pipeline failed: ${pipeline_status[*]}"
  secret_scan_validated=1
  api_scenarios="$(jq -s 'map({key:.scenarioId,value:{api:.passed}}) | from_entries' \
    "$artifact_root"/scenarios/*/api/validation.json)"
  xlsx_scenarios="$(jq -s 'map({key:.scenarioId,value:{xlsx:.passed}}) | from_entries' \
    "$artifact_root"/scenarios/*/xlsx/validation.json)"
  scenario_validations="$(jq -nc --argjson api "$api_scenarios" --argjson xlsx "$xlsx_scenarios" \
    '$api * $xlsx | with_entries(.value += {admin:true,android:true,database:true})')"
  jq -n --arg suite complex --arg run_id "$run_id" --argjson scenarios "$scenario_validations" \
    --slurpfile security "$security_dir/validation.json" \
    --slurpfile admin "$artifact_root/admin/validation.json" \
    --slurpfile android "$artifact_root/android/validation.json" \
    --slurpfile database "$artifact_root/database-assertions.json" \
    --slurpfile secret_scan "$artifact_root/secret-scan.json" \
    '{passed:true,suite:$suite,runId:$run_id,scenarioCount:6,
      scenarioWorkbookCount:6,securityWorkbookCount:1,adminWorkbookCount:$admin[0].workbookCount,
      scenarios:$scenarios,
      apiValidated:true,xlsxValidated:true,security:$security[0],admin:$admin[0],
      android:$android[0],database:$database[0],secretScan:$secret_scan[0]}' \
    >"$artifact_root/validation-proof.json"

  owner_token=""
  read_only_token=""
  outsider_token=""
  owner_password=""
  read_only_password=""
  outsider_password=""
  fixture_password=""
}

main() {
  local chrome_bin=""
  local pnpm_version=""
  local admin_dev_port=""
  local admin_origin=""
  local temporary_cors=""
  local host_lan_ip=""
  local device_characteristics=""
  local surface_orientation=""
  local window_state=""
  local flyway_version=""
  local fixture_output=""
  local fixture_manifest=""
  local credential_profile=""
  local fixture_password=""
  local user_name=""
  local house_name=""
  local house_id=""
  local batch_id=""
  local isolation_house_id=""
  local isolation_batch_id=""
  local login_payload=""
  local login_response=""
  local token=""
  local api_http_status=""
  local export_http_status=""
  local export_content_type=""
  local content_disposition=""
  local batch_code=""
  local admin_status="0"
  local drive_status="0"
  local pipeline_status=()
  local result_file=""

  case "${1:-}" in
  -h | --help)
    usage
    return 0
    ;;
  '') ;;
  *)
    echo "Unknown argument: $1" >&2
    usage >&2
    return 64
    ;;
  esac

  configure_suite || return $?
  for command_name in jq curl node corepack unzip awk sed grep find sort; do
    require_command "$command_name"
  done
  configure_container_runtime
  resolve_compose_project_name
  if command -v shasum >/dev/null 2>&1; then
    :
  elif command -v sha256sum >/dev/null 2>&1; then
    :
  else
    die "Missing SHA-256 tool: shasum or sha256sum"
  fi
  [[ -f "$FIXTURE_SQL" ]] || die "Fixture SQL not found: $FIXTURE_SQL"
  [[ -f "$CLEANUP_SQL" ]] || die "Cleanup SQL not found: $CLEANUP_SQL"
  [[ -f "$VALIDATOR" ]] || die "Validation helper not found: $VALIDATOR"
  if [[ "$SUITE" == "complex" ]]; then
    [[ -f "$COMPLEX_CATALOG" ]] || die "Complex catalog not found: $COMPLEX_CATALOG"
  fi
  [[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] ||
    die "RABBIT_BATCH_STATISTICS_DB_NAME must contain only letters, digits, or underscores"
  [[ "$DB_USER" =~ ^[A-Za-z0-9_.-]+$ ]] ||
    die "RABBIT_BATCH_STATISTICS_DB_USER contains unsupported characters"
  [[ -n "$DB_PASSWORD" ]] || die "RABBIT_BATCH_STATISTICS_DB_PASSWORD must not be empty"
  case "$KEEP_FIXTURE" in 0 | 1) ;; *) die "RABBIT_BATCH_STATISTICS_KEEP_FIXTURE must be 0 or 1" ;; esac
  case "$READY_TIMEOUT_SECONDS" in
  '' | *[!0-9]*) die "RABBIT_BATCH_STATISTICS_READY_TIMEOUT_SECONDS must be a positive integer" ;;
  esac
  [[ "$READY_TIMEOUT_SECONDS" -gt 0 ]] ||
    die "RABBIT_BATCH_STATISTICS_READY_TIMEOUT_SECONDS must be positive"

  HOST_API_URL="$(validate_base_url "$HOST_API_URL" RABBIT_BATCH_STATISTICS_HOST_API_URL)"
  chrome_bin="$(resolve_chrome)"
  pnpm_version="$(corepack pnpm --version)"
  [[ "$pnpm_version" == "$PNPM_VERSION" ]] ||
    die "Admin requires pnpm $PNPM_VERSION through Corepack; resolved $pnpm_version"
  admin_dev_port="$(resolve_admin_dev_port)" ||
    die "Cannot reserve an available loopback port for the Admin Vite server"
  admin_origin="http://127.0.0.1:$admin_dev_port"
  (
    cd "$REPO_DIR"
    corepack pnpm --dir admin exec node --input-type=module -e '
      import { chromium } from "playwright";
      const browser = await chromium.launch({
        executablePath: process.argv[1],
        headless: true,
      });
      await browser.close();
    ' "$chrome_bin"
  ) >/dev/null 2>&1 || die "Playwright could not launch Google Chrome at $chrome_bin"

  # shellcheck source=../app/scripts/toolchain_env.sh
  source "$APP_DIR/scripts/toolchain_env.sh"
  if [[ -n "${RABBIT_ANDROID_E2E_JAVA_HOME:-}" ]]; then
    export RABBIT_JAVA_HOME="$RABBIT_ANDROID_E2E_JAVA_HOME"
  fi
  rabbit_configure_android_toolchain
  ADB_BIN="${ADB_BIN:-$ANDROID_SDK_ROOT/platform-tools/adb}"
  [[ -x "$ADB_BIN" ]] || die "Android adb not found: $ADB_BIN"
  select_device

  device_characteristics="$($ADB_BIN -s "$DEVICE_ID" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r')"
  if [[ -z "$DEVICE_API_URL" ]]; then
    if [[ "$DEVICE_ID" == emulator-* || "$device_characteristics" == *emulator* ]]; then
      DEVICE_API_URL="http://10.0.2.2:8080"
    else
      host_lan_ip="$(resolve_host_lan_ip)"
      [[ -n "$host_lan_ip" ]] ||
        die "Cannot resolve a LAN address for physical device $DEVICE_ID. Set RABBIT_ANDROID_E2E_DEVICE_API_URL."
      DEVICE_API_URL="http://$host_lan_ip:8080"
    fi
  fi
  DEVICE_API_URL="$(validate_base_url "$DEVICE_API_URL" RABBIT_ANDROID_E2E_DEVICE_API_URL)"
  case "$DEVICE_API_URL" in
  *[!A-Za-z0-9:/._\[\]-]*) die "Device API URL contains characters unsafe for adb shell: $DEVICE_API_URL" ;;
  esac

  run_id="$(node -e "process.stdout.write(require('node:crypto').randomBytes(10).toString('hex'))")"
  [[ "$run_id" =~ ^[a-f0-9]{20}$ ]] ||
    die "Generated fixture run_id is not a safe 20-character lowercase hex value"
  artifact_root="$REPO_DIR/artifacts/batch-statistics-cross-client/$run_id"
  mkdir -p "$artifact_root/admin" "$artifact_root/android"
  chmod 700 "$artifact_root"

  # This must happen before the traps can recreate or stop the backend.
  capture_backend_state
  temporary_cors="$backend_original_cors,$admin_origin"
  trap 'trap_interrupt 130' INT
  trap 'trap_interrupt 143' TERM
  trap on_exit EXIT

  original_accelerometer_rotation="$($ADB_BIN -s "$DEVICE_ID" shell settings get system accelerometer_rotation | tr -d '\r')"
  original_user_rotation="$($ADB_BIN -s "$DEVICE_ID" shell settings get system user_rotation | tr -d '\r')"
  original_stay_on_while_plugged_in="$($ADB_BIN -s "$DEVICE_ID" shell settings get global stay_on_while_plugged_in | tr -d '\r')"
  device_touched=1
  "$ADB_BIN" -s "$DEVICE_ID" shell svc power stayon true >/dev/null
  "$ADB_BIN" -s "$DEVICE_ID" shell input keyevent KEYCODE_WAKEUP >/dev/null
  "$ADB_BIN" -s "$DEVICE_ID" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  window_state="$($ADB_BIN -s "$DEVICE_ID" shell dumpsys window 2>/dev/null | tr -d '\r')"
  if [[ "$window_state" == *"mDreamingLockscreen=true"* ||
    "$window_state" == *"isStatusBarKeyguard=true"* ]]; then
    die "Android device $DEVICE_ID is locked; unlock it before running the visible E2E"
  fi
  "$ADB_BIN" -s "$DEVICE_ID" shell settings put system accelerometer_rotation 0 >/dev/null
  "$ADB_BIN" -s "$DEVICE_ID" shell settings put system user_rotation 0 >/dev/null
  sleep 1
  surface_orientation="$($ADB_BIN -s "$DEVICE_ID" shell dumpsys input 2>/dev/null |
    awk -F': ' '/SurfaceOrientation:/{gsub(/\r/, "", $2); print $2; exit}')"
  if [[ -n "$surface_orientation" && "$surface_orientation" != "0" ]]; then
    die "Android device $DEVICE_ID is not in portrait orientation (SurfaceOrientation=$surface_orientation)"
  fi

  backend_touched=1
  (
    cd "$REPO_DIR"
    APP_CAPTCHA_ENABLED=false BACKEND_BIND_ADDRESS=0.0.0.0 \
      APP_CORS_ALLOWED_ORIGINS="$temporary_cors" \
      "${COMPOSE[@]}" up -d --build --force-recreate backend
  ) >"$artifact_root/backend-compose.log" 2>&1
  wait_for_host_backend
  wait_for_device_backend
  assert_backend_database_matches_fixture

  db_container_id="$(compose_service_container_id mysql)"
  [[ -n "$db_container_id" ]] ||
    die "Compose did not report a running mysql service container"
  [[ "$("${CONTAINER_ENGINE[@]}" inspect "$db_container_id" | jq -r '.[0].State.Running // false')" == "true" ]] ||
    die "The mysql service container is not running"

  flyway_version="$(mysql_exec -e "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)), 0) FROM flyway_schema_history WHERE success = 1 AND version REGEXP '^[0-9]+$';")"
  case "$flyway_version" in '' | *[!0-9]*) die "Flyway schema version is not numeric: $flyway_version" ;; esac
  [[ "$flyway_version" -ge 56 ]] ||
    die "Flyway schema version $flyway_version is below required V56"

  if [[ "$SUITE" == "complex" ]]; then
    run_complex_suite "$chrome_bin" "$admin_dev_port" "$admin_origin" \
      "$device_characteristics" "$flyway_version" "$pnpm_version"
    return 0
  fi

  fixture_attempted=1
  fixture_output="$({
    printf "SET @fixture_run_id = '%s';\n" "$run_id"
    cat "$FIXTURE_SQL"
  } |
    mysql_exec)"
  fixture_manifest="$(printf '%s\n' "$fixture_output" | awk 'NF { value = $0 } END { print value }')"
  printf '%s\n' "$fixture_manifest" | jq -e --arg run_id "$run_id" '
    .run_id == $run_id and
    .credential_profile == "e2e-default" and
    (.user_name | type == "string" and length > 0) and
    (.house_id | type == "number" and . > 0 and floor == .) and
    (.batch_id | type == "number" and . > 0 and floor == .) and
    (.isolation_house_id | type == "number" and . > 0 and floor == .) and
    (.isolation_batch_id | type == "number" and . > 0 and floor == .) and
    .mated_cycle_count == 1230 and .litter_count == 1004 and .sold_rabbit_count == 6834
  ' >/dev/null
  printf '%s\n' "$fixture_manifest" | jq '.' >"$artifact_root/fixture-manifest.json"
  fixture_loaded=1

  credential_profile="$(jq -r '.credential_profile' "$artifact_root/fixture-manifest.json")"
  case "$credential_profile" in
  e2e-default) fixture_password="123456" ;;
  *) die "Unsupported fixture credential profile: $credential_profile" ;;
  esac
  user_name="$(jq -r '.user_name' "$artifact_root/fixture-manifest.json")"
  house_id="$(jq -r '.house_id' "$artifact_root/fixture-manifest.json")"
  batch_id="$(jq -r '.batch_id' "$artifact_root/fixture-manifest.json")"
  isolation_house_id="$(jq -r '.isolation_house_id' "$artifact_root/fixture-manifest.json")"
  isolation_batch_id="$(jq -r '.isolation_batch_id' "$artifact_root/fixture-manifest.json")"

  login_payload="$(LOGIN_USER_NAME="$user_name" LOGIN_PASSWORD="$fixture_password" jq -nc \
    '{userName:env.LOGIN_USER_NAME,password:env.LOGIN_PASSWORD}')"
  login_response="$(printf '%s' "$login_payload" |
    curl -fsS --max-time 20 -X POST "$HOST_API_URL/api/auth/login" \
      -H 'Content-Type: application/json' --data-binary @-)"
  token="$(printf '%s' "$login_response" | jq -er '
    select(.code == 0) | .data.token | select(type == "string" and length > 0)
  ')" || die "Fixture login failed or did not return a token"
  login_payload=""
  login_response=""

  api_http_status="$(printf 'header = "Authorization: Bearer %s"\nheader = "X-House-Id: %s"\n' \
    "$token" "$house_id" |
    curl --config - -sS --max-time 30 -o "$artifact_root/api-statistics.json" \
      -w '%{http_code}' "$HOST_API_URL/api/batches/$batch_id/statistics")"
  [[ "$api_http_status" == "200" ]] ||
    die "Statistics API returned HTTP $api_http_status"
  node "$VALIDATOR" api "$artifact_root/api-statistics.json" "$batch_id" \
    "$artifact_root/api-validation.json"
  house_name="$(jq -er '.data.houseName | select(type == "string" and length > 0)' \
    "$artifact_root/api-statistics.json")" || die "Statistics API did not return a house name"
  api_validated=1

  batch_code="BSF-$run_id"
  export_http_status="$(printf 'header = "Authorization: Bearer %s"\nheader = "X-House-Id: %s"\n' \
    "$token" "$house_id" |
    curl --config - -sS --max-time 60 \
      -D "$artifact_root/workbook-headers.txt" \
      -o "$artifact_root/batch-statistics.xlsx" \
      -w '%{http_code}' "$HOST_API_URL/api/reports/batches/$batch_id/statistics.xlsx")"
  export_content_type="$(header_value Content-Type "$artifact_root/workbook-headers.txt")"
  if [[ "$export_http_status" != "200" || "$export_content_type" == application/json* ]]; then
    if jq -e . "$artifact_root/batch-statistics.xlsx" >/dev/null 2>&1; then
      echo "Workbook request crossed into the JSON business-error boundary (HTTP $export_http_status, code $(jq -r '.code // "missing"' "$artifact_root/batch-statistics.xlsx"))" >&2
    else
      echo "Workbook request failed before a valid XLSX response (HTTP $export_http_status)" >&2
    fi
    exit 1
  fi
  [[ "$export_content_type" == "$XLSX_MEDIA_TYPE" ]] ||
    die "Workbook Content-Type is '$export_content_type', expected '$XLSX_MEDIA_TYPE'"
  [[ -s "$artifact_root/batch-statistics.xlsx" ]] || die "Downloaded workbook is empty"
  [[ "$(dd if="$artifact_root/batch-statistics.xlsx" bs=1 count=2 2>/dev/null)" == "PK" ]] ||
    die "Downloaded workbook is not an OOXML PK ZIP"
  content_disposition="$(header_value Content-Disposition "$artifact_root/workbook-headers.txt")"
  printf '%s\n' "$content_disposition" | grep -Eq \
    "filename=\"batch-${batch_code}-statistics-[0-9]{14}\\.xlsx\"" ||
    die "Workbook Content-Disposition lacks the required ASCII filename"
  printf '%s\n' "$content_disposition" | grep -Eq \
    "filename\\*=UTF-8''%E6%89%B9%E6%AC%A1-${batch_code}-%E7%BB%9F%E8%AE%A1-[0-9]{14}\\.xlsx" ||
    die "Workbook Content-Disposition lacks the required UTF-8 filename"
  unzip -t "$artifact_root/batch-statistics.xlsx" >"$artifact_root/workbook-unzip.txt"
  jq -n --arg content_type "$export_content_type" \
    --arg content_disposition "$content_disposition" \
    --argjson http_status "$export_http_status" \
    '{passed:true,httpStatus:$http_status,contentType:$content_type,contentDisposition:$content_disposition,zipTest:"passed"}' \
    >"$artifact_root/xlsx-validation.json"
  xlsx_validated=1

  runtime_defines_file="$(mktemp "${TMPDIR:-/tmp}/rabbit-batch-statistics-defines.XXXXXX")" ||
    die "Cannot create the temporary client define file"
  chmod 600 "$runtime_defines_file"
  RABBIT_DEFINES_PASSWORD="$fixture_password" jq -n \
    --arg build_env dev \
    --arg api_base_url "$DEVICE_API_URL" \
    --arg run_id "$run_id" \
    --arg user_name "$user_name" \
    --arg house_name "$house_name" \
    --argjson house_id "$house_id" \
    --argjson batch_id "$batch_id" \
    --argjson isolation_house_id "$isolation_house_id" \
    --argjson isolation_batch_id "$isolation_batch_id" '
    {
      RABBIT_BUILD_ENV:$build_env,
      RABBIT_API_BASE_URL:$api_base_url,
      RABBIT_E2E_RUN_ID:$run_id,
      RABBIT_E2E_USERNAME:$user_name,
      RABBIT_E2E_PASSWORD:env.RABBIT_DEFINES_PASSWORD,
      RABBIT_E2E_HOUSE_NAME:$house_name,
      RABBIT_E2E_HOUSE_ID:$house_id,
      RABBIT_E2E_BATCH_ID:$batch_id,
      RABBIT_E2E_ISOLATION_HOUSE_ID:$isolation_house_id,
      RABBIT_E2E_ISOLATION_BATCH_ID:$isolation_batch_id
    }
  ' >"$runtime_defines_file"

  printf 'run_id=%s\nhost_api_url=%s\ndevice_api_url=%s\nadmin_origin=%s\ndevice_id=%s\ndevice_characteristics=%s\nflyway_version=%s\npnpm_version=%s\nflutter_bin=%s\nadb_bin=%s\nchrome_bin=%s\nbackend_originally_running=%s\nbackend_original_captcha=%s\nbackend_original_bind=%s\n' \
    "$run_id" "$HOST_API_URL" "$DEVICE_API_URL" "$admin_origin" "$DEVICE_ID" "${device_characteristics:-unknown}" \
    "$flyway_version" "$pnpm_version" "$RABBIT_FLUTTER_BIN" "$ADB_BIN" "$chrome_bin" \
    "$backend_originally_running" "$backend_original_captcha" "$backend_original_bind" \
    >"$artifact_root/environment-sanitized.txt"

  set +e
  env -i \
    PATH="$PATH" HOME="$HOME" TMPDIR="${TMPDIR:-/tmp}" \
    COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
    RABBIT_API_BASE_URL="$HOST_API_URL" \
    ADMIN_DEV_PORT="$admin_dev_port" \
    RABBIT_E2E_DEFINES_FILE="$runtime_defines_file" \
    RABBIT_ADMIN_E2E_ARTIFACT_DIR="$artifact_root/admin" \
    RABBIT_CHROME_BIN="$chrome_bin" \
    corepack pnpm --dir "$ADMIN_DIR" e2e:browser:batch-statistics:real \
    2>&1 | redact_stream "$fixture_password" | tee "$artifact_root/admin/browser-e2e.log"
  pipeline_status=("${PIPESTATUS[@]}")
  admin_status="${pipeline_status[0]}"
  set -e
  [[ "$admin_status" == "0" ]] ||
    die "Admin real browser E2E failed with status $admin_status"
  [[ "${pipeline_status[1]}" == "0" ]] ||
    die "Failed to redact the Admin browser E2E output"
  [[ "${pipeline_status[2]}" == "0" ]] ||
    die "Failed to write the Admin browser E2E log"
  [[ -s "$artifact_root/admin/desktop-detail.png" ]] ||
    die "Admin desktop artifact is missing"
  [[ -s "$artifact_root/admin/batch-statistics.xlsx" ]] ||
    die "Admin workbook artifact is missing"
  jq -n '{passed:true}' >"$artifact_root/admin/validation.json"
  admin_validated=1

  android_app_touched=1
  clear_android_app before || die "Failed to clear $APP_ID before Flutter drive"
  export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_root/android"
  cd "$APP_DIR"
  set +e
  "$RABBIT_FLUTTER_BIN" drive \
    --driver=test_driver/android_e2e_driver.dart \
    --target=integration_test/batches/statistics_android_test.dart \
    --device-id="$DEVICE_ID" \
    --flavor=dev \
    --dart-define-from-file="$runtime_defines_file" \
    2>&1 | redact_stream "$fixture_password" | tee "$artifact_root/android/flutter-drive.log"
  pipeline_status=("${PIPESTATUS[@]}")
  drive_status="${pipeline_status[0]}"
  set -e
  cd "$REPO_DIR"
  [[ "$drive_status" == "0" ]] || die "Flutter drive failed with status $drive_status"
  [[ "${pipeline_status[1]}" == "0" ]] || die "Failed to redact the Flutter drive output"
  [[ "${pipeline_status[2]}" == "0" ]] || die "Failed to write the Flutter drive log"
  rm -f "$runtime_defines_file" || die "Failed to remove the temporary client define file"
  runtime_defines_file=""

  result_file="$artifact_root/android/android_e2e_result.json"
  [[ -s "$result_file" ]] || die "Flutter driver did not write android_e2e_result.json"
  sanitize_android_result "$result_file"
  node "$VALIDATOR" android "$result_file" "$run_id" "$house_id" "$batch_id" \
    "$artifact_root/android/validation.json"
  for screenshot in \
    01-statistics-mating 02-statistics-pregnancy 03-statistics-birth \
    04-statistics-selection 05-statistics-weaning 06-statistics-outbound \
    07-statistics-sales 08-statistics-feed-conversion; do
    [[ -s "$artifact_root/android/$screenshot.png" ]] ||
      die "Missing Android group screenshot: $screenshot.png"
  done
  android_validated=1

  capture_database_assertions
  jq -n \
    --slurpfile api "$artifact_root/api-validation.json" \
    --slurpfile xlsx "$artifact_root/xlsx-validation.json" \
    --slurpfile admin "$artifact_root/admin/validation.json" \
    --slurpfile android "$artifact_root/android/validation.json" \
    --slurpfile database "$artifact_root/database-assertions.json" \
    '{passed:true,api:$api[0],xlsx:$xlsx[0],admin:$admin[0],android:$android[0],database:$database[0]}' \
    >"$artifact_root/validation-proof.json"

  token=""
  fixture_password=""
}

main "$@"
