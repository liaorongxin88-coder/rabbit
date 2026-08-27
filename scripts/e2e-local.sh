#!/usr/bin/env bash
# 本地后端 E2E 一键运行。
#
# 解决的问题：三个 E2E 数据源变量的内置默认值都指向 localhost:3306，
# 而 docker-compose.yml 里的 mysql 有意不暴露 3306（生产安全），
# 文档建议的临时容器又跑在 13307。直接 `mvn -Pe2e verify` 会全量
# 以 "Failed to load ApplicationContext" 失败，而不是提示连不上库。
#
# 用法：
#   bash scripts/e2e-local.sh                         # 跑全量 e2e
#   bash scripts/e2e-local.sh -Dit.test=ReproLifecycleIT
#   E2E_SCHEMA_SUFFIX=_a1 bash scripts/e2e-local.sh    # 多 agent 并行时隔离库名
#   E2E_CLEAN=1 bash scripts/e2e-local.sh              # 先 mvn clean
#   E2E_DROP=1 bash scripts/e2e-local.sh               # 跑完删掉本次的三个库
#
#   # 复现 CI 的第 3 片。库名后缀要一并给，否则两个分片会互相清库。
#   E2E_SCHEMA_SUFFIX=_s3 E2E_SHARD_INDEX=3 E2E_SHARD_TOTAL=4 bash scripts/e2e-local.sh
#
# 什么时候需要 E2E_CLEAN=1：若出现 NoClassDefFoundError（类在源码里确实存在）
# 或 "The forked VM terminated without properly saying goodbye"，那是 target/ 残留
# 的陈旧产物，不是真实缺陷。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"

cd "$ROOT_DIR"

E2E_CONTAINER="${E2E_CONTAINER:-rabbit-e2e}"
E2E_HOST="${E2E_HOST:-127.0.0.1}"
E2E_PORT="${E2E_PORT:-13307}"
E2E_ROOT_PASSWORD="${E2E_ROOT_PASSWORD:-rabbit_root}"
E2E_SCHEMA_SUFFIX="${E2E_SCHEMA_SUFFIX:-}"

MAIN_DB="rabbit_app_e2e${E2E_SCHEMA_SUFFIX}"
MIGRATION_DB="rabbit_app_e2e_migration${E2E_SCHEMA_SUFFIX}"
LARGE_LOOP_DB="rabbit_app_e2e_large_loop${E2E_SCHEMA_SUFFIX}"

