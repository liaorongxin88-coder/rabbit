#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPO_DIR="$(cd "$PROJECT_DIR/.." && pwd -P)"
source "$SCRIPT_DIR/toolchain_env.sh"

DB_CONTAINER="${RABBIT_ANDROID_E2E_DB_CONTAINER:-rabbit_mysql_1}"
DB_NAME="${RABBIT_ANDROID_E2E_DB_NAME:-rabbit_app}"
DB_USER="${RABBIT_ANDROID_E2E_DB_USER:-root}"
DB_PASSWORD="${RABBIT_ANDROID_E2E_DB_PASSWORD:-rabbit_root}"
DEVICE_ID="${RABBIT_ANDROID_E2E_DEVICE_ID:-}"
DEVICE_API_URL="${RABBIT_ANDROID_E2E_DEVICE_API_URL:-http://10.0.2.2:8080}"
HOST_API_URL="${RABBIT_ANDROID_E2E_HOST_API_URL:-http://127.0.0.1:8080}"

rabbit_configure_android_toolchain
ADB_BIN="${RABBIT_ANDROID_E2E_ADB:-$ANDROID_SDK_ROOT/platform-tools/adb}"

probe="$(curl -s -o /dev/null -w '%{http_code}' "$HOST_API_URL/api/houses" || true)"
if [[ "$probe" != "200" && "$probe" != "401" ]]; then
  echo "Rabbit backend is not reachable at $HOST_API_URL (HTTP ${probe:-none})" >&2
  exit 69
fi

migration_present=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
  mysql -N -B -u"$DB_USER" -D "$DB_NAME" \
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '40' AND success = 1;")
if [[ "$migration_present" != "1" ]]; then
  echo "Expected successful Flyway V40 in $DB_NAME" >&2
  exit 65
fi

if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi
if [[ -z "$DEVICE_ID" ]] ||
  [[ "$($ADB_BIN -s "$DEVICE_ID" get-state 2>/dev/null)" != "device" ]]; then
  echo "A connected Android emulator is required" >&2
  exit 69
fi

fixture_file="$REPO_DIR/backend/src/test/resources/fixtures/additional_client_modifications_fixture.sql"
fixture_output=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" -i "$DB_CONTAINER" \
  mysql --default-character-set=utf8mb4 -u"$DB_USER" "$DB_NAME" <"$fixture_file")

run_id=$(awk 'NR == 2 { print $1 }' <<<"$fixture_output")
house_id=$(awk 'NR == 2 { print $2 }' <<<"$fixture_output")
breeder_id=$(awk 'NR == 2 { print $3 }' <<<"$fixture_output")
replacement_id=$(awk 'NR == 2 { print $4 }' <<<"$fixture_output")
commodity_id=$(awk 'NR == 2 { print $5 }' <<<"$fixture_output")
mother_id=$(awk 'NR == 2 { print $6 }' <<<"$fixture_output")
batch_id=$(awk 'NR == 2 { print $7 }' <<<"$fixture_output")
weaning_record_id=$(awk 'NR == 2 { print $8 }' <<<"$fixture_output")
target_cage_id=$(awk 'NR == 2 { print $9 }' <<<"$fixture_output")

for value in "$run_id" "$house_id" "$breeder_id" "$replacement_id" \
  "$commodity_id" "$mother_id" "$batch_id" "$weaning_record_id" "$target_cage_id"; do
  if [[ -z "$value" ]]; then
    echo "Unable to parse additional-modifications fixture output" >&2
    printf '%s\n' "$fixture_output" >&2
    exit 65
  fi
done

artifact_dir="$PROJECT_DIR/build/android-e2e/additional-modifications-$run_id"
mkdir -p "$artifact_dir"
printf '%s\n' "$fixture_output" >"$artifact_dir/fixture.txt"
printf 'device=%s\napi=%s\nrun_id=%s\nhouse_id=%s\n' \
  "$DEVICE_ID" "$DEVICE_API_URL" "$run_id" "$house_id" >"$artifact_dir/environment.txt"
export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_dir"

original_rotation="$($ADB_BIN -s "$DEVICE_ID" shell settings get system accelerometer_rotation | tr -d '\r')"
cleanup() {
  "$ADB_BIN" -s "$DEVICE_ID" shell settings put system accelerometer_rotation \
    "$original_rotation" >/dev/null 2>&1 || true
}
trap cleanup EXIT

$ADB_BIN -s "$DEVICE_ID" shell svc power stayon true
$ADB_BIN -s "$DEVICE_ID" shell input keyevent KEYCODE_WAKEUP
$ADB_BIN -s "$DEVICE_ID" shell wm dismiss-keyguard >/dev/null 2>&1 || true
$ADB_BIN -s "$DEVICE_ID" shell settings put system accelerometer_rotation 0 >/dev/null
$ADB_BIN -s "$DEVICE_ID" shell settings put system user_rotation 0 >/dev/null

