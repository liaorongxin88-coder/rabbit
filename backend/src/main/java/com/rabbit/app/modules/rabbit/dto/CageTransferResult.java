package com.rabbit.app.modules.rabbit.dto;

/**
 * 换笼位结果。
 *
 * <p>三种结局对用户是完全不同的事实，接口必须说清楚是哪一种，客户端才能给出正确提示：
 * 搬进空笼（MOVE）、并入商品兔笼（APPEND）、两笼互换（SWAP，此时 {@code swappedRabbitId}
 * 是被换过来的那只）。
 */
public record CageTransferResult(
        String mode,
        Long rabbitId,
        Long fromCageId,
        Long toCageId,
        Long swappedRabbitId
) {
    public static final String MODE_MOVE = "MOVE";
    public static final String MODE_APPEND = "APPEND";
    public static final String MODE_SWAP = "SWAP";
    /** 幂等重放：该请求之前已成功，只能告知当前落位。 */
    public static final String MODE_REPLAY = "REPLAY";
}
