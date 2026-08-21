#!/usr/bin/env bash
set -euo pipefail

# 真机验收：新场开张的完整链路。
# 建兔舍 → 批量建笼 → 录入兔只 → 兔只总表行内编辑/换笼/单兔出库入口
# → 按兔号邀请同事 → 同事登录确认权限真的生效。
#
# 别的本子都从「fixture 已经建好兔舍笼位兔只」开始，等于把开张这段挡在自动化外面；
# 而这段每个新客户必然走一次，出问题的代价最大。所以这里的 fixture 只给两个空账号。
#
# 与其它真机脚本一致：局域网直连后端，不用 adb reverse——后者把流量隧道回 USB，
# 会掩盖真实网络下的延迟与断连。

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
original_accelerometer_rotation=""
artifact_dir=""

cleanup() {
  if [[ -n "$original_stay_on_while_plugged_in" && -n "$DEVICE_ID" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" shell settings put global stay_on_while_plugged_in \
      "$original_stay_on_while_plugged_in" >/dev/null 2>&1 || true
  fi
  if [[ -n "$original_accelerometer_rotation" && -n "$DEVICE_ID" ]]; then
    "$ADB_BIN" -s "$DEVICE_ID" shell settings put system accelerometer_rotation \
      "$original_accelerometer_rotation" >/dev/null 2>&1 || true
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
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32' AND success = 1;")
if [[ "$migration_present" != "1" ]]; then
  echo "Expected successful Flyway V32 in $DB_NAME" >&2
  exit 65
fi

"$ADB_BIN" start-server >/dev/null
if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi
if [[ -z "$DEVICE_ID" ]] || \
   [[ "$("$ADB_BIN" -s "$DEVICE_ID" get-state 2>/dev/null)" != "device" ]]; then
  echo "A connected Android device is required for the farm-setup E2E" >&2
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

# 锁竖屏。手机平放在桌上被自动旋转转成横屏后，逻辑视口只剩 ~360px 高，
# ListView 里靠下的卡片压根不会被构建，用例会以一连串「Found 0 widgets」
# 报错——看起来像界面坏了，其实只是手机躺歪了。截图证据在横屏下也没法看。
original_accelerometer_rotation="$(
  "$ADB_BIN" -s "$DEVICE_ID" shell settings get system accelerometer_rotation | tr -d '\r'
)"
"$ADB_BIN" -s "$DEVICE_ID" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
"$ADB_BIN" -s "$DEVICE_ID" shell settings put system user_rotation 0 >/dev/null 2>&1 || true

fixture_file="$REPO_DIR/backend/src/test/resources/fixtures/farm_setup_fixture.sql"
if [[ ! -f "$fixture_file" ]]; then
  echo "Farm-setup fixture not found: $fixture_file" >&2
  exit 66
fi
fixture_output=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" -i "$DB_CONTAINER" \
  mysql --default-character-set=utf8mb4 -u"$DB_USER" "$DB_NAME" < "$fixture_file")

run_id=$(awk 'NR == 2 { print $1 }' <<<"$fixture_output")
founder_user=$(awk '$2 == "founder" { print $4 }' <<<"$fixture_output")
mate_user=$(awk '$2 == "mate" { print $4 }' <<<"$fixture_output")
mate_code=$(awk '$2 == "mate" { print $5 }' <<<"$fixture_output")

if [[ -z "$run_id" || -z "$founder_user" || -z "$mate_user" || -z "$mate_code" ]]; then
  echo "Unable to parse farm-setup fixture output" >&2
  printf '%s\n' "$fixture_output" >&2
  exit 65
fi

artifact_dir="$PROJECT_DIR/build/android-e2e/farm-setup-$run_id"
mkdir -p "$artifact_dir"
printf '%s\n' "$fixture_output" > "$artifact_dir/fixture.txt"
printf 'device=%s\napi=%s\nrun_id=%s\nfounder=%s\nmate=%s\nmate_code=%s\n' \
  "$DEVICE_ID" "$DEVICE_API_URL" "$run_id" "$founder_user" "$mate_user" "$mate_code" \
  > "$artifact_dir/environment.txt"

export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_dir"

cd "$PROJECT_DIR"
set +e
"$RABBIT_FLUTTER_BIN" drive \
  --driver=test_driver/android_e2e_driver.dart \
  --target=integration_test/houses/farm_setup_android_test.dart \
  --device-id="$DEVICE_ID" \
  --flavor=dev \
  --dart-define=RABBIT_BUILD_ENV=dev \
  --dart-define=RABBIT_API_BASE_URL="$DEVICE_API_URL" \
  --dart-define=RABBIT_E2E_RUN_ID="$run_id" \
  --dart-define=RABBIT_E2E_PASSWORD=123456 \
  --dart-define=RABBIT_E2E_FOUNDER_USER="$founder_user" \
  --dart-define=RABBIT_E2E_MATE_USER="$mate_user" \
  --dart-define=RABBIT_E2E_MATE_CODE="$mate_code" \
  2>&1 | tee "$artifact_dir/flutter-drive.log"
drive_status=${PIPESTATUS[0]}
set -e

if [[ "$drive_status" != "0" ]]; then
  echo "flutter drive failed (status $drive_status); see $artifact_dir/flutter-drive.log" >&2
  exit "$drive_status"
fi

screenshots=(
  00-production-template
  01-empty-houses
  02-house-created
  02b-house-production-snapshot
  03-cages-auto
  04-cages-created
  05-first-rabbit
  06-rabbit-roster
  07-rabbit-edited
  08-rabbit-moved
  09-single-outbound-entry
  10-member-invited
  11-mate-sees-house
  12-mate-readonly
)
for screenshot in "${screenshots[@]}"; do
  if [[ ! -s "$artifact_dir/$screenshot.png" ]]; then
    echo "Missing farm-setup E2E screenshot: $screenshot.png" >&2
    exit 1
  fi
done
printf '%s.png\n' "${screenshots[@]}" > "$artifact_dir/screenshots.txt"

# 界面说「建好了」和数据库里真有这些行，是两件事。
# 这里逐条核对开张链路的落库结果：兔舍布局、笼位数量、兔只归属与改名、
# 成员角色，以及邀请记录确实走的是兔号通道。
read -r house_layout cage_count rabbit_breed rabbit_cage_rank mate_role invite_channel invite_status house_sale_days user_sale_days \
  <<<"$(
  docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
    mysql -N -B -u"$DB_USER" -D "$DB_NAME" -e "
      SET @house := (SELECT id FROM rabbit_houses WHERE name = 'H-SETUP-$run_id');
      SET @mate := (SELECT user_id FROM sys_user WHERE user_name = '$mate_user');
      SELECT
        (SELECT CONCAT(layout_rows, 'x', layout_cols, 'x', layout_layers)
           FROM rabbit_houses WHERE id = @house),
        (SELECT COUNT(*) FROM cages WHERE house_id = @house),
        (SELECT COALESCE(MAX(breed), 'none') FROM rabbits WHERE house_id = @house AND is_active = TRUE),
        -- 兔子换过笼：它现在应该在本舍第 2 个笼位（按 id 排序）
        (SELECT COUNT(*) FROM rabbits r
           WHERE r.house_id = @house AND r.is_active = TRUE
             AND r.cage_id = (SELECT MIN(id) + 1 FROM cages WHERE house_id = @house)),
        (SELECT COALESCE(MAX(role), 'none') FROM house_users
           WHERE house_id = @house AND user_id = @mate AND status = 'ENABLED'),
        (SELECT COALESCE(MAX(invite_channel), 'none') FROM house_invitations
           WHERE house_id = @house AND invited_user_id = @mate),
        (SELECT COALESCE(MAX(status), 'none') FROM house_invitations
           WHERE house_id = @house AND invited_user_id = @mate),
        (SELECT COALESCE(MAX(sale_days), -1) FROM global_setting WHERE house_id = @house),
        (SELECT COALESCE(MAX(sale_days), -1) FROM global_setting
           WHERE user_id = (SELECT user_id FROM sys_user WHERE user_name = '$founder_user'));
    "
)"

actual="$house_layout $cage_count $rabbit_breed $rabbit_cage_rank $mate_role $invite_channel $invite_status $house_sale_days $user_sale_days"
# 1 排 3 列 2 层的兔舍：建舍时自动铺 6 个，再手工补一排 6 个，共 12 个；
# 兔只改名成 SETUP-RENAMED 并换到了第 2 个笼位；同事以 VIEWER 入伙，
# 邀请记录来自 USER_CODE 通道且当场 ACCEPTED。最后两列证明建场时把用户模板
# 的出售周期 91 天复制成了兔场独立配置，而不是仅在页面上临时回退显示。
expected="1x3x2 12 SETUP-RENAMED 1 VIEWER USER_CODE ACCEPTED 91 91"
printf 'expected=%s\nactual=%s\n' "$expected" "$actual" | tee "$artifact_dir/database_assertions.txt"
if [[ "$actual" != "$expected" ]]; then
  echo "Farm-setup E2E database assertions failed" >&2
  exit 1
fi

echo "Farm-setup Android E2E passed"
echo "Fixture run: $run_id"
echo "Artifacts: $artifact_dir"
