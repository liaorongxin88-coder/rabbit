#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPO_DIR="$(cd "$PROJECT_DIR/.." && pwd -P)"
source "$SCRIPT_DIR/toolchain_env.sh"

DB_CONTAINER="${RABBIT_ANDROID_E2E_DB_CONTAINER:-rabbit-mysql-1}"
DB_NAME="${RABBIT_ANDROID_E2E_DB_NAME:-rabbit_app}"
DB_USER="${RABBIT_ANDROID_E2E_DB_USER:-root}"
DB_PASSWORD="${RABBIT_ANDROID_E2E_DB_PASSWORD:-rabbit_root}"
HOST_API_URL="${RABBIT_ANDROID_E2E_HOST_API_URL:-http://127.0.0.1:8080}"
DEVICE_API_URL="${RABBIT_ANDROID_E2E_DEVICE_API_URL:-http://10.0.2.2:8080}"
TEXT_SCALE="${RABBIT_ANDROID_E2E_TEXT_SCALE:-1.0}"
TEST_PROFILE="${RABBIT_ANDROID_E2E_PROFILE:-}"
EXPECTED_EFFECTIVE_TEXT_SCALE="${RABBIT_ANDROID_E2E_EXPECTED_EFFECTIVE_TEXT_SCALE:-}"
ADB_BIN="${RABBIT_ANDROID_E2E_ADB:-}"
EMULATOR_BIN="${RABBIT_ANDROID_E2E_EMULATOR:-}"
DEVICE_ID="${RABBIT_ANDROID_E2E_DEVICE_ID:-}"
AVD_NAME="${RABBIT_ANDROID_E2E_AVD:-}"
KEEP_EMULATOR="${RABBIT_ANDROID_E2E_KEEP_EMULATOR:-0}"
ALLOW_DEVICE_SETTINGS="${RABBIT_ANDROID_E2E_ALLOW_DEVICE_SETTINGS:-0}"

started_emulator=0
original_font_scale=""
artifact_dir=""

if [[ -z "$TEST_PROFILE" ]]; then
  if [[ "$TEXT_SCALE" == "1" || "$TEXT_SCALE" == "1.0" ]]; then
    TEST_PROFILE="visual-baseline"
  else
    TEST_PROFILE="accessibility-stress"
  fi
fi
if [[ -z "$EXPECTED_EFFECTIVE_TEXT_SCALE" ]]; then
  EXPECTED_EFFECTIVE_TEXT_SCALE="$(awk -v scale="$TEXT_SCALE" 'BEGIN { print (scale > 1.5 ? 1.5 : scale) }')"
fi
usage() {
  cat <<'USAGE'
Usage: ./scripts/android_e2e.sh

Optional environment variables:
  RABBIT_ANDROID_E2E_DEVICE_ID       Existing Android device/emulator id.
  RABBIT_ANDROID_E2E_AVD             AVD to start when no device is connected.
  RABBIT_ANDROID_E2E_TEXT_SCALE      Android font scale, defaults to 1.0.
  RABBIT_ANDROID_E2E_PROFILE         Artifact profile label; inferred from font scale.
  RABBIT_ANDROID_E2E_EXPECTED_EFFECTIVE_TEXT_SCALE
                                      App text scale after ergonomic clamping.
  RABBIT_ANDROID_E2E_JAVA_HOME       JDK 21 home used by Gradle.
  RABBIT_ANDROID_E2E_KEEP_EMULATOR   Keep an emulator started by this script (1/0).
  RABBIT_ANDROID_E2E_ALLOW_DEVICE_SETTINGS
                                      Allow font-scale changes on a physical device (1/0).
  RABBIT_ANDROID_E2E_DEVICE_API_URL  Backend URL visible from Android.
  RABBIT_ANDROID_E2E_HOST_API_URL    Backend URL visible from the host.
USAGE
}

