package com.rabbit.app.modules.batch.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

public class ParturitionRequest {
    @NotNull(message = "rabbitId不能为空")
    private Long rabbitId;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @NotNull(message = "birthDate不能为空")
    private Date birthDate;

    @NotNull(message = "totalKits不能为空")
    @Min(value = 0, message = "totalKits不能小于0")
    private Integer totalKits;

    @NotNull(message = "liveKits不能为空")
    @Min(value = 0, message = "liveKits不能小于0")
    private Integer liveKits;

    private Boolean failed;
    private String remark;

    public Long getRabbitId() {
        return rabbitId;
    }

    public void setRabbitId(Long rabbitId) {
        this.rabbitId = rabbitId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Date getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
    }

    public Integer getTotalKits() {
        return totalKits;
    }

    public void setTotalKits(Integer totalKits) {
        this.totalKits = totalKits;
    }

    public Integer getLiveKits() {
        return liveKits;
    }

    public void setLiveKits(Integer liveKits) {
        this.liveKits = liveKits;
    }

    public Boolean getFailed() {
        return failed;
    }

    public void setFailed(Boolean failed) {
        this.failed = failed;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
