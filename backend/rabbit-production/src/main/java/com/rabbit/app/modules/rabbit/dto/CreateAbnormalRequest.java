package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateAbnormalRequest {
    @NotNull(message = "rabbitId不能为空")
    private Long rabbitId;

    @NotBlank(message = "异常类型不能为空")
    @Size(max = 50, message = "异常类型不能超过50个字符")
    private String warningStatus;

    @NotBlank(message = "请上传一张相关图片")
    @Size(max = 64, message = "图片引用不正确")
    private String imageFileId;

    @NotBlank(message = "异常说明不能为空")
    @Size(max = 255, message = "异常说明不能超过255个字符")
    private String remark;

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId不正确")
    private String requestId;

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public String getWarningStatus() {
        return warningStatus;
    }

    public void setWarningStatus(String warningStatus) {
        this.warningStatus = warningStatus;
    }

    public String getImageFileId() {
        return imageFileId;
    }

    public void setImageFileId(String imageFileId) {
        this.imageFileId = imageFileId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
