#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPO_DIR="$(cd "$PROJECT_DIR/.." && pwd -P)"
source "$SCRIPT_DIR/toolchain_env.sh"

resolve_db_container() {
  if [[ -n "${RABBIT_ANDROID_E2E_DB_CONTAINER:-}" ]]; then
    printf '%s\n' "$RABBIT_ANDROID_E2E_DB_CONTAINER"
    return
  fi
  # Compose project names differ between Docker Desktop and the legacy setup.
  # Prefer the current underscore form, while retaining the historical name.
  for candidate in rabbit_mysql_1 rabbit-mysql-1; do
    if command -v docker >/dev/null 2>&1 &&
       docker inspect "$candidate" >/dev/null 2>&1; then
      printf '%s\n' "$candidate"
      return
    fi
  done
  printf '%s\n' 'rabbit_mysql_1'
}

DB_CONTAINER="$(resolve_db_container)"
DB_NAME="${RABBIT_ANDROID_E2E_DB_NAME:-rabbit_app}"
DB_USER="${RABBIT_ANDROID_E2E_DB_USER:-root}"
DB_PASSWORD="${RABBIT_ANDROID_E2E_DB_PASSWORD:-rabbit_root}"
HOST_API_URL="${RABBIT_ANDROID_E2E_HOST_API_URL:-http://127.0.0.1:8080}"
DEVICE_API_URL="${RABBIT_ANDROID_E2E_DEVICE_API_URL:-http://10.0.2.2:8080}"
TEXT_SCALE="${RABBIT_ANDROID_E2E_TEXT_SCALE:-1.0}"
EXPECTED_EFFECTIVE_TEXT_SCALE="${RABBIT_ANDROID_E2E_EXPECTED_EFFECTIVE_TEXT_SCALE:-}"
ADB_BIN="${RABBIT_ANDROID_E2E_ADB:-}"
DEVICE_ID="${RABBIT_ANDROID_E2E_DEVICE_ID:-}"
ALLOW_DEVICE_SETTINGS="${RABBIT_ANDROID_E2E_ALLOW_DEVICE_SETTINGS:-0}"
KEEP_DEVICE_AWAKE="${RABBIT_ANDROID_E2E_KEEP_DEVICE_AWAKE:-1}"

original_font_scale=""
original_stay_on_while_plugged_in=""
time_accelerator_pid=""

if [[ -z "$EXPECTED_EFFECTIVE_TEXT_SCALE" ]]; then
  EXPECTED_EFFECTIVE_TEXT_SCALE="$(awk -v scale="$TEXT_SCALE" 'BEGIN { print (scale > 2.0 ? 2.0 : scale) }')"
fi

