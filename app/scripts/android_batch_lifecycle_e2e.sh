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

# 主机局域网 IP。真机是通过局域网直连后端的，不走 adb reverse：
# 后者会把流量隧道回 USB，掩盖掉真实网络下的延迟与断连行为。
resolve_host_lan_ip() {
  if [[ -n "${RABBIT_ANDROID_E2E_HOST_LAN_IP:-}" ]]; then
    printf '%s\n' "$RABBIT_ANDROID_E2E_HOST_LAN_IP"
    return
  fi
  local ip=""
  for iface in en0 en1 en2; do
    ip="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
    [[ -n "$ip" ]] && break
  done
  printf '%s\n' "$ip"
}

DB_CONTAINER="$(resolve_db_container)"
CACHE_CONTAINER="${RABBIT_ANDROID_E2E_CACHE_CONTAINER:-rabbit_valkey_1}"
BACKEND_CONTAINER="${RABBIT_ANDROID_E2E_BACKEND_CONTAINER:-rabbit_backend_1}"
DB_NAME="${RABBIT_ANDROID_E2E_DB_NAME:-rabbit_app}"
DB_USER="${RABBIT_ANDROID_E2E_DB_USER:-root}"
DB_PASSWORD="${RABBIT_ANDROID_E2E_DB_PASSWORD:-rabbit_root}"
HOST_LAN_IP="$(resolve_host_lan_ip)"
HOST_API_URL="${RABBIT_ANDROID_E2E_HOST_API_URL:-http://127.0.0.1:8080}"
# 默认走局域网；只有模拟器才回退到 10.0.2.2。
if [[ -n "${RABBIT_ANDROID_E2E_DEVICE_API_URL:-}" ]]; then
  DEVICE_API_URL="$RABBIT_ANDROID_E2E_DEVICE_API_URL"
elif [[ -n "$HOST_LAN_IP" ]]; then
  DEVICE_API_URL="http://$HOST_LAN_IP:8080"
else
  DEVICE_API_URL="http://10.0.2.2:8080"
fi
TEXT_SCALE="${RABBIT_ANDROID_E2E_TEXT_SCALE:-1.0}"
EXPECTED_EFFECTIVE_TEXT_SCALE="${RABBIT_ANDROID_E2E_EXPECTED_EFFECTIVE_TEXT_SCALE:-}"
ADB_BIN="${RABBIT_ANDROID_E2E_ADB:-}"
DEVICE_ID="${RABBIT_ANDROID_E2E_DEVICE_ID:-}"
ALLOW_DEVICE_SETTINGS="${RABBIT_ANDROID_E2E_ALLOW_DEVICE_SETTINGS:-0}"
KEEP_DEVICE_AWAKE="${RABBIT_ANDROID_E2E_KEEP_DEVICE_AWAKE:-1}"

original_font_scale=""
original_stay_on_while_plugged_in=""
original_accelerometer_rotation=""
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

# 必须跑在完整 compose 集群上（mysql + valkey + backend 容器），而不是临时拼的环境。
# 本地跑 mvn spring-boot:run 也能让用例通过，但那就测不到容器网络、镜像构建
# 与缓存接线这三件真实部署才有的事。
for container in "$DB_CONTAINER" "$CACHE_CONTAINER" "$BACKEND_CONTAINER"; do
  if ! docker inspect "$container" >/dev/null 2>&1; then
    echo "Missing compose container: $container" >&2
    echo "Start the full cluster first: docker compose --profile valkey up -d --build" >&2
    exit 69
  fi
done

# 缓存必须真的是 valkey 且连得上。仅看环境变量不够：配错主机名时后端照样启动，
# 只在真正用到缓存的路径上才报错（短信验证码）。
cache_provider=$(docker exec "$BACKEND_CONTAINER" printenv APP_CACHE_PROVIDER 2>/dev/null || true)
if [[ "$cache_provider" != "valkey" ]]; then
  echo "Backend cache provider must be valkey for this run (got: '${cache_provider:-unset}')" >&2
  exit 78
