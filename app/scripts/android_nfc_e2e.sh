#!/usr/bin/env bash
set -euo pipefail

# 模拟器验收：NFC 碰一下。两条路径各一个 mode，共用同一套 fixture 与注入器。
#
# 存在的理由是「不用再借真机」。所以这个脚本**只跑模拟器**，
# 默认拒绝物理设备：如果它悄悄绑到插着的那台手机上，跑绿了也证明不了
# 模拟器这条路通，而借机器恰恰是我们要干掉的瓶颈。
#
#   --mode launch（默认）
#     碰标签 -> MainActivity -> nfcIntent 通道 -> App 跳进该笼位详情。
#     没有采集窗口时的默认处理，覆盖启动/回前台这一支。
#
#   --mode incapture
#     换笼弹层开着、按下「碰一下目标笼位」之后碰标签 -> NfcCagePicker 自己
#     消费事件 -> 目标笼被选中，弹层不被顶掉。
#
# 两个 mode 的注入点都在 native（adb am start 发 debug-only 的 DEBUG_NFC_TAG
# intent），和真卡贴上来时是同一行代码，只少了射频与 NDEF 解析。payload 从
# GET /api/nfc/cages/write-queue 取回**后端真实签名**的那一串，HMAC 客户端伪造
# 不出来，所以校签、兔舍归属、笼位解析全是真的。
#
# incapture 曾经跑不起来，卡点只有一处：采集窗口订阅之前先问
# NfcHardwareService.isAvailable()，而 AVD 没有 android.hardware.nfc 这个
# feature（本脚本会实测并打印），这一问必然 false。用例侧的解法是自己抄一遍
# main.dart 的启动三行并带上 ProviderScope overrides，只把
# nfcHardwareServiceProvider 换掉；lib/ 一行未动。
# 传 RABBIT_ANDROID_E2E_NFC_FORCE_HARDWARE=false 可以把那个 override 摘掉，
# 用来验证这个用例真的会红——不能红的绿是假绿。
#
# 真正覆盖不到的只剩「手机天线读到了卡」这一步。
#
# 沿用 android_cage_ops_e2e.sh 的 fixture 与后端约定，不另起一套。

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

DB_CONTAINER="$(resolve_db_container)"
DB_NAME="${RABBIT_ANDROID_E2E_DB_NAME:-rabbit_app}"
DB_USER="${RABBIT_ANDROID_E2E_DB_USER:-root}"
DB_PASSWORD="${RABBIT_ANDROID_E2E_DB_PASSWORD:-rabbit_root}"
HOST_API_URL="${RABBIT_ANDROID_E2E_HOST_API_URL:-http://127.0.0.1:8080}"
# 模拟器里的 10.0.2.2 就是宿主机，不需要 LAN IP，也不需要 adb reverse。
DEVICE_API_URL="${RABBIT_ANDROID_E2E_DEVICE_API_URL:-http://10.0.2.2:8080}"
ADB_BIN="${RABBIT_ANDROID_E2E_ADB:-}"
DEVICE_ID="${RABBIT_ANDROID_E2E_DEVICE_ID:-}"
AVD_NAME="${RABBIT_ANDROID_E2E_AVD:-Pixel_10_Pro}"
BOOT_AVD="${RABBIT_ANDROID_E2E_BOOT_AVD:-0}"
# 明确的越狱开关：默认 0，也就是「不是模拟器就退出」。
ALLOW_PHYSICAL="${RABBIT_ANDROID_E2E_ALLOW_PHYSICAL_DEVICE:-0}"
APP_ID="${RABBIT_ANDROID_E2E_APP_ID:-com.rabbit.app.flutter.dev}"
APP_ACTIVITY="com.rabbit.app.flutter.MainActivity"
DEBUG_TAG_ACTION="com.rabbit.app.flutter.DEBUG_NFC_TAG"
MODE="${RABBIT_ANDROID_E2E_NFC_MODE:-launch}"
# 采集窗口那条路要求 isAvailable() 说 true；置 false 就是反证跑。
FORCE_HARDWARE="${RABBIT_ANDROID_E2E_NFC_FORCE_HARDWARE:-true}"

