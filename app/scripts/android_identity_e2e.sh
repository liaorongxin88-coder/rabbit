#!/usr/bin/env bash
set -euo pipefail

# 真机验收：身份与设置链路（账号设置、应用设置、生产设置、数据面板、掉线回登录）。
#
# 这条链路以前只有 widget 测试。真机要证明的是 widget 测试证明不了的三件事：
# 改完密码能用新密码登进来、会话失效会老实回登录页、设了默认启动页重开就落在那页。
#
# 不覆盖（留人工）：短信验证码登录和运营商一键登录。dev 后端 app.sms.enabled=false，
# 发码直接 503；验证码在 valkey 里只存哈希，明文只走真实短信通道，机器拿不到。

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

# 这条链路依赖的接口必须真的在跑着的镜像里，否则用例会退化成「界面看起来没坏」。
for endpoint in /api/settings /api/house-settings; do
  body="$(curl -s "$HOST_API_URL$endpoint" || true)"
  if [[ "$body" == *"No static resource"* ]]; then
    echo "Backend image is stale: $endpoint is not routed." >&2
    echo "Run: docker compose up -d --build --force-recreate backend" >&2
    exit 78
  fi
done

migration_present=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
  mysql -N -B -u"$DB_USER" -D "$DB_NAME" \
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31' AND success = 1;")
if [[ "$migration_present" != "1" ]]; then
  echo "Expected successful Flyway V31 in $DB_NAME" >&2
  exit 65
fi

"$ADB_BIN" start-server >/dev/null
if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi
if [[ -z "$DEVICE_ID" ]] || \
   [[ "$("$ADB_BIN" -s "$DEVICE_ID" get-state 2>/dev/null)" != "device" ]]; then
  echo "A connected Android device is required for the identity E2E" >&2
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

fixture_file="$REPO_DIR/backend/src/test/resources/fixtures/identity_settings_fixture.sql"
if [[ ! -f "$fixture_file" ]]; then
  echo "Identity fixture not found: $fixture_file" >&2
  exit 66
fi
fixture_output=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" -i "$DB_CONTAINER" \
  mysql --default-character-set=utf8mb4 -u"$DB_USER" "$DB_NAME" < "$fixture_file")

run_id=$(awk 'NR == 2 { print $1 }' <<<"$fixture_output")
house_id=$(awk '$1 == "HOUSE" { print $3 }' <<<"$fixture_output")
owner_user=$(awk '$1 == "USER" { print $2 }' <<<"$fixture_output")
owner_id=$(awk '$1 == "USER" { print $3 }' <<<"$fixture_output")
owner_code=$(awk '$1 == "USER" { print $4 }' <<<"$fixture_output")

if [[ -z "$run_id" || -z "$house_id" || -z "$owner_user" || -z "$owner_id" || -z "$owner_code" ]]; then
  echo "Unable to parse identity fixture output" >&2
  printf '%s\n' "$fixture_output" >&2
  exit 65
fi

new_password="rabbit-654321"
renamed_user="$owner_user-renamed"
sale_days=97
weaning_days=41

artifact_dir="$PROJECT_DIR/build/android-e2e/identity-$run_id"
mkdir -p "$artifact_dir"
printf '%s\n' "$fixture_output" > "$artifact_dir/fixture.txt"
printf 'device=%s\napi=%s\nrun_id=%s\nhouse_id=%s\nowner_user=%s\nowner_code=%s\n' \
  "$DEVICE_ID" "$DEVICE_API_URL" "$run_id" "$house_id" "$owner_user" "$owner_code" \
  > "$artifact_dir/environment.txt"

export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_dir"