fi
if ! docker exec "$CACHE_CONTAINER" valkey-cli ping 2>/dev/null | grep -q PONG; then
  echo "Valkey is not responding in container $CACHE_CONTAINER" >&2
  exit 69
fi

# 真机必须能从局域网直接访问后端。这一步失败通常是 BACKEND_BIND_ADDRESS 仍为
# 127.0.0.1（只监听回环），或手机不在同一局域网。
if [[ "$DEVICE_API_URL" == http://10.0.2.2:* ]]; then
  echo "Warning: falling back to emulator loopback; no host LAN IP detected" >&2
fi


# V32 includes the user/house reminder preferences exercised by this scenario.
# Checking only the older doe-breeding-v2 migration lets a stale backend image
# reach the settings page and then fail as a misleading missing-widget timeout.
migration_present=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
  mysql -N -B -u"$DB_USER" -D "$DB_NAME" \
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32' AND success = 1;")
if [[ "$migration_present" != "1" ]]; then
  echo "Expected successful Flyway V32 in $DB_NAME; rebuild and recreate the backend container" >&2
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

# 从设备侧实打一次：主机自测通过不代表手机能访问（监听地址、防火墙、
# AP 隔离都会只卡设备这一侧）。失败时提前报错，而不是让用例在登录页超时。
if [[ "$DEVICE_API_URL" != http://10.0.2.2:* ]]; then
  device_probe="$("$ADB_BIN" -s "$DEVICE_ID" shell "curl -s -m 8 -o /dev/null -w '%{http_code}' $DEVICE_API_URL/api/houses" 2>/dev/null | tr -d '\r')"
  if [[ "$device_probe" != "401" && "$device_probe" != "200" ]]; then
    echo "Device $DEVICE_ID cannot reach the backend at $DEVICE_API_URL (probe: '${device_probe:-none}')" >&2
    echo "Check BACKEND_BIND_ADDRESS=0.0.0.0 in .env and that the phone is on the same LAN." >&2
    exit 69
  fi
  echo "Device reaches backend over LAN at $DEVICE_API_URL"
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
cache_keys_before=$(docker exec "$CACHE_CONTAINER" valkey-cli dbsize 2>/dev/null | tr -d '\r')
printf 'device=%s\nprofile=batch-lifecycle\ntime_mode=compressed-work-task-due\ntransport=lan\ndevice_api_url=%s\ncache_provider=valkey\ncache_container=%s\ncache_keys_before=%s\nsystem_text_scale=%s\neffective_text_scale=%s\nphysical_size=%s\nphysical_density=%s\nrun_id=%s\nprimary_house_id=%s\nmother_a_id=%s\nmother_b_id=%s\nfather_id=%s\n' \
  "$DEVICE_ID" "$DEVICE_API_URL" "$CACHE_CONTAINER" "${cache_keys_before:-0}" \
  "$TEXT_SCALE" "$EXPECTED_EFFECTIVE_TEXT_SCALE" "$physical_size" "$physical_density" \
  "$run_id" "$house_id" "$mother_a_id" "$mother_b_id" "$father_id" \
  > "$artifact_dir/environment.txt"

export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_dir"

# Production reminders are date-gated. Compress only this isolated fixture's
# pending reminder dates so a 30+ day lifecycle remains visible in one run.
#
# work_tasks is the only reminder source after doe-breeding-v2: V28 dropped
# breeding_cycles.next_event_*, and batch_rabbits' copy now only feeds the
# replacement/sale reminders that have not moved into the task centre yet.
(
  while true; do
    docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
      mysql --default-character-set=utf8mb4 -N -B -u"$DB_USER" -D "$DB_NAME" -e "
        UPDATE work_tasks wt
        JOIN batches b ON b.id = wt.batch_id
        SET wt.due_date = CURDATE(), wt.due_time = NOW()
        WHERE b.house_id = $house_id
          AND b.batch_code = 'B-LIFECYCLE-$run_id'
          AND wt.status = 'PENDING'
          AND wt.due_date > CURDATE();
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
  --target=integration_test/batches/lifecycle_android_test.dart \
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
  02b-reminders-disabled
  02c-reminders-filtered
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
  15-abortion-mother-b
  16-outbound-selection
  17-outbound-confirmation
  18-outbound-success
  19-batch-completed
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

# doe-breeding-v2 下的预期值。与旧模型的差异都是设计使然，不是回归：
#
#   cycles 3 -> 6      空怀/流产/断奶后都会自动接续下一轮，而不是把母兔丢出流程。
#                      构成（可逐条复核，不是魔数）：
#                        母兔 A：#1 配种→产仔→断奶；#2 血配→产仔→断奶；
#                                #3 断奶后自动接续的待催情周期，淘汰时置为 REMOVED
#                        母兔 B：#1 配种→空怀；#2 重开后再配→待摸胎时流产；
#                                #3 流产后自动接续的待催情周期，淘汰时置为 REMOVED
#   aborted_cycles 1   流产是非计划事件，无待办可走，只能从母兔行的独立入口提交。
#                      单看 cycles 变化不足以证明流产真的落库，所以单独计一列。
#   阶段计数改用 lifecycle/result   而不再看中文 status 镜像列（V28 将删除）。
#   overlap_cycles 改义              从「写过 overlap_* 列的周期数」改为「本批次里拥有多轮
#                                    周期的母兔数」＝2（A 三轮、B 两轮）。
#   parturitions 2 -> 0              产仔已由 repro_events 记录，不再写遗留记录表。
#   mother_b_active 1 -> 0           母兔 B 空怀后仍在生产中，需显式离场批次才能结束。
#
# lineage_kits 仍为 7：谱系（父/母/出生周期）是硬要求，不得因重构而丢失。
expected="1 9 0 6 2 1 2 2 0 7 7 7 7 0 1 0 1 1 1"
actual=""
for _ in {1..40}; do
  read -r completed_batches batch_members active_members cycles weaned_cycles empty_cycles overlap_cycles weanings parturitions born_kits lineage_kits sold_kits sale_items mother_b_active mother_a_inactive nursing_kits cull_departures sale_orders aborted_cycles <<<"$(
    docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
      mysql --default-character-set=utf8mb4 -N -B -u"$DB_USER" -D "$DB_NAME" -e "
        SELECT
          (SELECT COUNT(*) FROM batches WHERE id = $batch_id AND house_id = $house_id AND status = '已完成' AND end_date IS NOT NULL),
          (SELECT COUNT(*) FROM batch_rabbits WHERE batch_id = $batch_id),
          (SELECT COUNT(*) FROM batch_rabbits WHERE batch_id = $batch_id AND is_active = TRUE),
          (SELECT COUNT(*) FROM breeding_cycles WHERE batch_id = $batch_id),
          (SELECT COUNT(*) FROM breeding_cycles WHERE batch_id = $batch_id AND result = 'WEANED'),
          (SELECT COUNT(*) FROM breeding_cycles WHERE batch_id = $batch_id AND result = 'EMPTY'),
          -- 血配的新证据：同一头母兔在本批次里拥有多条周期。
          -- 旧的 overlap_* 列已随 V28 删除（只写不读的死数据）；新模型里血配就是
          -- 「哺乳周期与新怀孕周期并存」，落到最终数据上即母兔持有多轮周期。
          (SELECT COUNT(*) FROM (
              SELECT mother_rabbit_id FROM breeding_cycles WHERE batch_id = $batch_id
               GROUP BY mother_rabbit_id HAVING COUNT(*) > 1
          ) repeat_breeders),
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
          (SELECT COUNT(*) FROM sale_orders WHERE house_id = $house_id),
          -- 流产落库的直接证据：周期以 ABORTED 结束。
          (SELECT COUNT(*) FROM breeding_cycles WHERE batch_id = $batch_id AND result = 'ABORTED');
      "
  )"
  actual="$completed_batches $batch_members $active_members $cycles $weaned_cycles $empty_cycles $overlap_cycles $weanings $parturitions $born_kits $lineage_kits $sold_kits $sale_items $mother_b_active $mother_a_inactive $nursing_kits $cull_departures $sale_orders $aborted_cycles"
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