while [[ $# -gt 0 ]]; do
  case "$1" in
  --mode)
    MODE="${2:-}"
    shift 2
    ;;
  --mode=*)
    MODE="${1#--mode=}"
    shift
    ;;
  *)
    echo "Unknown argument: $1" >&2
    echo "Usage: $0 [--mode launch|incapture]" >&2
    exit 64
    ;;
  esac
done

# mode 决定四件事：跑哪个用例、等哪一行 logcat、发多密、验哪几张截图。
# 别的（设备选择、fixture、签名 payload、注入器本身）两个 mode 完全一样，
# 所以是一个开关而不是两个脚本——复制一份 400 行的壳只会让它们慢慢跑偏。
case "$MODE" in
launch)
  TEST_TARGET="integration_test/nfc/emulator_tap_android_test.dart"
  READY_MARKER='nfc-e2e. ready-for-injection'
  LANDED_MARKER='nfc-e2e. injected tap landed'
  EXPECTED_SHOTS=(00-cage-grid 01-nfc-jump-to-cage)
  # 启动路径收到重复 intent 无所谓：跳过去还是那一页。补发得密一点更稳。
  INJECT_ATTEMPTS=40
  INJECT_INTERVAL=3
  MODE_SUMMARY='launch/intent path'
  ;;
incapture)
  TEST_TARGET="integration_test/nfc/emulator_incapture_android_test.dart"
  READY_MARKER='nfc-e2e. incapture-ready-for-injection'
  LANDED_MARKER='nfc-e2e. injected tap selected'
  EXPECTED_SHOTS=(00-move-sheet 01-capture-listening 02-nfc-target-selected)
  # 采集窗口收到第一条事件就自己关掉，多发的那一条会被 app.dart 接走跳页，
  # 把换笼弹层顶掉。所以发得稀、等得久，宁可慢也不要多发。
  INJECT_ATTEMPTS=8
  INJECT_INTERVAL=15
  MODE_SUMMARY='in-capture picker path'
  ;;
*)
  echo "Unknown mode: $MODE (expected launch or incapture)" >&2
  exit 64
  ;;
esac

artifact_dir=""
injector_pid=""
logcat_pid=""

cleanup() {
  [[ -n "$injector_pid" ]] && kill "$injector_pid" >/dev/null 2>&1 || true
  [[ -n "$logcat_pid" ]] && kill "$logcat_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT

for command_name in docker curl awk python3; do
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

"$ADB_BIN" start-server >/dev/null

# ---------------------------------------------------------------------------
# 设备选择：显式挑模拟器，挑到哪一台要打印出来。
# ---------------------------------------------------------------------------
if [[ -z "$DEVICE_ID" ]]; then
  DEVICE_ID="$("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1; exit }')"
fi

if [[ -z "$DEVICE_ID" && "$BOOT_AVD" == "1" ]]; then
  EMULATOR_BIN="${RABBIT_ANDROID_E2E_EMULATOR:-$ANDROID_SDK_ROOT/emulator/emulator}"
  if [[ ! -x "$EMULATOR_BIN" ]]; then
    echo "Android emulator binary not found: $EMULATOR_BIN" >&2
    exit 69
  fi
  if ! "$EMULATOR_BIN" -list-avds | grep -qx "$AVD_NAME"; then
    echo "AVD not found: $AVD_NAME (see: $EMULATOR_BIN -list-avds)" >&2
    exit 69
  fi
  echo "Booting AVD $AVD_NAME ..."
  nohup "$EMULATOR_BIN" -avd "$AVD_NAME" -no-snapshot -no-boot-anim -no-audio \
    >"${TMPDIR:-/tmp}/rabbit-nfc-e2e-emulator.log" 2>&1 &
  for _ in $(seq 1 60); do
    DEVICE_ID="$("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1; exit }')"
    [[ -n "$DEVICE_ID" ]] && break
    sleep 10
  done
fi

if [[ -z "$DEVICE_ID" ]]; then
  echo "No running Android EMULATOR found." >&2
  echo "This acceptance run is emulator-only on purpose: binding to a physical" >&2
  echo "handset would not prove the emulator path, which is the bottleneck we" >&2
  echo "are removing." >&2
  echo "Boot one with: RABBIT_ANDROID_E2E_BOOT_AVD=1 $0" >&2
  exit 69
