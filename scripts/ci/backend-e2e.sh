#!/usr/bin/env bash
# 运行后端 E2E。可选按分片执行，供 CI 并发。
#
# 输入（环境变量）：
#   CACHE_IT_HOST / CACHE_IT_PORT  Valkey 地址，默认 127.0.0.1:6379。
#   E2E_SHARD_INDEX / E2E_SHARD_TOTAL  见 e2e-shard-args.sh。
#
# 注意：每个分片必须连到自己独立的数据库。E2eTestSupport 在 @BeforeEach 里
# 做 flyway.clean()，两个分片共用一个库会互相清空对方的数据。本脚本只负责
# 选片和调 maven，建库和数据源变量由调用方准备。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"

cd "$ROOT_DIR"

# 不带冒号：区分「没设」和「显式设为空」。e2e-local.sh 会故意传空，让两个
# Lettuce IT 自己 assumeTrue 跳过；用 :- 会把空值当成未设置，反而去连不存在的 Valkey。
CACHE_IT_HOST="${CACHE_IT_HOST-127.0.0.1}"
CACHE_IT_PORT="${CACHE_IT_PORT-6379}"

mvn_args=(
  --batch-mode
  --no-transfer-progress
  --file backend/pom.xml
  -Pe2e
  "-Dcache.it.host=$CACHE_IT_HOST"
  "-Dcache.it.port=$CACHE_IT_PORT"
)

shard_arg="$("$SCRIPT_DIR/e2e-shard-args.sh")"
if [[ -n "$shard_arg" ]]; then
  mvn_args+=("$shard_arg")
fi

mvn "${mvn_args[@]}" "$@" verify
