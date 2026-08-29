import type { OperationEvent } from "@/types/api";

/**
 * 操作事件的展示辅助。
 *
 * 事件流是给人看的审计记录，所以这里只做「让人读得懂」这一件事：
 * 后端给的 operationCode 是 `feed:add` 这种机器名，直接摆到页面上
 * 等于让用户自己翻代码。
 */

const OPERATION_LABELS: Record<string, string> = {
  "repro:state-machine": "繁育流程",
  "feed:add": "投喂",
  "weight:create": "称重",
  "treatment:create": "开始治疗",
  "treatment:complete": "结束治疗",
  "vaccination:create": "接种",
  "sale:create": "销售",
  "batch.create": "新建批次",
  "batch.addMembers": "批次加入",
  "batch.removeMember": "批次移出",
  "batch.sale": "批次出售",
  "batch.completeBatch": "批次完成",
  "batch.rename": "批次改名",
  "rabbit.create": "兔只入栏",
  "rabbit.updateBaseInfo": "修改资料",
  "rabbit.transferCage": "转笼",
  "rabbit.toReplacement": "转后备",
  "rabbit.promoteReplacement": "后备转种",
  "inventory:item:create": "新建物料",
  "inventory:tx": "库存出入",
  "nfc:bind": "绑定标签",
  "cage:nfc:bind": "绑定笼位标签",
};

const TARGET_LABELS: Record<string, string> = {
  RABBIT: "兔只",
  BATCH: "批次",
  CAGE: "笼位",
  INVENTORY_ITEM: "物料",
  SALE_ORDER: "销售单",
  NFC_TAG: "标签",
  OPERATION: "操作",
};

/**
 * 机器名转中文。
 *
 * 认不出来的原样返回而不是显示「未知」：新操作码会随后端上线不断出现，
 * 显示 `feed:add` 至少还能让人搜到，显示「未知操作」就什么线索都没了。
 */
export function operationLabel(event: OperationEvent) {
  const code = event.operationCode?.trim();
  if (!code) {
    return event.eventLabel?.trim() || "操作";
  }
  // 库存出入库这类会带方向后缀，如 inventory:tx:OUT。
  return OPERATION_LABELS[code] ?? OPERATION_LABELS[code.split(":").slice(0, 2).join(":")] ?? code;
}

export function targetLabel(event: OperationEvent) {
  const type = event.targetType?.trim();
  if (!type) {
    return "";
  }
  const name = TARGET_LABELS[type] ?? type;
  return event.targetId === null || event.targetId === undefined
    ? name
    : `${name} #${event.targetId}`;
}

/**
 * 繁育事件才有阶段迁移，其余操作没有，这里返回空串让调用方直接跳过。
 */
export function stageTransition(event: OperationEvent) {
  const from = event.fromStage?.trim();
  const to = event.toStage?.trim();
  if (!from && !to) {
    return "";
  }
  if (!from) {
    return to ?? "";
  }
  if (!to) {
    return from;
  }
  return `${from} → ${to}`;
}

/**
 * 操作人展示名。
 *
 * 后端存的是当时的展示名快照，缺失时不要退回 userId：
 * 一串数字对复盘的人毫无意义，不如老实说不知道。
 */
export function operatorLabel(event: OperationEvent) {
  return event.operatorName?.trim() || "未记录";
}

/**
 * 追加下一页，按 id 去重。
 *
 * 游标分页在并发写入下仍可能把同一行带回来（同一 occurredAt 的边界行），
 * 页面重复渲染同一条审计记录会让人以为操作真的发生了两次。
 */
export function appendEvents(
  current: OperationEvent[],
  incoming: OperationEvent[],
) {
  const seen = new Set(current.map((item) => item.id));
  return [...current, ...incoming.filter((item) => !seen.has(item.id))];
}