fi

"$ADB_BIN" -s "$DEVICE_ID" wait-for-device
for _ in $(seq 1 60); do
  [[ "$("$ADB_BIN" -s "$DEVICE_ID" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break
  sleep 5
done
if [[ "$("$ADB_BIN" -s "$DEVICE_ID" get-state 2>/dev/null)" != "device" ]]; then
  echo "Emulator $DEVICE_ID never reached a usable state" >&2
  exit 69
fi

# 就算调用方用 RABBIT_ANDROID_E2E_DEVICE_ID 硬指了一台，也要再确认它真是模拟器。
device_characteristics="$("$ADB_BIN" -s "$DEVICE_ID" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r')"
if [[ "$DEVICE_ID" != emulator-* && "$device_characteristics" != *emulator* ]]; then
  if [[ "$ALLOW_PHYSICAL" != "1" ]]; then
    echo "Refusing to run against physical device $DEVICE_ID." >&2
    echo "Set RABBIT_ANDROID_E2E_ALLOW_PHYSICAL_DEVICE=1 only if you really mean it." >&2
    exit 69
  fi
  echo "WARNING: running against PHYSICAL device $DEVICE_ID (explicitly allowed)"
fi
echo "Selected device: $DEVICE_ID (characteristics='${device_characteristics:-none}')"

# ---------------------------------------------------------------------------
# NFC 能力实测：不猜，打出来，并顺便说明本轮的覆盖边界。
# ---------------------------------------------------------------------------
nfc_feature="$("$ADB_BIN" -s "$DEVICE_ID" shell pm list features 2>/dev/null | tr -d '\r' | grep -c 'android.hardware.nfc' || true)"
nfc_service="$("$ADB_BIN" -s "$DEVICE_ID" shell service list 2>/dev/null | tr -d '\r' | grep -ci 'nfc' || true)"
echo "NFC probe: hardware feature entries=$nfc_feature, system service entries=$nfc_service"
if [[ "$nfc_feature" == "0" ]]; then
  echo "  -> No android.hardware.nfc on this image, so NfcManager.isAvailable() is false."
  case "$MODE" in
  launch)
    echo "  -> The launch/intent path never asks isAvailable(), so this run is unaffected."
    ;;
  incapture)
    if [[ "$FORCE_HARDWARE" == "true" ]]; then
      echo "  -> The test overrides nfcHardwareServiceProvider so the picker still subscribes."
      echo "  -> Only that one answer is faked; parsing, signature and cage lookup stay real."
    else
      echo "  -> RABBIT_ANDROID_E2E_NFC_FORCE_HARDWARE=false: the override is OFF."
      echo "  -> This is the negative-control run. The test is EXPECTED to fail here."
    fi
    ;;
  esac
fi
echo "Mode: $MODE ($MODE_SUMMARY) -> $TEST_TARGET"

# ---------------------------------------------------------------------------
# 后端与迁移
# ---------------------------------------------------------------------------
probe="$(curl -s -o /dev/null -w '%{http_code}' "$HOST_API_URL/api/houses" || true)"
if [[ "$probe" != "401" && "$probe" != "200" ]]; then
  echo "Rabbit backend is not reachable at $HOST_API_URL (probe: '${probe:-none}')" >&2
  exit 69
fi

migration_present=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" "$DB_CONTAINER" \
  mysql -N -B -u"$DB_USER" -D "$DB_NAME" \
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '27' AND success = 1;")
if [[ "$migration_present" != "1" ]]; then
  echo "Expected successful Flyway V27 in $DB_NAME" >&2
  exit 65
fi

