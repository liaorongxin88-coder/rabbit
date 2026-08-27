package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
import com.rabbit.app.modules.rabbit.mapper.RabbitAbnormalConditionMapper;
import java.util.Date;
import org.springframework.stereotype.Service;

/**
 * 接产的附带记账：绩效累加，失败时再挂一条健康预警。
 *
 * <p>与 {@link KitPlacementService} 同类，都是「状态机不该知道、但确实要发生」
 * 的领域副作用，放在编排层由 {@link ReproActionService} 收进同一个事务。
 *
 * <h2>与旧实现的一处业务差异（有意为之，需要业务确认）</h2>
 *
 * <p>旧的 {@code BatchService.parturition} 在分娩失败且母兔没有其它哺乳窝时，
 * 会<b>直接让母兔离场</b>：退出批次、退笼位、写 departure_type=parturition_fail
 * 的离场记录、状态历史记「流产离场」。
 *
 * <p>本实现<b>不淘汰母兔</b>，只关闭本次周期并按 T6x 进入休养期，恢复到期后自动进入待催情。
 * 理由：转换表已经把「离场」独立成 T11 RETIRE 动作，由人显式发起；
 * 一次分娩失败就隐式、不可逆地淘汰一只种母，代价与证据不匹配。
 * 若业务确认要保留旧的自动淘汰，正确做法是在这里补一次 RETIRE，
 * 而不是把离场逻辑再散回状态机。
 */
@Service
public class DeliveryAftercareService {

    private final BreedingPerformanceRecorder performanceRecorder;
    private final RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper;

    public DeliveryAftercareService(
        BreedingPerformanceRecorder performanceRecorder,
        RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper
    ) {
        this.performanceRecorder = performanceRecorder;
        this.rabbitAbnormalConditionMapper = rabbitAbnormalConditionMapper;
    }

    /**
     * @param failed true 表示失败产（全窝无活仔）
     */
    public void record(
        Long houseId,
        Long motherRabbitId,
        int totalKits,
        int liveKits,
        Date birthDate,
        boolean failed,
        String remark,
        String operator
    ) {
        performanceRecorder.recordParturition(
            houseId, motherRabbitId, totalKits, liveKits, birthDate
        );
        if (!failed) {
            return;
        }
        // 失败产挂预警而不是淘汰：让人看见并决定，而不是替人决定。
        RabbitAbnormalCondition condition = new RabbitAbnormalCondition();
        condition.setHouseId(houseId);
        condition.setRabbitId(motherRabbitId);
        condition.setWarningStatus("流产");
        condition.setWarningTime(birthDate);
        condition.setIsDeal(Boolean.FALSE);
        condition.setRemark(remark);
        condition.setCreateBy(operator);
        condition.setUpdateBy(operator);
        rabbitAbnormalConditionMapper.insert(condition);
    }
}
