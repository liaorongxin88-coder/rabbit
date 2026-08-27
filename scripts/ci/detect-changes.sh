#!/usr/bin/env bash
# 判断本次改动涉及哪些组件，让 CI 跳过与改动无关的质量门禁。
#
# 输入（环境变量）：
#   FILTER_BY_CHANGES  只有为 true 时才做过滤；其余一律全量执行。
#   BASE_SHA           对比基线。PR 用 base.sha，push 用 event.before。
#   HEAD_SHA           对比目标，默认 HEAD。
#
# 输出：写入 $GITHUB_OUTPUT 的 backend / admin / app / lint 四个布尔值，
# 同时打印到日志便于排查。
#
# 设计前提是「宁可多跑，不可漏跑」：基线解析不出来、diff 失败、碰到共享 CI
# 配置，一律回退到全量执行。漏跑一个门禁会让缺陷进主干，多跑一次只是浪费几
# 分钟机器时间，两者代价不对等。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"

cd "$ROOT_DIR"

FILTER_BY_CHANGES="${FILTER_BY_CHANGES:-false}"
BASE_SHA="${BASE_SHA:-}"
HEAD_SHA="${HEAD_SHA:-HEAD}"

backend=false
admin=false
app=false
lint=false

emit() {
  local reason="$1"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "backend=$backend"
      echo "admin=$admin"
      echo "app=$app"
      echo "lint=$lint"
    } >>"$GITHUB_OUTPUT"
  fi
  echo "backend=$backend admin=$admin app=$app lint=$lint"
  echo "判定依据：$reason"
}

run_everything() {
  backend=true
  admin=true
  app=true
  lint=true
  emit "$1"
  exit 0
}

if [[ "$FILTER_BY_CHANGES" != "true" ]]; then
  run_everything "调用方未启用变更过滤"
fi

# 建分支的首次 push 里 event.before 是全零，没有可用基线。
if [[ -z "$BASE_SHA" || "$BASE_SHA" =~ ^0+$ ]]; then
  run_everything "没有可用的对比基线"
fi

if ! git rev-parse --verify --quiet "${BASE_SHA}^{commit}" >/dev/null ||
  ! git rev-parse --verify --quiet "${HEAD_SHA}^{commit}" >/dev/null; then
  run_everything "基线或目标提交不在本地历史中，可能是浅克隆或强推"
fi

# 三点 diff 取的是 merge-base 到 HEAD，也就是本分支自己的改动，不会把基线分支
# 上别人的提交算进来。push 事件里 before 通常就是 merge-base，两者等价。
if ! changed="$(git diff --name-only "$BASE_SHA...$HEAD_SHA" 2>/dev/null)"; then
  run_everything "git diff 执行失败"
fi

if [[ -z "$changed" ]]; then
  emit "没有文件变更"
  exit 0
fi

echo "变更文件："
echo "$changed" | awk '{ print "  " $0 }'
echo

# 第一遍：命中共享配置就直接全量，不必再看其余文件。
while IFS= read -r file; do
  [[ -n "$file" ]] || continue
  case "$file" in
  .github/workflows/* | scripts/ci/* | docker-compose.yml | .env.example)
    run_everything "共享 CI 配置变更：$file"
    ;;
  esac
done <<<"$changed"

while IFS= read -r file; do
  [[ -n "$file" ]] || continue

  # Markdown 只进文档门禁。没有任何构建消费 .md，让它触发后端 E2E 是纯浪费。
  case "$file" in
  *.md)
    lint=true
    continue
    ;;
  esac

  case "$file" in
  backend/*) backend=true ;;
  admin/*) admin=true ;;
  app/*) app=true ;;
  *) lint=true ;;
  esac
done <<<"$changed"

emit "按变更路径归类"