# 设备侧实打一次：宿主机通不代表设备里通。
#
# 10.0.2.2 是模拟器 NAT 固定映回宿主机的地址，不经过局域网，没有
# 监听地址/防火墙/AP 隔离这些变数，没必要探；而且 AVD 镜像里既没 curl
# 也没 wget，探了只会得到 127。换成自定义地址（比如指到局域网后端）时才值
# 得探，那时候才真的会卡在设备侧。
if [[ "$DEVICE_API_URL" != http://10.0.2.2:* ]]; then
  device_probe="$("$ADB_BIN" -s "$DEVICE_ID" shell "curl -s -m 8 -o /dev/null -w '%{http_code}' $DEVICE_API_URL/api/houses" 2>/dev/null | tr -d '\r' || true)"
  if [[ "$device_probe" != "401" && "$device_probe" != "200" ]]; then
    echo "Device $DEVICE_ID cannot reach the backend at $DEVICE_API_URL (probe: '${device_probe:-none}')" >&2
    echo "Check BACKEND_BIND_ADDRESS=0.0.0.0 in .env." >&2
    exit 69
  fi
  echo "Device reaches backend at $DEVICE_API_URL"
else
  echo "Using emulator loopback $DEVICE_API_URL (no on-device probe: AVD images ship no curl)"
fi

# ---------------------------------------------------------------------------
# fixture：复用笼位验收那套，它已经把标签绑在 1-5-1 上了。
# ---------------------------------------------------------------------------
fixture_file="$REPO_DIR/backend/src/test/resources/fixtures/cage_ops_fixture.sql"
if [[ ! -f "$fixture_file" ]]; then
  echo "Cage-ops fixture not found: $fixture_file" >&2
  exit 66
fi
fixture_output=$(docker exec -e MYSQL_PWD="$DB_PASSWORD" -i "$DB_CONTAINER" \
  mysql --default-character-set=utf8mb4 -u"$DB_USER" "$DB_NAME" <"$fixture_file")

run_id=$(awk 'NR == 2 { print $1 }' <<<"$fixture_output")
house_id=$(awk 'NR == 2 { print $2 }' <<<"$fixture_output")
first_cage_id=$(awk '$1 == "CAGE" && $2 == "1-1-1" { print $3 }' <<<"$fixture_output")
# 采集窗口那条路要搬一只兔：后备兔进空笼、进已占用的非商品兔笼都放行，
# 目标笼位怎么排都能被选中，是最不挑的那一只。
reserve_rabbit_id=$(awk '$2 == "CAGEOPS-RESERVE" { print $3 }' <<<"$fixture_output")
c5_tag_uid=$(awk 'NR == 2 { print $5 }' <<<"$fixture_output")
if [[ -z "$run_id" || -z "$house_id" || -z "$first_cage_id" || -z "$c5_tag_uid" ||
  -z "$reserve_rabbit_id" ]]; then
  echo "Unable to parse cage-ops fixture output" >&2
  printf '%s\n' "$fixture_output" >&2
  exit 65
fi
target_cage_id=$((first_cage_id + 4))

artifact_dir="$PROJECT_DIR/build/android-e2e/nfc-$MODE-$run_id"
mkdir -p "$artifact_dir"
printf '%s\n' "$fixture_output" >"$artifact_dir/fixture.txt"

# ---------------------------------------------------------------------------
# 取回**后端真实签名**的 payload。客户端算不出 HMAC，只能问后端要。
# ---------------------------------------------------------------------------
control_user="cage_ops_fixture_${run_id}_control"
login_body="$(curl -s -X POST "$HOST_API_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"userName\":\"$control_user\",\"password\":\"${RABBIT_ANDROID_E2E_PASSWORD:-123456}\"}" || true)"
token="$(printf '%s' "$login_body" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("token",""))' 2>/dev/null || true)"
if [[ -z "$token" ]]; then
  echo "Unable to log in as $control_user for the write-queue lookup" >&2
  printf '%s\n' "$login_body" >&2
  # 验证码是这里最常见的一趾，而且报文看着像「密码错了」，很容易查偏。
  # 验证码图片的明文后端不返回，脚本无法自己解，只能要求后端关掉。
  # android_cage_ops_e2e.sh 同样依赖这个前提，不是本脚本特有。
  if [[ "$login_body" == *验证码* ]]; then
    echo >&2
    echo "The backend has image captcha enabled, so no script can log in:" >&2
    echo "the plaintext code is never returned by GET /api/auth/captcha." >&2
    echo "Restart the backend with captcha off, e.g." >&2
    echo "  APP_CAPTCHA_ENABLED=false docker compose up -d --force-recreate backend" >&2
  fi
  exit 65
fi

queue_body="$(curl -s "$HOST_API_URL/api/nfc/cages/write-queue" \
  -H "Authorization: Bearer $token" -H "X-House-Id: $house_id" || true)"
printf '%s\n' "$queue_body" >"$artifact_dir/write-queue.json"
read -r signed_payload binding_status <<<"$(
  printf '%s' "$queue_body" | python3 -c '
import json, sys
doc = json.load(sys.stdin)
if doc.get("code") != 0:
    sys.exit("write-queue returned code %r" % doc.get("code"))
target = int(sys.argv[1])
for row in doc.get("data") or []:
    if int(row.get("cageId", -1)) == target:
        print(row.get("payload", ""), row.get("bindingStatus", ""))
        break
else:
    sys.exit("write queue has no cage %d" % target)
' "$target_cage_id"
)"
if [[ -z "$signed_payload" || "$signed_payload" != r1.* ]]; then
  echo "write-queue did not return an r1 payload for cage $target_cage_id" >&2
  exit 65