cleanup() {
  if [[ -n "$DEVICE_ID" && -n "$original_font_scale" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" shell settings put system font_scale "$original_font_scale" >/dev/null 2>&1 || true
  fi
  if [[ "$started_emulator" == "1" && "$KEEP_EMULATOR" != "1" && -n "$DEVICE_ID" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" emu kill >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "$TEST_PROFILE" == "visual-baseline" ]] &&
   ! awk -v system_scale="$TEXT_SCALE" -v effective_scale="$EXPECTED_EFFECTIVE_TEXT_SCALE" \
     'BEGIN { exit !((system_scale >= 0.99 && system_scale <= 1.01) && (effective_scale >= 0.99 && effective_scale <= 1.01)) }'; then
  echo "visual-baseline screenshots require system and effective text scale 1.0" >&2
  exit 64
fi

for command_name in docker curl awk; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: $command_name" >&2
    exit 69
  fi
done

if [[ -n "${RABBIT_ANDROID_E2E_JAVA_HOME:-}" ]]; then
  RABBIT_JAVA_HOME="$RABBIT_ANDROID_E2E_JAVA_HOME"
fi
rabbit_configure_android_toolchain

ANDROID_SDK_DIR="$ANDROID_SDK_ROOT"
ADB_BIN="${ADB_BIN:-$ANDROID_SDK_DIR/platform-tools/adb}"
EMULATOR_BIN="${EMULATOR_BIN:-$ANDROID_SDK_DIR/emulator/emulator}"

if [[ ! -x "$ADB_BIN" ]]; then
  echo "Android adb not found: $ADB_BIN" >&2
  exit 69
fi

if ! curl -fsS "$HOST_API_URL/api/houses" >/dev/null; then
  echo "Rabbit backend is not reachable at $HOST_API_URL" >&2
  exit 69
fi

migration_present=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
  mysql -N -B -u"$DB_USER" -D "$DB_NAME" \
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '11' AND success = 1;")
if [[ "$migration_present" != "1" ]]; then
  echo "Expected successful Flyway V11 in $DB_NAME" >&2
  exit 65
fi

"$ADB_BIN" start-server >/dev/null

select_device() {
  "$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'
}

if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID="$(select_device)"
fi

if [[ -z "$DEVICE_ID" ]]; then
  if [[ ! -x "$EMULATOR_BIN" ]]; then
    echo "No Android device is connected and emulator was not found: $EMULATOR_BIN" >&2
    exit 69
  fi
  if [[ -z "$AVD_NAME" ]]; then
    AVD_NAME="$($EMULATOR_BIN -list-avds | awk 'NF { print; exit }')"
  fi
  if [[ -z "$AVD_NAME" ]]; then
    echo "No Android device or AVD is available" >&2
    exit 69
  fi
  bootstrap_dir="$PROJECT_DIR/build/android-e2e/bootstrap"
  mkdir -p "$bootstrap_dir"
  "$EMULATOR_BIN" -avd "$AVD_NAME" -no-snapshot-load -no-snapshot-save \
    >"$bootstrap_dir/emulator.log" 2>&1 &
  started_emulator=1
  for _ in $(seq 1 120); do
    DEVICE_ID="$(select_device)"
    if [[ -n "$DEVICE_ID" ]] && \
       [[ "$("$ADB_BIN" -s "$DEVICE_ID" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
      break
    fi
    sleep 2
  done
fi

if [[ -z "$DEVICE_ID" ]] || \
   [[ "$("$ADB_BIN" -s "$DEVICE_ID" get-state 2>/dev/null)" != "device" ]]; then
  echo "Android device did not become ready" >&2
  exit 69
fi

if [[ "$DEVICE_ID" == emulator-* || "$ALLOW_DEVICE_SETTINGS" == "1" ]]; then
  original_font_scale="$("$ADB_BIN" -s "$DEVICE_ID" shell settings get system font_scale | tr -d '\r')"
  "$ADB_BIN" -s "$DEVICE_ID" shell settings put system font_scale "$TEXT_SCALE"
elif [[ "$TEXT_SCALE" != "1.0" && "$TEXT_SCALE" != "1" ]]; then
  echo "Refusing to change font scale on physical device $DEVICE_ID without RABBIT_ANDROID_E2E_ALLOW_DEVICE_SETTINGS=1" >&2
  exit 64
fi

fixture_file="$REPO_DIR/backend/src/test/resources/fixtures/batch_outbound_fixture.sql"
if [[ ! -f "$fixture_file" ]]; then
  echo "Android E2E fixture not found: $fixture_file" >&2
  exit 66
fi
fixture_output=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" -i "$DB_CONTAINER" \
  mysql --default-character-set=utf8mb4 -u"$DB_USER" "$DB_NAME" < "$fixture_file")

run_id=$(awk 'NR == 2 { print $1 }' <<<"$fixture_output")
primary_house_id=$(awk 'NR == 2 { print $3 }' <<<"$fixture_output")
g01_rabbit_id=$(awk '$1 == "FIXTURE-G01" { print $2 }' <<<"$fixture_output")
g02_rabbit_id=$(awk '$1 == "FIXTURE-G02" { print $2 }' <<<"$fixture_output")
g06_rabbit_id=$(awk '$1 == "FIXTURE-G06" { print $2 }' <<<"$fixture_output")

if [[ -z "$run_id" || -z "$primary_house_id" || -z "$g01_rabbit_id" || \
      -z "$g02_rabbit_id" || -z "$g06_rabbit_id" ]]; then
  echo "Unable to parse batch outbound fixture output" >&2
  exit 65
fi

artifact_dir="$PROJECT_DIR/build/android-e2e/$run_id"
mkdir -p "$artifact_dir"
printf '%s\n' "$fixture_output" > "$artifact_dir/fixture.txt"
physical_size="$($ADB_BIN -s "$DEVICE_ID" shell wm size | awk -F ': ' '/Physical size/ { print $2; exit }' | tr -d '\r')"
physical_density="$($ADB_BIN -s "$DEVICE_ID" shell wm density | awk -F ': ' '/Physical density/ { print $2; exit }' | tr -d '\r')"
diagonal_inches="$(awk -F '[x ]' -v size="$physical_size" -v density="$physical_density" 'BEGIN { split(size, px, "x"); if (density > 0) printf "%.2f", sqrt(px[1]^2 + px[2]^2) / density; else print "unknown" }')"
printf 'device=%s\navd=%s\nprofile=%s\nsystem_text_scale=%s\neffective_text_scale=%s\nscreenshot_text_scale=%s\nphysical_size=%s\nphysical_density=%s\ndiagonal_inches=%s\nrun_id=%s\nprimary_house_id=%s\n' \
  "$DEVICE_ID" "${AVD_NAME:-existing}" "$TEST_PROFILE" "$TEXT_SCALE" "$EXPECTED_EFFECTIVE_TEXT_SCALE" \
  "$EXPECTED_EFFECTIVE_TEXT_SCALE" "$physical_size" "$physical_density" "$diagonal_inches" "$run_id" "$primary_house_id" \
  > "$artifact_dir/environment.txt"

export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_dir"

cd "$PROJECT_DIR"
"$RABBIT_FLUTTER_BIN" drive \
  --driver=test_driver/android_e2e_driver.dart \
  --target=integration_test/batch_outbound_android_test.dart \
  --device-id="$DEVICE_ID" \
  --flavor=dev \
  --dart-define=RABBIT_BUILD_ENV=dev \
  --dart-define=RABBIT_API_BASE_URL="$DEVICE_API_URL" \
  --dart-define=RABBIT_E2E_RUN_ID="$run_id" \
  --dart-define=RABBIT_E2E_PASSWORD=123456 \
  --dart-define=RABBIT_E2E_PRIMARY_HOUSE_ID="$primary_house_id" \
  --dart-define=RABBIT_E2E_G01_RABBIT_ID="$g01_rabbit_id" \
  --dart-define=RABBIT_E2E_EXPECTED_TEXT_SCALE="$TEXT_SCALE" \
  --dart-define=RABBIT_E2E_EXPECTED_EFFECTIVE_TEXT_SCALE="$EXPECTED_EFFECTIVE_TEXT_SCALE" \
  2>&1 | tee "$artifact_dir/flutter-drive.log"

screenshots=(
  01-login
  02-view-permission
  03-selection
  04-early-sale-selected
  05-confirmation
  06-conflict
  07-success
)
for screenshot in "${screenshots[@]}"; do
  if [[ ! -s "$artifact_dir/$screenshot.png" ]]; then
    echo "Missing Android E2E screenshot: $screenshot.png" >&2
    exit 1
  fi
done
printf '%s.png\n' "${screenshots[@]}" > "$artifact_dir/screenshots.txt"

read -r sale_orders sale_items sold_rabbits g01_quarantined completed_tasks completed_requests conflict_requests early_sale_items <<<"$(
  docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
    mysql -N -B -u"$DB_USER" -D "$DB_NAME" -e "
      SELECT
        (SELECT COUNT(*) FROM sale_orders WHERE house_id = $primary_house_id),
        (SELECT COUNT(*) FROM sale_order_items soi JOIN sale_orders so ON so.id = soi.sale_order_id WHERE so.house_id = $primary_house_id),
        (SELECT COUNT(*) FROM rabbits WHERE house_id = $primary_house_id AND id IN ($g02_rabbit_id, $g06_rabbit_id) AND is_active = FALSE),
        (SELECT COUNT(*) FROM rabbits WHERE house_id = $primary_house_id AND id = $g01_rabbit_id AND is_active = TRUE AND is_quarantined = TRUE),
        (SELECT COUNT(*) FROM outbound_tasks WHERE house_id = $primary_house_id AND status = 'COMPLETED'),
        (SELECT COUNT(*) FROM outbound_requests WHERE house_id = $primary_house_id AND status = 'COMPLETED'),
        (SELECT COUNT(*) FROM outbound_requests WHERE house_id = $primary_house_id AND status = 'CONFLICT'),
        (SELECT COUNT(*) FROM sale_order_items soi JOIN sale_orders so ON so.id = soi.sale_order_id WHERE so.house_id = $primary_house_id AND soi.rabbit_id = $g02_rabbit_id AND soi.early_sale = TRUE AND length(trim(soi.early_sale_reason)) > 0);
    "
)"

actual="$sale_orders $sale_items $sold_rabbits $g01_quarantined $completed_tasks $completed_requests $conflict_requests $early_sale_items"
expected="1 2 2 1 1 1 1 1"
printf 'expected=%s\nactual=%s\n' "$expected" "$actual" | tee "$artifact_dir/database_assertions.txt"
if [[ "$actual" != "$expected" ]]; then
  echo "Android E2E database assertions failed" >&2
  exit 1
fi

echo "Android E2E passed"
echo "Fixture run: $run_id"
echo "Artifacts: $artifact_dir"