cd "$PROJECT_DIR"
set +e
"$RABBIT_FLUTTER_BIN" drive \
  --driver=test_driver/android_e2e_driver.dart \
  --target=integration_test/settings/identity_android_test.dart \
  --device-id="$DEVICE_ID" \
  --flavor=dev \
  --dart-define=RABBIT_BUILD_ENV=dev \
  --dart-define=RABBIT_API_BASE_URL="$DEVICE_API_URL" \
  --dart-define=RABBIT_E2E_RUN_ID="$run_id" \
  --dart-define=RABBIT_E2E_PASSWORD=123456 \
  --dart-define=RABBIT_E2E_NEW_PASSWORD="$new_password" \
  --dart-define=RABBIT_E2E_HOUSE_ID="$house_id" \
  --dart-define=RABBIT_E2E_OWNER_USER="$owner_user" \
  --dart-define=RABBIT_E2E_OWNER_CODE="$owner_code" \
  2>&1 | tee "$artifact_dir/flutter-drive.log"
drive_status=${PIPESTATUS[0]}
set -e

if [[ "$drive_status" != "0" ]]; then
  echo "flutter drive failed (status $drive_status); see $artifact_dir/flutter-drive.log" >&2
  exit "$drive_status"
fi

screenshots=(
  01-logged-in
  02-account-settings
  03-user-name-saved
  04-production-default-form
  05-production-default-saved
  06-production-house-saved
  07-dashboard
  08-app-settings
  09-relaunch-restored
  10-password-changed
  11-login-with-new-password
  12-local-settings-cleared
)
for screenshot in "${screenshots[@]}"; do
  if [[ ! -s "$artifact_dir/$screenshot.png" ]]; then
    echo "Missing identity E2E screenshot: $screenshot.png" >&2
    exit 1
  fi
done
printf '%s.png\n' "${screenshots[@]}" > "$artifact_dir/screenshots.txt"

# 界面提示与真正落库是两件事。这里核的是落库：
# 用户名改了、密码换了（旧密码必须登不进、新密码必须登得进）、
# 两级生产设置各写各的、面板数的是在栏兔。
read -r renamed pwd_changed user_settings house_settings active_rabbits \
  <<<"$(
  docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
    mysql -N -B -u"$DB_USER" -D "$DB_NAME" -e "
      SELECT
        (SELECT COUNT(*) FROM sys_user WHERE user_id = $owner_id AND user_name = '$renamed_user'),
        (SELECT COUNT(*) FROM sys_user WHERE user_id = $owner_id
           AND password <> '\$2a\$10\$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6'),
        (SELECT COALESCE(MAX(sale_days), -1) FROM global_setting
           WHERE user_id = $owner_id AND house_id IS NULL),
        (SELECT COALESCE(MAX(weaning_days), -1) FROM global_setting
           WHERE house_id = $house_id),
        (SELECT COUNT(*) FROM rabbits WHERE house_id = $house_id AND is_active = TRUE);
    "
)"

actual="$renamed $pwd_changed $user_settings $house_settings $active_rabbits"
expected="1 1 $sale_days $weaning_days 3"
echo "expected=$expected"
echo "actual=$actual"
if [[ "$actual" != "$expected" ]]; then
  echo "Identity E2E database assertions failed" >&2
  exit 1
fi

# 密码这条最容易自欺欺人：库里 hash 变了不代表新密码真能登录。
# 这里用后端接口实打两次——旧密码必须被拒，新密码必须放行。
old_login_code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$HOST_API_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"userName\":\"$renamed_user\",\"password\":\"123456\"}")
new_login_body=$(curl -s -X POST "$HOST_API_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"userName\":\"$renamed_user\",\"password\":\"$new_password\"}")
if [[ "$new_login_body" != *'"token"'* ]]; then
  echo "New password cannot log in: $new_login_body" >&2
  exit 1
fi
if [[ "$old_login_code" == "200" ]] && \
   [[ "$(curl -s -X POST "$HOST_API_URL/api/auth/login" -H 'Content-Type: application/json' \
        -d "{\"userName\":\"$renamed_user\",\"password\":\"123456\"}")" == *'"token"'* ]]; then
  echo "Old password still works after the change" >&2
  exit 1
fi
echo "Password change verified: old rejected, new accepted"

echo "Identity Android E2E passed"
echo "Fixture run: $run_id"
echo "Artifacts: $artifact_dir"