fi
if [[ "$binding_status" != "BOUND" ]]; then
  echo "Cage $target_cage_id is '$binding_status', not BOUND; a tap would fail with 4603" >&2
  exit 65
fi
echo "Signed payload for cage $target_cage_id acquired from the backend write queue"

device_physical_size="$("$ADB_BIN" -s "$DEVICE_ID" shell wm size |
  awk -F': ' '/Physical size/ { gsub(/\r/, "", $2); print $2; exit }')"
device_density="$("$ADB_BIN" -s "$DEVICE_ID" shell wm density |
  awk -F': ' '/Physical density/ { gsub(/\r/, "", $2); print $2; exit }')"
device_physical_width="${device_physical_size%x*}"
device_physical_height="${device_physical_size#*x}"
if [[ ! "$device_physical_width" =~ ^[0-9]+$ || ! "$device_physical_height" =~ ^[0-9]+$ ||
  ! "$device_density" =~ ^[0-9]+$ ]]; then
  echo "Unable to read physical display metrics from $DEVICE_ID" >&2
  exit 69
fi
device_pixel_ratio="$(awk -v density="$device_density" 'BEGIN { printf "%.6f", density / 160 }')"

printf 'mode=%s\nforce_hardware=%s\ndevice=%s\napi=%s\nrun_id=%s\nhouse_id=%s\nfirst_cage_id=%s\ntarget_cage_id=%s\nreserve_rabbit_id=%s\ntag_uid=%s\nnfc_feature_entries=%s\nphysical_size=%sx%s\npixel_ratio=%s\n' \
  "$MODE" "$FORCE_HARDWARE" "$DEVICE_ID" "$DEVICE_API_URL" "$run_id" "$house_id" \
  "$first_cage_id" "$target_cage_id" "$reserve_rabbit_id" \
  "$c5_tag_uid" "$nfc_feature" "$device_physical_width" "$device_physical_height" \
  "$device_pixel_ratio" >"$artifact_dir/environment.txt"

# ---------------------------------------------------------------------------
# 注入器：用例站定后台再发。发一发容易撞上启动抖动，所以补发到跳转为止。
# ---------------------------------------------------------------------------
"$ADB_BIN" -s "$DEVICE_ID" logcat -c >/dev/null 2>&1 || true
"$ADB_BIN" -s "$DEVICE_ID" logcat >"$artifact_dir/logcat.txt" 2>&1 &
logcat_pid=$!

inject_once() {
  "$ADB_BIN" -s "$DEVICE_ID" shell am start \
    -n "$APP_ID/$APP_ACTIVITY" \
    -a "$DEBUG_TAG_ACTION" \
    --es payload "$signed_payload" \
    --es tagUid "$c5_tag_uid" >/dev/null 2>&1 || true
}

