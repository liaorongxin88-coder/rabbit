package com.rabbit.app.modules.repro.service;

import java.util.Date;

/**
 * 分笼落位的入参：把仔兔从母兔笼迁到商品兔笼。
 *
 * <p>与 {@link ReproCommand} 分开，是因为两者回答的是不同的问题：
 * ReproCommand 说「母兔的周期怎么走」，这个说「这一窝仔兔去哪儿」。
 * 前者由状态机处理，后者由 {@link KitPlacementService} 处理，
 * 状态机不该知道笼位容量，笼位也不该知道转换表。
 *
 * @param targetCageId 指定笼位；为空则自动选笼（先空笼后半满笼）
 * @param maleCount    公仔数；与 femaleCount 同为 0 表示不区分性别
 * @param femaleCount  母仔数
 */
public record KitPlacementCommand(
    Long houseId,
    Long userId,
    String operator,
    Long batchId,
    Long cycleId,
    Long motherRabbitId,
    Long sireRabbitId,
    Date weaningDate,
    int weanedCount,
    Integer maleCount,
    Integer femaleCount,
    Long targetCageId,
    Double avgWeight,
    String remark,
    String requestId
) {
}
