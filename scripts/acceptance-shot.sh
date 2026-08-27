#!/usr/bin/env bash
# 验收截图采集：按业务域顺序编号保存，并同步写入清单。
#
#   scripts/acceptance-shot.sh <业务域> <说明>
#   scripts/acceptance-shot.sh 05-breeding "配种表单已填"
#
# 产物：
#   artifacts/acceptance-<日期>/05-breeding/05-03-配种表单已填.png
#   artifacts/acceptance-<日期>/MANIFEST.md      同步追加一行
#
# 环境变量：
#   RABBIT_DEVICE   指定设备序列号；不指定则在恰好一台设备时自动选取
#   ACCEPTANCE_DIR  覆盖产物目录（默认 artifacts/acceptance-<日期>）
#
# 为什么不用 adb shell screencap 再 pull：那样会在中间落一个设备侧临时文件，
# 且部分 ROM 会把 stdout 的 LF 转成 CRLF 把 PNG 弄坏。exec-out 是二进制直通。

set -euo pipefail

die() {
  printf '错误：%s\n' "$*" >&2
  exit 1
}

[ $# -ge 2 ] || die "用法：$0 <业务域> <说明>
业务域取值见 docs/project/acceptance-runbook.md，例如 05-breeding"

SCOPE="$1"
shift
DESC="$*"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATE_TAG="$(date +%Y%m%d)"
OUT_ROOT="${ACCEPTANCE_DIR:-$REPO_ROOT/artifacts/acceptance-$DATE_TAG}"
SCOPE_DIR="$OUT_ROOT/$SCOPE"
MANIFEST="$OUT_ROOT/MANIFEST.md"

# --- 选设备 ---------------------------------------------------------------
pick_device() {
  if [ -n "${RABBIT_DEVICE:-}" ]; then
    printf '%s' "$RABBIT_DEVICE"
    return
  fi
  local list count
  list="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
  count="$(printf '%s\n' "$list" | grep -c . || true)"
  [ "$count" -eq 1 ] || die "检测到 $count 台设备，请用 RABBIT_DEVICE 指定序列号：
$(printf '%s\n' "$list" | sed 's/^/  /')"
  printf '%s' "$list"
}
DEVICE="$(pick_device)"

# --- 序号：按业务域内已有文件数递增 ---------------------------------------
mkdir -p "$SCOPE_DIR"
NEXT="$(find "$SCOPE_DIR" -maxdepth 1 -name '*.png' | wc -l | tr -d ' ')"
NEXT=$((NEXT + 1))
SEQ="$(printf '%02d' "$NEXT")"

# 说明里的空格和斜杠会让文件名难处理，统一换成连字符。
SAFE_DESC="$(printf '%s' "$DESC" | tr ' /' '--' | tr -d '"'"'"'`$')"
FILE="$SCOPE_DIR/$SCOPE-$SEQ-$SAFE_DESC.png"

# --- 采集 -----------------------------------------------------------------
# 重定向会先把文件创建出来，所以任何失败路径都必须把它删掉——否则一个 0 字节的
# PNG 会留在业务域目录里，看起来像一张成功的证据截图。
cleanup_bad_shot() { rm -f "$FILE"; }
trap cleanup_bad_shot EXIT

adb -s "$DEVICE" exec-out screencap -p >"$FILE" 2>/dev/null || die "截图失败，设备 $DEVICE 是否在线并已解锁？"
[ -s "$FILE" ] || die "截图为空，设备 $DEVICE 可能锁屏或断开"
head -c 8 "$FILE" | grep -q $'\x89PNG' || die "采集到的不是 PNG，设备 $DEVICE 的 screencap 可能被 ROM 改过"

# 走到这里说明产物是好的，撤掉清理钩子。
trap - EXIT

SIZE="$(du -h "$FILE" | cut -f1 | tr -d ' ')"

# --- 写清单 ---------------------------------------------------------------
if [ ! -f "$MANIFEST" ]; then
  {
    printf '# 验收截图清单 %s\n\n' "$DATE_TAG"
    printf '设备：`%s`　仓库：`%s`\n\n' "$DEVICE" "$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
    printf '| 时间 | 业务域 | 序号 | 说明 | 文件 |\n|---|---|---|---|---|\n'
  } >"$MANIFEST"
fi
printf '| %s | %s | %s | %s | `%s` |\n' \
  "$(date +%H:%M:%S)" "$SCOPE" "$SEQ" "$DESC" "${FILE#"$OUT_ROOT/"}" >>"$MANIFEST"

printf '✓ %s/%s-%s  %s  (%s)\n' "$SCOPE" "$SCOPE" "$SEQ" "$DESC" "$SIZE"