(
  # 等用例自己说「我到位了」，再开始注入。
  # incapture 尤其不能抢跑：采集窗口没开时的标签会被 app.dart 接走跳页，
  # 正在填的换笼弹层当场消失，之后再怎么补发都救不回来。
  for _ in $(seq 1 150); do
    grep -q "$READY_MARKER" "$artifact_dir/logcat.txt" 2>/dev/null && break
    sleep 2
  done
  for _ in $(seq 1 "$INJECT_ATTEMPTS"); do
    grep -q "$LANDED_MARKER" "$artifact_dir/logcat.txt" 2>/dev/null && break
    inject_once
    # 间隔按秒轮询而不是一觉睡到点：用例一打出落地行就停手，
    # 把「已经收工了还多发一条」的窗口从一个间隔压到 1 秒。
    for _ in $(seq 1 "$INJECT_INTERVAL"); do
      grep -q "$LANDED_MARKER" "$artifact_dir/logcat.txt" 2>/dev/null && break
      sleep 1
    done
  done
) &
injector_pid=$!

export RABBIT_ANDROID_E2E_ARTIFACT_DIR="$artifact_dir"

cd "$PROJECT_DIR"
set +e
"$RABBIT_FLUTTER_BIN" drive \
  --driver=test_driver/android_e2e_driver.dart \
  --target="$TEST_TARGET" \
  --device-id="$DEVICE_ID" \
  --flavor=dev \
  --dart-define=RABBIT_BUILD_ENV=dev \
  --dart-define=RABBIT_API_BASE_URL="$DEVICE_API_URL" \
  --dart-define=RABBIT_E2E_RUN_ID="$run_id" \
  --dart-define=RABBIT_E2E_PASSWORD="${RABBIT_ANDROID_E2E_PASSWORD:-123456}" \
  --dart-define=RABBIT_E2E_HOUSE_ID="$house_id" \
  --dart-define=RABBIT_E2E_FIRST_CAGE_ID="$first_cage_id" \
  --dart-define=RABBIT_E2E_RESERVE_RABBIT_ID="$reserve_rabbit_id" \
  --dart-define=RABBIT_E2E_NFC_FORCE_HARDWARE="$FORCE_HARDWARE" \
  --dart-define=RABBIT_E2E_NFC_CAGE_NUMBER=1-5-1 \
  --dart-define=RABBIT_E2E_DEVICE_PHYSICAL_WIDTH="$device_physical_width" \
  --dart-define=RABBIT_E2E_DEVICE_PHYSICAL_HEIGHT="$device_physical_height" \
  --dart-define=RABBIT_E2E_DEVICE_PIXEL_RATIO="$device_pixel_ratio" \
  2>&1 | tee "$artifact_dir/flutter-drive.log"
drive_status=${PIPESTATUS[0]}
set -e

kill "$injector_pid" >/dev/null 2>&1 || true
injector_pid=""

if [[ "$drive_status" != "0" ]]; then
  echo "flutter drive failed (status $drive_status); see $artifact_dir/flutter-drive.log" >&2
  exit "$drive_status"
fi

for screenshot in "${EXPECTED_SHOTS[@]}"; do
  if [[ ! -s "$artifact_dir/$screenshot.png" ]]; then
    echo "Missing NFC E2E screenshot: $screenshot.png" >&2
    exit 1
  fi
done

# 截图能证明画面，证明不了「是被注入的这一脚推过去的」。
# 这一条 logcat 断言把因果钉死：用例自己打的落地行必须在。
if ! grep -q "$LANDED_MARKER" "$artifact_dir/logcat.txt"; then
  echo "logcat has no evidence that the injected tap landed" >&2
  exit 1
fi

kill "$logcat_pid" >/dev/null 2>&1 || true
logcat_pid=""

echo "NFC emulator E2E passed ($MODE_SUMMARY)"
echo "Fixture run: $run_id"
echo "Target cage: $target_cage_id (1-5-1), tag $c5_tag_uid"
echo "Artifacts: $artifact_dir"
echo
case "$MODE" in
launch)
  echo "NOT covered here: in-capture tag selection. Run --mode incapture for that."
  ;;
incapture)
  echo "Covered here: the NfcCagePicker capture window on the cage-transfer sheet."
  echo "The only faked answer is NfcHardwareService.isAvailable(); everything after"
  echo "it (channel event, payload parse, HMAC check, house scoping, cage match,"
  echo "selection state) ran for real. Still NOT covered: the antenna reading a card,"
  echo "and the other three capture entry points (feed, outbound, rabbit intake)."
  ;;
esac
