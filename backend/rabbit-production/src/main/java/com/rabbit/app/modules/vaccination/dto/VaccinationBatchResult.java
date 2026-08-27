package com.rabbit.app.modules.vaccination.dto;

import com.rabbit.app.modules.vaccination.entity.VaccinationRecord;
import java.util.List;

/**
 * 批量接种结果。
 *
 * @param created 本次实际落库的记录数；幂等重放时为 0
 * @param records 落库或回查到的记录，顺序与请求的 rabbitIds 一致
 */
public record VaccinationBatchResult(int created, List<VaccinationRecord> records) {
}
