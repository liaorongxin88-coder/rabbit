package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.repro.domain.DeliveryOutcome;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生产动作的编排层：状态迁移 + 该动作附带的领域副作用。
 *
 * <p><b>为什么要有这一层。</b>状态机只管周期怎么走，落位只管仔兔去哪儿，
 * 两者都不该知道对方。但它们必须<b>同生共死</b>：分笼一旦把周期关了却没能
 * 给仔兔占到笼位，数据就成了「窝已断奶、仔兔不存在」的悬空态。
 * 这一层的唯一职责就是把二者收进同一个事务。
 *
 * <p>控制器调用本服务而不是直接调状态机，正是为了让这个边界只有一处。
 */
@Service
public class ReproActionService {

    private final ReproStateMachineService stateMachine;
    private final KitPlacementService kitPlacementService;
    private final DeliveryAftercareService deliveryAftercareService;
    private final ReproCycleMapper reproCycleMapper;

    public ReproActionService(
        ReproStateMachineService stateMachine,
        KitPlacementService kitPlacementService,
        DeliveryAftercareService deliveryAftercareService,
        ReproCycleMapper reproCycleMapper
    ) {
        this.stateMachine = stateMachine;
        this.kitPlacementService = kitPlacementService;
        this.deliveryAftercareService = deliveryAftercareService;
        this.reproCycleMapper = reproCycleMapper;
    }

    /**
     * 执行一次生产动作。
     *
     * @param placement 分笼落位的补充入参；非分笼动作传 null
     */
    @Transactional
    public ReproResult apply(ReproCommand command, PlacementInput placement) {
        ReproResult result = stateMachine.apply(command);

        // 幂等回放：副作用早已发生。再跑一次就是凭空多出一窝兔子、
        // 或把产仔数在绩效里加两遍。
        if (result.replayed()) {
            return result;
        }
        if (command.getAction() == ReproAction.DELIVERY) {
            recordDelivery(command, result);
            return result;
        }
        if (command.getAction() == ReproAction.RETIRE) {
            closeRemainingCyclesAfterRetire(command, result);
            return result;
        }
        if (command.getAction() != ReproAction.WEANING) {
            return result;
        }

        ReproCycle cycle = reproCycleMapper.selectById(command.getHouseId(), result.cycleId());
        PlacementInput input = placement == null ? PlacementInput.empty() : placement;
        var weaningRecord = kitPlacementService.registerPending(new KitPlacementCommand(
            command.getHouseId(),
            command.getUserId(),
            operatorOf(command),
            cycle.getBatchId(),
            cycle.getId(),
            cycle.getMotherRabbitId(),
            cycle.getMaleRabbitId(),
            command.getOccurredAt(),
            command.getWeanedCount() == null ? 0 : command.getWeanedCount(),
            input.maleCount(),
            input.femaleCount(),
            input.targetCageId(),
            command.getAvgWeaningWeight(),
            command.getRemark(),
            command.getRequestId()
        ));
        return result.withWeaning(weaningRecord.getId(), weaningRecord.getWaitingCount());
    }

    /**
     * 母兔离场：结清她名下所有未结束的生产周期。
     *
     * <p>一头母兔可能同时持有两个开放周期（哺乳 + 血配新怀），所以逐个发 RETIRE；
     * 对单个周期的 RETIRE 只关它自己，不会顺手把另一个也关掉。
     *
     * <p>不能用旧的 {@code closeOpenByMother}：那条 UPDATE 只写 status/closed_at，
     * 不认识 lifecycle/stage，也不会取消待办、不会刷新母兔投影。结果是兔子已经离场，
     * 周期在新视角仍是 OPEN（永久占着 uk_bc_pipeline），待办也永远 PENDING——
     * 她会一直出现在今日清单里。
     *
     * @return 实际关闭的周期数
     */
    /**
     * 离场后收尾：把这头母兔剩下的开着的周期一并结清。
     *
     * <p>离场的语义是「这只母兔走了」，不是「这条周期结束了」，所以状态机在
     * T11 里按兔取消了全部待办。但它只能关掉被点名的那一条周期——血配时母兔
     * 同时持有哺乳与怀孕两条，剩下的那条会 OPEN 着却再也没有待办：
     * 崽子等不到分笼提醒，批次也会被这条看不见的周期永久卡住无法结束。
     *
     * <p>放在编排层而不是状态机里：跨周期的连带处理是业务编排，
     * 状态机只应对单条周期负责。
     */
    private void closeRemainingCyclesAfterRetire(ReproCommand command, ReproResult result) {
        ReproCycle retired = reproCycleMapper.selectById(command.getHouseId(), result.cycleId());
        if (retired == null) {
            return;
        }
        for (ReproCycle cycle : reproCycleMapper.selectOpenByMother(
            command.getHouseId(), retired.getMotherRabbitId()
        )) {
            stateMachine.apply(ReproCommand.builder()
                .houseId(command.getHouseId())
                .userId(command.getUserId())
                .operatorName(command.getOperatorName())
                .cycleId(cycle.getId())
                .action(ReproAction.RETIRE)
                .occurredAt(command.getOccurredAt())
                .reason(command.getReason())
                .requestId(ReproRequestIds.derive(command.getRequestId(), "retire-" + cycle.getId()))
                .build());
        }
    }

    @Transactional
    public int retireMother(
        Long houseId,
        Long userId,
        String operatorName,
        Long motherRabbitId,
        java.util.Date occurredAt,
        String reason,
        String requestId
    ) {
        List<ReproCycle> open = reproCycleMapper.selectOpenByMother(houseId, motherRabbitId);
        int closed = 0;
        for (ReproCycle cycle : open) {
            stateMachine.apply(ReproCommand.builder()
                .houseId(houseId)
                .userId(userId)
                .operatorName(operatorName)
                .cycleId(cycle.getId())
                .action(ReproAction.RETIRE)
                .occurredAt(occurredAt)
                .reason(reason)
                .requestId(ReproRequestIds.derive(requestId, "retire-" + cycle.getId()))
                .build());
            closed++;
        }
        return closed;
    }

    private void recordDelivery(ReproCommand command, ReproResult result) {
        ReproCycle cycle = reproCycleMapper.selectById(command.getHouseId(), result.cycleId());
        boolean failed = DeliveryOutcome.FAILED.name().equalsIgnoreCase(command.getOutcome());
        deliveryAftercareService.record(
            command.getHouseId(),
            cycle.getMotherRabbitId(),
            failed ? 0 : orZero(command.getTotalKits()),
            failed ? 0 : orZero(command.getLiveKits()),
            command.getOccurredAt(),
            failed,
            command.getRemark(),
            operatorOf(command)
        );
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String operatorOf(ReproCommand command) {
        if (command.getOperatorName() != null && !command.getOperatorName().isBlank()) {
            return command.getOperatorName();
        }
        return command.getUserId() == null ? "system" : String.valueOf(command.getUserId());
    }

    /** 分笼专属入参；放在 ReproCommand 之外，避免每个动作都拖着笼位字段。 */
    public record PlacementInput(Long targetCageId, Integer maleCount, Integer femaleCount) {
        public static PlacementInput empty() {
            return new PlacementInput(null, null, null);
        }
    }
}
