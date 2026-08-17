package com.rabbit.app.modules.repro.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;

/**
 * POST /api/repro/cycles/{cycleId}/actions —— 单只母兔的一次操作（设计 §5.2）。
 *
 * <p>六大操作共用一个请求体而不是六个专用 DTO：动作合法性由转换表判定，
 * 必填字段由 {@code validateFacts} 按动作判定，校验集中在一处才不会像旧实现
 * 那样在六个方法里各写一份、各自漂移。
 *
 * <p>{@code occurredAt} 允许回填过去时间（补录昨天的配种），到期时间会相应
 * 前移并被 {@code DueDateCalculator} 拉回今天，不会生成一条已过期的待办。
 */
public class CycleActionRequest {

    @NotBlank(message = "操作不能为空")
    private String action;

    /** 接产用：BORN / FAILED。 */
    private String outcome;

    private Date occurredAt;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    private String remark;

    private String reason;

    private Long maleRabbitId;

    private String matingMethod;

    /** 摸胎用：PREGNANT / EMPTY / UNSURE。 */
    private String palpationResult;

    /** 推迟到期时间；UNSURE 摸胎的复检日期也走这里。 */
    private Date nextRemindAt;

    private Integer totalKits;

    private Integer liveKits;

    private Integer keptKits;

    /** 流产死胎数（设计 §5.2）。 */
    private Integer stillbirthCount;

    private Integer weanedCount;

    private Double avgWeaningWeight;

    private Long nursingCageId;

    /** 分笼去向笼位；为空则自动选笼（先空笼后半满笼）。 */
    private Long targetCageId;

    /** 分笼时的公仔数；与 femaleCount 同为空/0 表示不区分性别。 */
    private Integer maleCount;

    /** 分笼时的母仔数。 */
    private Integer femaleCount;

    /** 附件只传 file_id 引用，事件 payload 不内联文件内容。 */
    private List<String> attachmentFileIds;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Date getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Date occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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

    public String getPalpationResult() {
        return palpationResult;
    }

    public void setPalpationResult(String palpationResult) {
        this.palpationResult = palpationResult;
    }

    public Date getNextRemindAt() {
        return nextRemindAt;
    }

    public void setNextRemindAt(Date nextRemindAt) {
        this.nextRemindAt = nextRemindAt;
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

    public Integer getStillbirthCount() {
        return stillbirthCount;
    }

    public void setStillbirthCount(Integer stillbirthCount) {
        this.stillbirthCount = stillbirthCount;
    }

    public Integer getWeanedCount() {
        return weanedCount;
    }

    public void setWeanedCount(Integer weanedCount) {
        this.weanedCount = weanedCount;
    }

    public Double getAvgWeaningWeight() {
        return avgWeaningWeight;
    }

    public void setAvgWeaningWeight(Double avgWeaningWeight) {
        this.avgWeaningWeight = avgWeaningWeight;
    }

    public Long getNursingCageId() {
        return nursingCageId;
    }

    public void setNursingCageId(Long nursingCageId) {
        this.nursingCageId = nursingCageId;
    }

    public List<String> getAttachmentFileIds() {
        return attachmentFileIds;
    }

    public void setAttachmentFileIds(List<String> attachmentFileIds) {
        this.attachmentFileIds = attachmentFileIds;
    }

    public Long getTargetCageId() {
        return targetCageId;
    }

    public void setTargetCageId(Long targetCageId) {
        this.targetCageId = targetCageId;
    }

    public Integer getMaleCount() {
        return maleCount;
    }

    public void setMaleCount(Integer maleCount) {
        this.maleCount = maleCount;
    }

    public Integer getFemaleCount() {
        return femaleCount;
    }

    public void setFemaleCount(Integer femaleCount) {
        this.femaleCount = femaleCount;
    }
}
