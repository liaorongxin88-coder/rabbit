#!/usr/bin/env bash
set -euo pipefail

# 真机验收：死亡记录收口、换笼位对调/并笼、笼内逐只管理、录入母兔入轨。
# 对应飞书 recvrpTL16SBwu / recvqh5TC8wd3y / recvsrEA6TRuK6 / recvsrnEJ8bKrk / recvsrpMlvu2SC。
#
# 与 android_batch_lifecycle_e2e.sh 同样走局域网直连后端，不用 adb reverse：
# 后者把流量隧道回 USB，会掩盖真实网络下的延迟与断连。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
REPO_DIR="$(cd "$PROJECT_DIR/.." && pwd -P)"
source "$SCRIPT_DIR/toolchain_env.sh"

resolve_db_container() {
  if [[ -n "${RABBIT_ANDROID_E2E_DB_CONTAINER:-}" ]]; then
    printf '%s\n' "$RABBIT_ANDROID_E2E_DB_CONTAINER"
    return
  fi
  for candidate in rabbit_mysql_1 rabbit-mysql-1; do
    if command -v docker >/dev/null 2>&1 && docker inspect "$candidate" >/dev/null 2>&1; then
      printf '%s\n' "$candidate"
      return
    fi
  done
  printf '%s\n' 'rabbit_mysql_1'
}

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
DB_NAME="${RABBIT_ANDROID_E2E_DB_NAME:-rabbit_app}"
DB_USER="${RABBIT_ANDROID_E2E_DB_USER:-root}"
DB_PASSWORD="${RABBIT_ANDROID_E2E_DB_PASSWORD:-rabbit_root}"
HOST_LAN_IP="$(resolve_host_lan_ip)"
HOST_API_URL="${RABBIT_ANDROID_E2E_HOST_API_URL:-http://127.0.0.1:8080}"
if [[ -n "${RABBIT_ANDROID_E2E_DEVICE_API_URL:-}" ]]; then
  DEVICE_API_URL="$RABBIT_ANDROID_E2E_DEVICE_API_URL"
elif [[ -n "$HOST_LAN_IP" ]]; then
  DEVICE_API_URL="http://$HOST_LAN_IP:8080"
else
  DEVICE_API_URL="http://10.0.2.2:8080"
fi
ADB_BIN="${RABBIT_ANDROID_E2E_ADB:-}"
DEVICE_ID="${RABBIT_ANDROID_E2E_DEVICE_ID:-}"
KEEP_DEVICE_AWAKE="${RABBIT_ANDROID_E2E_KEEP_DEVICE_AWAKE:-1}"

original_stay_on_while_plugged_in=""
artifact_dir=""