cleanup() {
  if [[ -n "$time_accelerator_pid" ]]; then
    kill "$time_accelerator_pid" >/dev/null 2>&1 || true
    wait "$time_accelerator_pid" >/dev/null 2>&1 || true
  fi
  if [[ -n "$DEVICE_ID" && -n "$original_font_scale" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" shell settings put system font_scale "$original_font_scale" >/dev/null 2>&1 || true
  fi
  if [[ -n "$DEVICE_ID" && -n "$original_stay_on_while_plugged_in" ]]; then
    if [[ "$original_stay_on_while_plugged_in" == "null" ]]; then
      "$ADB_BIN" -s "$DEVICE_ID" shell settings delete global stay_on_while_plugged_in >/dev/null 2>&1 || true
    else
      "$ADB_BIN" -s "$DEVICE_ID" shell settings put global stay_on_while_plugged_in \
        "$original_stay_on_while_plugged_in" >/dev/null 2>&1 || true
    fi
  fi
}
trap cleanup EXIT

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
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '24' AND success = 1;")
if [[ "$migration_present" != "1" ]]; then
  echo "Expected successful Flyway V24 in $DB_NAME" >&2
  exit 65
fi

"$ADB_BIN" start-server >/dev/null
if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi
if [[ -z "$DEVICE_ID" ]] || \
   [[ "$("$ADB_BIN" -s "$DEVICE_ID" get-state 2>/dev/null)" != "device" ]]; then
  echo "A connected Android device is required for the Batch lifecycle E2E" >&2
  exit 69
fi

if [[ "$KEEP_DEVICE_AWAKE" == "1" ]]; then
  original_stay_on_while_plugged_in="$(
    "$ADB_BIN" -s "$DEVICE_ID" shell settings get global stay_on_while_plugged_in | tr -d '\r'
  )"
  "$ADB_BIN" -s "$DEVICE_ID" shell svc power stayon true
  "$ADB_BIN" -s "$DEVICE_ID" shell input keyevent KEYCODE_WAKEUP
  "$ADB_BIN" -s "$DEVICE_ID" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  if "$ADB_BIN" -s "$DEVICE_ID" shell dumpsys window | \
      tr -d '\r' | grep -Eq 'mDreamingLockscreen=true|isStatusBarKeyguard=true'; then
    echo "Android device $DEVICE_ID is locked; unlock it before starting the visible Batch lifecycle E2E" >&2
    exit 69
  fi
fi

if [[ "$DEVICE_ID" == emulator-* || "$ALLOW_DEVICE_SETTINGS" == "1" ]]; then
  original_font_scale="$("$ADB_BIN" -s "$DEVICE_ID" shell settings get system font_scale | tr -d '\r')"
  "$ADB_BIN" -s "$DEVICE_ID" shell settings put system font_scale "$TEXT_SCALE"
elif [[ "$TEXT_SCALE" != "1.0" && "$TEXT_SCALE" != "1" ]]; then
  echo "Refusing to change font scale on physical device $DEVICE_ID without RABBIT_ANDROID_E2E_ALLOW_DEVICE_SETTINGS=1" >&2
  exit 64
fi

fixture_file="$REPO_DIR/backend/src/test/resources/fixtures/batch_lifecycle_fixture.sql"
if [[ ! -f "$fixture_file" ]]; then
  echo "Batch lifecycle fixture not found: $fixture_file" >&2
  exit 66
fi
fixture_output=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" -i "$DB_CONTAINER" \
  mysql --default-character-set=utf8mb4 -u"$DB_USER" "$DB_NAME" < "$fixture_file")

read -r run_id house_id mother_a_id mother_b_id father_id <<<"$(awk 'NR == 2 { print $1, $2, $3, $4, $5 }' <<<"$fixture_output")"
if [[ -z "$run_id" || -z "$house_id" || -z "$mother_a_id" || \
      -z "$mother_b_id" || -z "$father_id" ]]; then
  echo "Unable to parse Batch lifecycle fixture output" >&2
  exit 65
fi

artifact_dir="$PROJECT_DIR/build/android-batch-lifecycle-e2e/$run_id"
mkdir -p "$artifact_dir"
printf '%s\n' "$fixture_output" > "$artifact_dir/fixture.txt"
physical_size="$($ADB_BIN -s "$DEVICE_ID" shell wm size | awk -F ': ' '/Physical size/ { print $2; exit }' | tr -d '\r')"
physical_density="$($ADB_BIN -s "$DEVICE_ID" shell wm density | awk -F ': ' '/Physical density/ { print $2; exit }' | tr -d '\r')"
printf 'device=%s\nprofile=batch-lifecycle\ntime_mode=compressed-next-event-dates\nsystem_text_scale=%s\neffective_text_scale=%s\nphysical_size=%s\nphysical_density=%s\nrun_id=%s\nprimary_house_id=%s\nmother_a_id=%s\nmother_b_id=%s\nfather_id=%s\n' \
  "$DEVICE_ID" "$TEXT_SCALE" "$EXPECTED_EFFECTIVE_TEXT_SCALE" "$physical_size" "$physical_density" \
  "$run_id" "$house_id" "$mother_a_id" "$mother_b_id" "$father_id" \
  > "$artifact_dir/environment.txt"

export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_dir"

# Production reminders are date-gated. Compress only this isolated fixture's
# pending reminder dates so a 30+ day lifecycle remains visible in one run.
(
  while true; do
    docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
      mysql --default-character-set=utf8mb4 -N -B -u"$DB_USER" -D "$DB_NAME" -e "
        UPDATE breeding_cycles bc
        JOIN batches b ON b.id = bc.batch_id
        SET bc.next_event_date = CURDATE()
        WHERE b.house_id = $house_id
          AND b.batch_code = 'B-LIFECYCLE-$run_id'
          AND bc.closed_at IS NULL
          AND bc.next_event_date > CURDATE();
        UPDATE batch_rabbits br
        JOIN batches b ON b.id = br.batch_id
        SET br.next_event_date = CURDATE()
        WHERE b.house_id = $house_id
          AND b.batch_code = 'B-LIFECYCLE-$run_id'
          AND br.is_active = TRUE
          AND br.next_event_date > CURDATE();
      " >/dev/null
    sleep 0.25
  done
) &
time_accelerator_pid=$!

cd "$PROJECT_DIR"
"$RABBIT_FLUTTER_BIN" drive \
  --driver=test_driver/android_e2e_driver.dart \
  --target=integration_test/batch_lifecycle_android_test.dart \
  --device-id="$DEVICE_ID" \
  --flavor=dev \
  --dart-define=RABBIT_BUILD_ENV=dev \
  --dart-define=RABBIT_API_BASE_URL="$DEVICE_API_URL" \
  --dart-define=RABBIT_E2E_RUN_ID="$run_id" \
  --dart-define=RABBIT_E2E_PASSWORD=123456 \
  --dart-define=RABBIT_E2E_PRIMARY_HOUSE_ID="$house_id" \
  --dart-define=RABBIT_E2E_MOTHER_A_ID="$mother_a_id" \
  --dart-define=RABBIT_E2E_MOTHER_B_ID="$mother_b_id" \
  --dart-define=RABBIT_E2E_FATHER_ID="$father_id" \
  --dart-define=RABBIT_E2E_EXPECTED_TEXT_SCALE="$TEXT_SCALE" \
  --dart-define=RABBIT_E2E_EXPECTED_EFFECTIVE_TEXT_SCALE="$EXPECTED_EFFECTIVE_TEXT_SCALE" \
  2>&1 | tee "$artifact_dir/flutter-drive.log"

kill "$time_accelerator_pid" >/dev/null 2>&1 || true
wait "$time_accelerator_pid" >/dev/null 2>&1 || true
time_accelerator_pid=""

screenshots=(
  01-login
  02-batch-created
  03-bulk-mating-selection
  04-bulk-mating-confirmation
  05-pregnancy-mother-a
  06-empty-pregnancy-mother-b
  07-prepartum-first-cycle
  08-parturition-first-cycle
  09-overlap-bulk-mating
  10-weaning-first-cycle
  11-pregnancy-second-cycle
  12-prepartum-second-cycle
  13-parturition-second-cycle
  14-weaning-second-cycle
  15-outbound-selection
  16-outbound-confirmation
  17-outbound-success
  18-batch-completed
)
for screenshot in "${screenshots[@]}"; do
  if [[ ! -s "$artifact_dir/$screenshot.png" ]]; then
    echo "Missing Batch lifecycle screenshot: $screenshot.png" >&2
    exit 1
  fi
done
printf '%s.png\n' "${screenshots[@]}" > "$artifact_dir/screenshots.txt"

batch_id=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
  mysql -N -B -u"$DB_USER" -D "$DB_NAME" \
  -e "SELECT id FROM batches WHERE house_id = $house_id AND batch_code = 'B-LIFECYCLE-$run_id' LIMIT 1;")