cd "$PROJECT_DIR"
set +e
"$RABBIT_FLUTTER_BIN" drive \
  --driver=test_driver/android_e2e_driver.dart \
  --target=integration_test/batches/additional_modifications_android_test.dart \
  --device-id="$DEVICE_ID" \
  --flavor=dev \
  --dart-define=RABBIT_BUILD_ENV=dev \
  --dart-define=RABBIT_API_BASE_URL="$DEVICE_API_URL" \
  --dart-define=RABBIT_E2E_RUN_ID="$run_id" \
  --dart-define=RABBIT_E2E_PASSWORD=123456 \
  --dart-define=RABBIT_E2E_HOUSE_ID="$house_id" \
  --dart-define=RABBIT_E2E_BREEDER_ID="$breeder_id" \
  --dart-define=RABBIT_E2E_REPLACEMENT_ID="$replacement_id" \
  --dart-define=RABBIT_E2E_COMMODITY_ID="$commodity_id" \
  --dart-define=RABBIT_E2E_BATCH_ID="$batch_id" \
  --dart-define=RABBIT_E2E_WEANING_RECORD_ID="$weaning_record_id" \
  2>&1 | tee "$artifact_dir/flutter-drive.log"
drive_status=${PIPESTATUS[0]}
set -e

if [[ "$drive_status" != "0" ]]; then
  echo "flutter drive failed (status $drive_status); see $artifact_dir/flutter-drive.log" >&2
  exit "$drive_status"
fi

screenshots=(
  01-daily-care-reminder
  02-house-prefixed-batch-code
  03-breeder-sale-entry
  04-breeder-sale-sheet
  05-breeder-sold
  06-replacement-sale-entry
  07-pending-separation
  08-separation-sheet
  09-separation-complete
)
for screenshot in "${screenshots[@]}"; do
  if [[ ! -s "$artifact_dir/$screenshot.png" ]]; then
    echo "Missing Android E2E screenshot: $screenshot.png" >&2
    exit 1
  fi
done
printf '%s.png\n' "${screenshots[@]}" >"$artifact_dir/screenshots.txt"

read -r breeder_inactive sale_rows replacement_active waiting_count kits target_state \
  batch_links sale_ready_tasks daily_care_tasks \
  <<<"$(
    docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
      mysql -N -B -u"$DB_USER" -D "$DB_NAME" -e "
      SELECT
        (SELECT COUNT(*) FROM rabbits WHERE id = $breeder_id AND is_active = FALSE),
        (SELECT COUNT(*) FROM sale_order_items WHERE rabbit_id = $breeder_id),
        (SELECT COUNT(*) FROM rabbits WHERE id = $replacement_id AND is_active = TRUE),
        (SELECT waiting_count FROM weaning_records WHERE id = $weaning_record_id),
        (SELECT COUNT(*) FROM rabbits WHERE mother_id = $mother_id AND birth_batch_id = $batch_id
           AND cage_id = $target_cage_id AND type = '2' AND is_active = TRUE),
        (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = $target_cage_id),
        (SELECT COUNT(*) FROM batch_rabbits br
           INNER JOIN rabbits r ON r.id = br.rabbit_id
           WHERE br.batch_id = $batch_id AND r.mother_id = $mother_id AND br.is_active = TRUE),
        (SELECT COUNT(*) FROM work_tasks wt
           INNER JOIN rabbits r ON r.id = wt.rabbit_id
           WHERE r.mother_id = $mother_id AND wt.task_type = 'SALE_READY' AND wt.status = 'PENDING'),
        (SELECT COUNT(*) FROM work_tasks wt
           INNER JOIN rabbits r ON r.id = wt.rabbit_id
           WHERE r.mother_id = $mother_id AND wt.task_type = 'COMMODITY_ADAPTATION_CARE'
             AND wt.status = 'PENDING');
    "
  )"

actual="$breeder_inactive $sale_rows $replacement_active $waiting_count $kits $target_state $batch_links $sale_ready_tasks $daily_care_tasks"
expected="1 1 1 2 2 3:2 2 2 2"
printf 'expected=%s\nactual=%s\n' "$expected" "$actual" | tee "$artifact_dir/database-assertions.txt"
if [[ "$actual" != "$expected" ]]; then
  echo "Android E2E database assertions failed" >&2
  exit 1
fi

printf 'Android additional-modifications E2E passed\nartifacts=%s\n' "$artifact_dir"
