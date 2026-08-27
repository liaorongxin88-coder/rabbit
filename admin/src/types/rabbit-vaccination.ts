export interface VaccinationRecord {
  id: number;
  rabbitId: number;
  vaccineName: string;
  vaccineBatchNo?: string | null;
  dose?: string | null;
  route?: string | null;
  vaccinatedAt?: string | null;
  nextDueDate?: string | null;
  /** `SCHEDULED` 表示还欠下一针，`DONE` 表示本轮已闭合。 */
  status: string;
  remark?: string | null;
}
