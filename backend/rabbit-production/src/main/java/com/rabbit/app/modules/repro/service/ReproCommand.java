package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.repro.domain.MatingMethod;
import com.rabbit.app.modules.repro.domain.PalpationResult;
import com.rabbit.app.modules.repro.domain.ReproAction;
import java.util.Date;
import java.util.List;

/**
 * 一次状态推进的完整输入。
 *
 * <p>做成单一命令对象而不是给每个动作开一个方法签名，是本次重构的关键：
 * 旧实现里六个动作各有一套参数、各自校验、各自更新，规则重复六遍且互相漂移。
 * 统一入参后，校验与转换只有一处实现。
 *
 * <p>动作专属字段（配种的公兔、摸胎结论、接产窝数……）刻意平铺而非按动作分子类型：
 * 校验由转换表 + 前置断言集中完成，分子类型只会把分支从服务层搬到构造期。
 */
public final class ReproCommand {
    private Long houseId;
    private Long userId;
    private String operatorName;
    private Long cycleId;
    private Long motherRabbitId;
    private Long batchId;
    private ReproAction action;
    private String outcome;
    /** 业务发生时间，允许补录历史；为空时由服务层取当前时间。 */
    private Date occurredAt;
    private String requestId;
    private String remark;
    private String reason;

    private Long maleRabbitId;
    private MatingMethod matingMethod;
    private PalpationResult palpationResult;
    /** 下一待办的可选覆盖日期；推迟与摸胎不确定时必填。 */
    private Date nextRemindAt;

    private Integer totalKits;
    private Integer liveKits;
    private Integer keptKits;
    /** 流产死胎数（设计 §5.2）。仅流产动作使用。 */
    private Integer stillbirthCount;
    private Integer weanedCount;
    private Double avgWeaningWeight;
    private Long nursingCageId;

    /** 附件只传 file_id，落到 biz_attachments，事件 payload 不内联文件内容。 */
    private List<String> attachmentFileIds;

    public static Builder builder() {
        return new Builder();
    }

    public Long getHouseId() {
        return houseId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public Long getCycleId() {
        return cycleId;
    }

    public Long getMotherRabbitId() {
        return motherRabbitId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public ReproAction getAction() {
        return action;
    }

    public String getOutcome() {
        return outcome;
    }

    public Date getOccurredAt() {
        return occurredAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRemark() {
        return remark;
    }

    public String getReason() {
        return reason;
    }

    public Long getMaleRabbitId() {
        return maleRabbitId;
    }

    public MatingMethod getMatingMethod() {
        return matingMethod;
    }

    public PalpationResult getPalpationResult() {
        return palpationResult;
    }

    public Date getNextRemindAt() {
        return nextRemindAt;
    }

    public Integer getTotalKits() {
        return totalKits;
    }

    public Integer getLiveKits() {
        return liveKits;
    }

    public Integer getKeptKits() {
        return keptKits;
    }

    public Integer getStillbirthCount() {
        return stillbirthCount;
    }

    public Integer getWeanedCount() {
        return weanedCount;
    }

    public Double getAvgWeaningWeight() {
        return avgWeaningWeight;
    }

    public Long getNursingCageId() {
        return nursingCageId;
    }

    public List<String> getAttachmentFileIds() {
        return attachmentFileIds;
    }

    public void setAttachmentFileIds(List<String> attachmentFileIds) {
        this.attachmentFileIds = attachmentFileIds;
    }

    public static final class Builder {
        private final ReproCommand command = new ReproCommand();

        public Builder houseId(Long value) {
            command.houseId = value;
            return this;
        }

        public Builder userId(Long value) {
            command.userId = value;
            return this;
        }

        public Builder operatorName(String value) {
            command.operatorName = value;
            return this;
        }

        public Builder cycleId(Long value) {
            command.cycleId = value;
            return this;
        }

        public Builder motherRabbitId(Long value) {
            command.motherRabbitId = value;
            return this;
        }

        public Builder batchId(Long value) {
            command.batchId = value;
            return this;
        }

        public Builder action(ReproAction value) {
            command.action = value;
            return this;
        }

        public Builder outcome(String value) {
            command.outcome = value;
            return this;
        }

        public Builder occurredAt(Date value) {
            command.occurredAt = value;
            return this;
        }

        public Builder requestId(String value) {
            command.requestId = value;
            return this;
        }

        public Builder remark(String value) {
            command.remark = value;
            return this;
        }

        public Builder reason(String value) {
            command.reason = value;
            return this;
        }

        public Builder maleRabbitId(Long value) {
            command.maleRabbitId = value;
            return this;
        }

        public Builder matingMethod(MatingMethod value) {
            command.matingMethod = value;
            return this;
        }

        public Builder palpationResult(PalpationResult value) {
            command.palpationResult = value;
            return this;
        }

        public Builder nextRemindAt(Date value) {
            command.nextRemindAt = value;
            return this;
        }

        public Builder totalKits(Integer value) {
            command.totalKits = value;
            return this;
        }

        public Builder liveKits(Integer value) {
            command.liveKits = value;
            return this;
        }

        public Builder keptKits(Integer value) {
            command.keptKits = value;
            return this;
        }

        public Builder stillbirthCount(Integer value) {
            command.stillbirthCount = value;
            return this;
        }

        public Builder weanedCount(Integer value) {
            command.weanedCount = value;
            return this;
        }

        public Builder avgWeaningWeight(Double value) {
            command.avgWeaningWeight = value;
            return this;
        }

        public Builder nursingCageId(Long value) {
            command.nursingCageId = value;
            return this;
        }

        public Builder attachmentFileIds(List<String> value) {
            command.attachmentFileIds = value;
            return this;
        }

        public ReproCommand build() {
            return command;
        }
    }
}