if [[ -z "$batch_id" ]]; then
  echo "Lifecycle Batch was not created" >&2
  exit 1
fi

expected="1 9 0 3 2 1 1 2 2 7 7 7 7 1 1 0 1 1"
actual=""
for _ in {1..40}; do
  read -r completed_batches batch_members active_members cycles weaned_cycles empty_cycles overlap_cycles weanings parturitions born_kits lineage_kits sold_kits sale_items mother_b_active mother_a_inactive nursing_kits cull_departures sale_orders <<<"$(
    docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
      mysql --default-character-set=utf8mb4 -N -B -u"$DB_USER" -D "$DB_NAME" -e "
        SELECT
          (SELECT COUNT(*) FROM batches WHERE id = $batch_id AND house_id = $house_id AND status = '已完成' AND end_date IS NOT NULL),
          (SELECT COUNT(*) FROM batch_rabbits WHERE batch_id = $batch_id),
          (SELECT COUNT(*) FROM batch_rabbits WHERE batch_id = $batch_id AND is_active = TRUE),
          (SELECT COUNT(*) FROM breeding_cycles WHERE batch_id = $batch_id),
          (SELECT COUNT(*) FROM breeding_cycles WHERE batch_id = $batch_id AND status = '已断奶'),
          (SELECT COUNT(*) FROM breeding_cycles WHERE batch_id = $batch_id AND status = '空怀'),
          (SELECT COUNT(*) FROM breeding_cycles WHERE batch_id = $batch_id AND overlap_litter_cycle_no IS NOT NULL AND postpartum_remating_days IS NOT NULL),
          (SELECT COUNT(*) FROM weaning_records WHERE batch_id = $batch_id),
          (SELECT COUNT(*) FROM parturition_records WHERE batch_id = $batch_id),
          (SELECT COUNT(*) FROM rabbits WHERE birth_batch_id = $batch_id),
          (SELECT COUNT(*) FROM rabbits WHERE birth_batch_id = $batch_id AND mother_id = $mother_a_id AND father_id = $father_id AND birth_cycle_id IS NOT NULL),
          (SELECT COUNT(*) FROM rabbit_departure_records WHERE house_id = $house_id AND departure_type = 'sale' AND rabbit_id IN (SELECT id FROM rabbits WHERE birth_batch_id = $batch_id)),
          (SELECT COUNT(*) FROM sale_order_items soi JOIN sale_orders so ON so.id = soi.sale_order_id WHERE so.house_id = $house_id),
          (SELECT COUNT(*) FROM rabbits WHERE id = $mother_b_id AND house_id = $house_id AND is_active = TRUE),
          (SELECT COUNT(*) FROM rabbits WHERE id = $mother_a_id AND house_id = $house_id AND is_active = FALSE),
          (SELECT COALESCE(SUM(current_nursing_kits), 0) FROM breeding_cycles WHERE batch_id = $batch_id),
          (SELECT COUNT(*) FROM rabbit_departure_records WHERE house_id = $house_id AND rabbit_id = $mother_a_id AND departure_type = 'cull'),
          (SELECT COUNT(*) FROM sale_orders WHERE house_id = $house_id);
      "
  )"
  actual="$completed_batches $batch_members $active_members $cycles $weaned_cycles $empty_cycles $overlap_cycles $weanings $parturitions $born_kits $lineage_kits $sold_kits $sale_items $mother_b_active $mother_a_inactive $nursing_kits $cull_departures $sale_orders"
  if [[ "$actual" == "$expected" ]]; then
    break
  fi
  sleep 0.25
