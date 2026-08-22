package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 换笼位请求。
 *
 * <p>与 {@code PUT /api/rabbits/{id}} 里顺带改 cageId 的编辑路径不同：编辑路径只能把兔子
 * 搬进空笼或同用途的商品兔笼，目标笼已有种兔时只会撞唯一键报错。换笼位是独立动作，
 * 需要按目标笼内的实际情况决定是入笼、合笼还是两笼对调。
 */
public class CageTransferRequest {
    @NotNull(message = "targetCageId不能为空")
    private Long targetCageId;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Long getTargetCageId() {
        return targetCageId;
    }

    public void setTargetCageId(Long targetCageId) {
        this.targetCageId = targetCageId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