cleanup() {
  if [[ -n "$original_stay_on_while_plugged_in" && -n "$DEVICE_ID" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" shell settings put global stay_on_while_plugged_in \
      "$original_stay_on_while_plugged_in" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

for command_name in docker mysql_client_in_container; do :; done
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
ADB_BIN="${ADB_BIN:-$ANDROID_SDK_ROOT/platform-tools/adb}"
if [[ ! -x "$ADB_BIN" ]]; then
  echo "Android adb not found: $ADB_BIN" >&2
  exit 69
fi

if ! curl -fsS -o /dev/null "$HOST_API_URL/api/houses" 2>/dev/null; then
  # 401 也算可达：接口要登录，未登录返回业务错误而不是连接失败。
  probe="$(curl -s -o /dev/null -w '%{http_code}' "$HOST_API_URL/api/houses" || true)"
  if [[ "$probe" != "401" && "$probe" != "200" ]]; then
    echo "Rabbit backend is not reachable at $HOST_API_URL (probe: '${probe:-none}')" >&2
    exit 69
  fi
fi

# 本轮验收的两个端点必须真的在跑着的镜像里。容器不重建时旧镜像会静默少接口，
# 用例会退化成「界面看起来没坏」，那不是验收。
for endpoint in /api/repro/entry-points /api/rabbits/0/cage-transfer; do
  body="$(curl -s -X POST "$HOST_API_URL$endpoint" || true)"
  if [[ "$body" == *"No static resource"* ]]; then
    echo "Backend image is stale: $endpoint is not routed." >&2
    echo "Run: docker compose up -d --build --force-recreate backend" >&2
    exit 78
  fi
done

migration_present=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
  mysql -N -B -u"$DB_USER" -D "$DB_NAME" \
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '27' AND success = 1;")
if [[ "$migration_present" != "1" ]]; then
  echo "Expected successful Flyway V27 in $DB_NAME" >&2
  exit 65
fi

"$ADB_BIN" start-server >/dev/null
if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi
if [[ -z "$DEVICE_ID" ]] || \
   [[ "$("$ADB_BIN" -s "$DEVICE_ID" get-state 2>/dev/null)" != "device" ]]; then
  echo "A connected Android device is required for the cage-ops E2E" >&2
  exit 69
fi

# 从设备侧实打一次：主机通不代表手机通（监听地址、防火墙、AP 隔离只卡设备侧）。
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
fi

fixture_file="$REPO_DIR/backend/src/test/resources/fixtures/cage_ops_fixture.sql"
if [[ ! -f "$fixture_file" ]]; then
  echo "Cage-ops fixture not found: $fixture_file" >&2
  exit 66
fi
fixture_output=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" -i "$DB_CONTAINER" \
  mysql --default-character-set=utf8mb4 -u"$DB_USER" "$DB_NAME" < "$fixture_file")

run_id=$(awk 'NR == 2 { print $1 }' <<<"$fixture_output")
house_id=$(awk 'NR == 2 { print $2 }' <<<"$fixture_output")
first_cage_id=$(awk '$1 == "CAGE" && $2 ~ /R1-C1-L1/ { print $3 }' <<<"$fixture_output")
doe_id=$(awk '$2 == "CAGEOPS-DOE" { print $3 }' <<<"$fixture_output")
reserve_id=$(awk '$2 == "CAGEOPS-RESERVE" { print $3 }' <<<"$fixture_output")
comm_a_id=$(awk '$2 == "CAGEOPS-COMM-A" { print $3 }' <<<"$fixture_output")
comm_b_id=$(awk '$2 == "CAGEOPS-COMM-B" { print $3 }' <<<"$fixture_output")
comm_c_id=$(awk '$2 == "CAGEOPS-COMM-C" { print $3 }' <<<"$fixture_output")

if [[ -z "$run_id" || -z "$house_id" || -z "$first_cage_id" || -z "$doe_id" || \
      -z "$reserve_id" || -z "$comm_a_id" || -z "$comm_b_id" || -z "$comm_c_id" ]]; then
  echo "Unable to parse cage-ops fixture output" >&2
  printf '%s\n' "$fixture_output" >&2
  exit 65
fi

artifact_dir="$PROJECT_DIR/build/android-e2e/cage-ops-$run_id"
mkdir -p "$artifact_dir"
printf '%s\n' "$fixture_output" > "$artifact_dir/fixture.txt"
printf 'device=%s\napi=%s\nrun_id=%s\nhouse_id=%s\nfirst_cage_id=%s\n' \
  "$DEVICE_ID" "$DEVICE_API_URL" "$run_id" "$house_id" "$first_cage_id" \
  > "$artifact_dir/environment.txt"

export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_dir"

cd "$PROJECT_DIR"
set +e
"$RABBIT_FLUTTER_BIN" drive \
  --driver=test_driver/android_e2e_driver.dart \
  --target=integration_test/rabbit_cage_ops_android_test.dart \
  --device-id="$DEVICE_ID" \
  --flavor=dev \
  --dart-define=RABBIT_BUILD_ENV=dev \
  --dart-define=RABBIT_API_BASE_URL="$DEVICE_API_URL" \
  --dart-define=RABBIT_E2E_RUN_ID="$run_id" \
  --dart-define=RABBIT_E2E_PASSWORD=123456 \
  --dart-define=RABBIT_E2E_HOUSE_ID="$house_id" \
  --dart-define=RABBIT_E2E_FIRST_CAGE_ID="$first_cage_id" \
  --dart-define=RABBIT_E2E_DOE_RABBIT_ID="$doe_id" \
  --dart-define=RABBIT_E2E_RESERVE_RABBIT_ID="$reserve_id" \
  --dart-define=RABBIT_E2E_COMM_A_RABBIT_ID="$comm_a_id" \
  --dart-define=RABBIT_E2E_COMM_B_RABBIT_ID="$comm_b_id" \
  --dart-define=RABBIT_E2E_COMM_C_RABBIT_ID="$comm_c_id" \
  2>&1 | tee "$artifact_dir/flutter-drive.log"
drive_status=${PIPESTATUS[0]}
set -e

if [[ "$drive_status" != "0" ]]; then
  echo "flutter drive failed (status $drive_status); see $artifact_dir/flutter-drive.log" >&2
  exit "$drive_status"
fi

screenshots=(
  01-cage-grid
  01b-cage-map
  02-commodity-cage-two-rabbits
  03-departure-sheet
  04-departure-filled
  05-departure-done
  06-doe-cage-with-stage
  07-move-sheet-swap-target
  08-swap-done
  09-move-sheet-append-target
  10-append-done
  11-doe-intake-form
  12-doe-intake-stage-picked
  13-doe-intake-done
)
for screenshot in "${screenshots[@]}"; do
  if [[ ! -s "$artifact_dir/$screenshot.png" ]]; then
    echo "Missing cage-ops E2E screenshot: $screenshot.png" >&2
    exit 1
  fi
done
printf '%s.png\n' "${screenshots[@]}" > "$artifact_dir/screenshots.txt"

# 界面提示与数据库落库是两件事。真正的验收在这里：
# 离场落了 departure 记录、对调后两只兔互换且都还在栏、并笼后计数正确、
# 新母兔带着 current_stage 与一条开放周期和一条待办。
c1=$first_cage_id
c2=$((first_cage_id + 1))
c3=$((first_cage_id + 2))
c4=$((first_cage_id + 3))
c6=$((first_cage_id + 5))

read -r departed departure_rows comm_b_active doe_cage reserve_cage active_swapped \
        c1_state c2_state comm_c_cage c3_state c4_state new_doe_stage open_cycles pending_tasks \
  <<<"$(
  docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
    mysql -N -B -u"$DB_USER" -D "$DB_NAME" -e "
      SELECT
        (SELECT COUNT(*) FROM rabbits WHERE id = $comm_a_id AND is_active = FALSE
           AND departure_date IS NOT NULL AND departure_reason IS NOT NULL),
        (SELECT COUNT(*) FROM rabbit_departure_records WHERE rabbit_id = $comm_a_id),
        (SELECT COUNT(*) FROM rabbits WHERE id = $comm_b_id AND is_active = TRUE AND cage_id = $c3),
        (SELECT cage_id FROM rabbits WHERE id = $doe_id),
        (SELECT cage_id FROM rabbits WHERE id = $reserve_id),
        (SELECT COUNT(*) FROM rabbits WHERE id IN ($doe_id, $reserve_id) AND is_active = TRUE
           AND departure_date IS NULL),
        (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = $c1),
        (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = $c2),
        (SELECT cage_id FROM rabbits WHERE id = $comm_c_id),
        (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = $c3),
        (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = $c4),
        (SELECT COALESCE(MAX(current_stage), 'none') FROM rabbits
           WHERE house_id = $house_id AND cage_id = $c6 AND is_active = TRUE),
        (SELECT COUNT(*) FROM breeding_cycles bc
           INNER JOIN rabbits r ON r.id = bc.mother_rabbit_id
           WHERE r.house_id = $house_id AND r.cage_id = $c6 AND bc.closed_at IS NULL),
        (SELECT COUNT(*) FROM work_tasks wt
           INNER JOIN rabbits r ON r.id = wt.rabbit_id
           WHERE r.house_id = $house_id AND r.cage_id = $c6 AND wt.status = 'PENDING');
    "
)"

actual="$departed $departure_rows $comm_b_active $doe_cage $reserve_cage $active_swapped $c1_state $c2_state $comm_c_cage $c3_state $c4_state $new_doe_stage $open_cycles $pending_tasks"
# 对调后：种母兔在原后备笼($c2)、后备兔在原种兔笼($c1)，两笼各 1 只且用途互换
# （$c1 变后备兔笼 '2'，$c2 变种兔笼 '1'）。并笼后 $c3 恢复 2 只、$c4 空且状态归 '0'。
expected="1 1 1 $c2 $c1 2 2:1 1:1 $c3 3:2 0:0 AWAIT_PALPATION 1 1"
printf 'expected=%s\nactual=%s\n' "$expected" "$actual" | tee "$artifact_dir/database_assertions.txt"
if [[ "$actual" != "$expected" ]]; then
  echo "Cage-ops E2E database assertions failed" >&2
  exit 1
fi

echo "Cage-ops Android E2E passed"
echo "Fixture run: $run_id"
echo "Artifacts: $artifact_dir"