done
{
  printf 'expected=%s\n' "$expected"
  printf 'actual=%s\n' "$actual"
  printf 'batch_id=%s\n' "$batch_id"
  printf 'completed_batches=%s\n' "$completed_batches"
  printf 'batch_members=%s\n' "$batch_members"
  printf 'active_members=%s\n' "$active_members"
  printf 'breeding_cycles=%s\n' "$cycles"
  printf 'weaned_cycles=%s\n' "$weaned_cycles"
  printf 'empty_cycles=%s\n' "$empty_cycles"
  printf 'overlap_cycles=%s\n' "$overlap_cycles"
  printf 'born_kits=%s\n' "$born_kits"
  printf 'sold_kits=%s\n' "$sold_kits"
  printf 'sale_items=%s\n' "$sale_items"
} | tee "$artifact_dir/database_assertions.txt"

if [[ "$actual" != "$expected" ]]; then
  echo "Batch lifecycle database assertions failed" >&2
  exit 1
fi

sale_order_id=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
  mysql -N -B -u"$DB_USER" -D "$DB_NAME" \
  -e "SELECT id FROM sale_orders WHERE house_id = $house_id ORDER BY id DESC LIMIT 1;")
printf 'sale_order=SO-%s\n' "$sale_order_id" | tee "$artifact_dir/result_summary.txt"

echo "Android Batch lifecycle E2E passed"
echo "Fixture run: $run_id"
echo "Batch: $batch_id"
echo "Artifacts: $artifact_dir"
