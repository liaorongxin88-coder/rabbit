#!/usr/bin/env bash
# 打印本分片对应的 `-Dit.test=` 参数；未配置分片时什么都不打印。
#
# 输入（环境变量）：
#   E2E_SHARD_INDEX  分片序号，从 1 开始。
#   E2E_SHARD_TOTAL  分片总数。两者都给且总数大于 1 时才分片。
#
# 单独成文件，是为了让 CI 入口（backend-e2e.sh）和本地入口（e2e-local.sh）
# 共用同一份选片逻辑。两边各写一遍迟早会分叉，届时「本地复现 CI 第 3 片」
# 这件事就不成立了。
#
# 分片用 LPT（最长优先）装箱：按耗时降序，每个类放进当前最轻的一片。
# 早先按类名轮转，是在不知道耗时的前提下的折中；实测后发现耗时极不均匀，
# LargeHouseOutboundSubmitScaleIT 一个类就 135 秒，占全量 442 秒的三成，轮转
# 会让关键路径比理论下界多出四成。
#
# 耗时表见 e2e-timings.txt。它只影响均衡，不影响覆盖：下面的 awk 对每个实际
# 存在的 *IT.java 都分配且只分配一次，表里没有的类按 DEFAULT_WEIGHT 计。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd -P)"

cd "$ROOT_DIR"

TIMINGS_FILE="$SCRIPT_DIR/e2e-timings.txt"

# 新增但还没测过的用例按这个权重计。取略高于中位数的值：宁可高估，
# 让它独占一片的一角，也不要低估成 0 而和一堆重活挤在一起。
DEFAULT_WEIGHT=10

E2E_SHARD_INDEX="${E2E_SHARD_INDEX:-}"
E2E_SHARD_TOTAL="${E2E_SHARD_TOTAL:-}"

if [[ -z "$E2E_SHARD_INDEX" || -z "$E2E_SHARD_TOTAL" ]] || ((E2E_SHARD_TOTAL <= 1)); then
  exit 0
fi

if ((E2E_SHARD_INDEX < 1 || E2E_SHARD_INDEX > E2E_SHARD_TOTAL)); then
  echo "分片序号越界：E2E_SHARD_INDEX=${E2E_SHARD_INDEX} E2E_SHARD_TOTAL=${E2E_SHARD_TOTAL}" >&2
  exit 1
fi

all_its=()
while IFS= read -r file; do
  name="${file##*/}"
  all_its+=("${name%.java}")
done < <(find backend -name '*IT.java' -not -path '*/target/*' | sort)

if ((${#all_its[@]} == 0)); then
  echo "没有找到任何 *IT.java，分片配置可能指向了错误的目录" >&2
  exit 1
fi

# 先给每个类配上权重，再按「权重降序、同权重按类名」排出确定的顺序。
# 加类名做次键是为了让同权重的类顺序稳定，否则不同机器上的 sort 可能给出
# 不同分片，本地就复现不了 CI 那一片。
pack_output="$(
  printf '%s\n' "${all_its[@]}" |
    awk -v timings="$TIMINGS_FILE" -v fallback="$DEFAULT_WEIGHT" '
      BEGIN {
        while ((getline line < timings) > 0) {
          sub(/#.*/, "", line)
          if (split(line, f, /[ \t]+/) >= 2 && f[1] != "") {
            weight[f[1]] = f[2] + 0
          }
        }
      }
      { printf "%.3f\t%s\n", ($0 in weight ? weight[$0] : fallback), $0 }
    ' |
    sort -k1,1rn -k2,2 |
    awk -v shard="$E2E_SHARD_INDEX" -v total="$E2E_SHARD_TOTAL" '
      {
        lightest = 1
        for (bin = 2; bin <= total; bin++) {
          if (load[bin] < load[lightest]) {
            lightest = bin
          }
        }
        load[lightest] += $1
        count[lightest]++
        if (lightest == shard) {
          selected = selected (selected == "" ? "" : ",") $2
          mine[++mineCount] = sprintf("%-40s %6.1fs", $2, $1)
        }
      }
      END {
        printf "SELECTED\t%s\n", selected
        printf "SUMMARY\t本片 %d 个用例，预计 %.0f 秒；各片预计 ", count[shard], load[shard]
        for (bin = 1; bin <= total; bin++) {
          printf "%s%.0fs", (bin == 1 ? "" : " / "), load[bin]
        }
        printf "\n"
        for (i = 1; i <= mineCount; i++) {
          printf "DETAIL\t  %s\n", mine[i]
        }
      }
    '
)"

selected="$(printf '%s\n' "$pack_output" | sed -n 's/^SELECTED\t//p')"
summary="$(printf '%s\n' "$pack_output" | sed -n 's/^SUMMARY\t//p')"

# 分片数超过用例数才会出现空片。那是 matrix 配错了，不是正常情况，
# 报错比静默通过更有用：静默通过会让人以为这一片真的跑了。
if [[ -z "$selected" ]]; then
  echo "分片 ${E2E_SHARD_INDEX}/${E2E_SHARD_TOTAL} 没有分到用例，共 ${#all_its[@]} 个用例，分片数过多" >&2
  exit 1
fi

echo "分片 ${E2E_SHARD_INDEX}/${E2E_SHARD_TOTAL}，共 ${#all_its[@]} 个用例。${summary}" >&2
printf '%s\n' "$pack_output" | sed -n 's/^DETAIL\t//p' >&2

echo "-Dit.test=${selected}"
