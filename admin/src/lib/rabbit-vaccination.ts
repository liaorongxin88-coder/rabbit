import type { VaccinationRecord } from "@/types/rabbit-vaccination";

export const VACCINATION_SCHEDULED = "SCHEDULED";

/**
 * 该记录是否还欠下一针。
 *
 * 只看 `status` 不够：后端把「无需下一针」和「已被新记录接替」都收敛成 DONE，
 * 但 SCHEDULED 行在 `nextDueDate` 缺失时是脏数据，不该显示成待补种。
 */
export function awaitsNextDose(record: VaccinationRecord) {
 return record.status === VACCINATION_SCHEDULED && Boolean(record.nextDueDate);
}

export function vaccinationStatusLabel(record: VaccinationRecord) {
 return awaitsNextDose(record) ? "待补种" : "已完成";
}

/**
 * Badge 没有 warning 变体，而 DESIGN.md 不允许在页面里散原色。
 * 待补种是要人动手的那一个，用 default 拉住视线；已完成退到 secondary。
 */
export function vaccinationStatusVariant(
 record: VaccinationRecord,
): "default" | "secondary" {
 return awaitsNextDose(record) ? "default" : "secondary";
}

/**
 * 疫苗批号、剂量、途径拼成一行次要信息，缺的字段直接跳过。
 *
 * 三项全空时返回 `-`，与表格里其它日期列的空值写法保持一致。
 */
export function vaccinationDetailSummary(record: VaccinationRecord) {
 // 先 trim 源字段再拼前缀。先拼后判空会让只有空白的剂量变成「剂量   」，
 // 它非空于是混过过滤，表格里就出现一个没有值的标签。
 const batchNo = record.vaccineBatchNo?.trim();
 const dose = record.dose?.trim();
 const route = record.route?.trim();
 const parts = [
  batchNo ? `批号 ${batchNo}` : null,
  dose ? `剂量 ${dose}` : null,
  route || null,
 ].filter((part): part is string => Boolean(part));
 return parts.length > 0 ? parts.join(" · ") : "-";
}

/**
 * 按接种时间倒序，最近一针排最前；时间相同或缺失时按 id 倒序兜底，
 * 保证渲染顺序稳定，不会因为后端排序变化而让表格跳动。
 */
export function sortVaccinationRecords(records: VaccinationRecord[]) {
 return [...records].sort((left, right) => {
  const leftTime = left.vaccinatedAt
   ? Date.parse(left.vaccinatedAt)
   : Number.NaN;
  const rightTime = right.vaccinatedAt
   ? Date.parse(right.vaccinatedAt)
   : Number.NaN;
  const leftValid = Number.isFinite(leftTime);
  const rightValid = Number.isFinite(rightTime);
  if (leftValid && rightValid && leftTime !== rightTime) {
   return rightTime - leftTime;
  }
  if (leftValid !== rightValid) {
   return leftValid ? -1 : 1;
  }
  return right.id - left.id;
 });
}
