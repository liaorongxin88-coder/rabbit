package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.repro.domain.MatingMethod;
import com.rabbit.app.modules.repro.domain.ReproStage;
import java.util.Date;

/**
 * 从任意阶段开启一个周期（设计 §3.4）。
 *
 * <p>四条路径共用它：存量母兔录入、开场初始化、后备兔转种、V27 历史回填。
 * 共用的意义在于回填脚本与线上接口走同一套校验——否则回填出来的数据会带着
 * 线上不可能出现的状态组合，等到 P4 切换时才爆。
 *
 * @param targetStage       入轨阶段，决定必须补录哪些事实
 * @param stageEnteredAt    进入该阶段的时间；缺省为 occurredAt
 * @param firstDueAt        首任务到期时间；仅 USER_SPECIFIED 锚点使用
 */
public record OpenCycleCommand(
    Long houseId,
    Long userId,
    String operatorName,
    Long motherRabbitId,
    Long batchId,
    ReproStage targetStage,
    Date occurredAt,
    Date stageEnteredAt,
    Date matingDate,
    Date expectedBirthDate,
    Date birthDate,
    Integer totalKits,
    Integer liveKits,
    Long maleRabbitId,
    MatingMethod matingMethod,
    Date firstDueAt,
    String remark,
    String requestId
) {
}
