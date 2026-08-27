package com.rabbit.app.modules.repro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * POST /api/repro/cycles —— 从任意阶段开启一个生产周期（设计 §5.1）。
 *
 * <p>{@code stage} 决定必须补录哪些事实，校验规则集中在 {@code EntryPoint}，
 * 这里只做形状校验。缺失事实一律拒绝而不是填默认值：默认出来的配种日期会一路
 * 传导成错误的预产期和错误的备产提醒。
 *
 * <p>{@code batchId} 在休养、待催情和待配种阶段可空；传值时表示计划批次。待摸胎及后续阶段必填。
 */
public class OpenCycleRequest {

    @NotNull(message = "母兔不能为空")
    private Long motherRabbitId;

    /** 早期阶段可空或作为计划批次；待摸胎及后续阶段必填。 */
    private Long batchId;

    /** 入轨阶段；缺省 AWAIT_ESTRUS（新母兔从待催情起步）。 */
    private String stage;

    private Date occurredAt;

    private Date stageEnteredAt;

    private Date matingDate;

    private Date expectedBirthDate;

    private Date birthDate;

    private Integer totalKits;

    private Integer liveKits;

    private Integer keptKits;

    private Long maleRabbitId;

    private String matingMethod;

    /** 首个待办的到期时间；仅 USER_SPECIFIED 锚点使用。 */
    private Date firstDueAt;

    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Long getMotherRabbitId() {
        return motherRabbitId;
    }

    public void setMotherRabbitId(Long motherRabbitId) {
        this.motherRabbitId = motherRabbitId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Date getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Date occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Date getStageEnteredAt() {
        return stageEnteredAt;
    }

    public void setStageEnteredAt(Date stageEnteredAt) {
        this.stageEnteredAt = stageEnteredAt;
    }

    public Date getMatingDate() {
        return matingDate;
    }

    public void setMatingDate(Date matingDate) {
        this.matingDate = matingDate;
    }

    public Date getExpectedBirthDate() {
        return expectedBirthDate;
    }

    public void setExpectedBirthDate(Date expectedBirthDate) {
        this.expectedBirthDate = expectedBirthDate;
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

    public Integer getKeptKits() {
        return keptKits;
    }

    public void setKeptKits(Integer keptKits) {
        this.keptKits = keptKits;
    }

    public Long getMaleRabbitId() {
        return maleRabbitId;
    }

    public void setMaleRabbitId(Long maleRabbitId) {
        this.maleRabbitId = maleRabbitId;
    }

    public String getMatingMethod() {
        return matingMethod;
    }

    public void setMatingMethod(String matingMethod) {
        this.matingMethod = matingMethod;
    }

    public Date getFirstDueAt() {
        return firstDueAt;
    }

    public void setFirstDueAt(Date firstDueAt) {
        this.firstDueAt = firstDueAt;
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