# 构建强制 JDK 21（maven-enforcer-plugin 规则 enforce-java-21）。
# 注意：不能只判断 JAVA_HOME 是否为空。开发机常见默认指向更高版本的
# JDK（如 26），那样会直接撞上 enforcer 失败，而且报错和数据库无关，容易误判。
java_major() {
  local home="$1"
  [[ -x "$home/bin/java" ]] || return 1
  "$home/bin/java" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

if [[ -z "${JAVA_HOME:-}" || "$(java_major "${JAVA_HOME:-}" || true)" != "21" ]]; then
  for candidate in \
    "$(/usr/libexec/java_home -v 21 2>/dev/null || true)" \
    /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-21-openjdk; do
    [[ -n "$candidate" ]] || continue
    if [[ "$(java_major "$candidate" || true)" == "21" ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [[ "$(java_major "${JAVA_HOME:-}" || true)" != "21" ]]; then
  echo "找不到 JDK 21。当前 JAVA_HOME=${JAVA_HOME:-<未设置>}" >&2
  echo "macOS/Homebrew 可执行：brew install openjdk@21" >&2
  exit 1
fi

echo "==> JDK 21 位于 $JAVA_HOME"

echo "==> E2E 容器 $E2E_CONTAINER  端口 $E2E_HOST:$E2E_PORT"

# 注意：不要用 `docker ps | grep -q` 判存在。grep -q 命中后立即退出并关闭管道，
# docker 收到 SIGPIPE 返回 141，叠上 set -o pipefail 会把整条管道判为失败，
# 于是「容器存在」被误判成「不存在」。这是个看运气的竞态，改用字符串匹配避开。
container_listed() {
  local name="$1" list
  list="$(docker "${@:2}" --format '{{.Names}}' 2>/dev/null || true)"
  [[ $'\n'"$list"$'\n' == *$'\n'"$name"$'\n'* ]]
}

if ! container_listed "$E2E_CONTAINER" ps; then
  if container_listed "$E2E_CONTAINER" ps -a; then
    echo "==> 启动已存在的容器"
    docker start "$E2E_CONTAINER" >/dev/null
  else
    echo "==> 创建容器（与 docs/project/testing.md 一致）"
    docker run -d --name "$E2E_CONTAINER" \
      -e MYSQL_ROOT_PASSWORD="$E2E_ROOT_PASSWORD" -e TZ=Asia/Shanghai \
      -p "${E2E_PORT}:3306" mysql:8.0 \
      --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
  fi
fi

echo "==> 等待 MySQL 就绪"
for _ in $(seq 1 60); do
  if docker exec "$E2E_CONTAINER" \
    mysqladmin ping -h 127.0.0.1 -uroot -p"$E2E_ROOT_PASSWORD" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! docker exec "$E2E_CONTAINER" \
  mysqladmin ping -h 127.0.0.1 -uroot -p"$E2E_ROOT_PASSWORD" >/dev/null 2>&1; then
  echo "MySQL 在 120 秒内未就绪，容器 $E2E_CONTAINER" >&2
  exit 1
fi

echo "==> 准备测试库：$MAIN_DB / $MIGRATION_DB / $LARGE_LOOP_DB"
for db in "$MAIN_DB" "$MIGRATION_DB" "$LARGE_LOOP_DB"; do
  docker exec "$E2E_CONTAINER" mysql -uroot -p"$E2E_ROOT_PASSWORD" \
    -e "create database if not exists \`$db\` default character set utf8mb4 collate utf8mb4_unicode_ci;" 2>/dev/null
done

QS="useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"

# 三个变量缺一不可。漏配任一个，对应用例会以 Failed to load ApplicationContext
# 失败，报错不会提示是连不上库，极难定位。
export E2E_DATASOURCE_URL="jdbc:mysql://${E2E_HOST}:${E2E_PORT}/${MAIN_DB}?${QS}"
export E2E_MIGRATION_DATASOURCE_URL="jdbc:mysql://${E2E_HOST}:${E2E_PORT}/${MIGRATION_DB}?createDatabaseIfNotExist=true&${QS}"
export E2E_LARGE_LOOP_DATASOURCE_URL="jdbc:mysql://${E2E_HOST}:${E2E_PORT}/${LARGE_LOOP_DB}?${QS}"
export E2E_DATASOURCE_USERNAME="${E2E_DATASOURCE_USERNAME:-root}"
export E2E_DATASOURCE_PASSWORD="${E2E_DATASOURCE_PASSWORD:-$E2E_ROOT_PASSWORD}"

if [[ "${E2E_CLEAN:-}" == "1" ]]; then
  echo "==> mvn clean"
  mvn --batch-mode --no-transfer-progress --file backend/pom.xml clean
fi

# 敲定名字逐个删，不用通配。容器里长期混着各人各次的临时库，
# 一旦用 `drop ... like 'rabbit_app_%'` 清场，很容易把别人正在用的库一并干掉。
drop_run_databases() {
  echo "==> 删除本次测试库：$MAIN_DB / $MIGRATION_DB / $LARGE_LOOP_DB"
  for db in "$MAIN_DB" "$MIGRATION_DB" "$LARGE_LOOP_DB"; do
    docker exec "$E2E_CONTAINER" mysql -uroot -p"$E2E_ROOT_PASSWORD" \
      -e "drop database if exists \`$db\`;" 2>/dev/null
  done
}

if [[ "${E2E_DROP:-}" == "1" ]]; then
  trap drop_run_databases EXIT
fi

# 转给 CI 同一个入口，两边共用选片逻辑。本地默认不起 Valkey，把 cache.it.host
# 置空让两个 Lettuce IT 自己 assumeTrue 跳过；想跑它们就显式给 CACHE_IT_HOST。
echo "==> mvn -Pe2e verify ${*:-}"
export CACHE_IT_HOST="${CACHE_IT_HOST:-}"

# E2E_DROP 靠 EXIT trap 收尾，而 exec 会把本进程换掉，trap 就不会触发了。
if [[ "${E2E_DROP:-}" == "1" ]]; then
  "$ROOT_DIR/scripts/ci/backend-e2e.sh" "$@"
else
  exec "$ROOT_DIR/scripts/ci/backend-e2e.sh" "$@"
fi
