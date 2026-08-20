package com.rabbit.app.modules.repro.domain;

import java.util.Date;

/**
 * 计算下一条待办到期日所需的事实集合。
 *
 * <p>把这些事实收进一个不可变载体、由调用方显式填齐，而不是让计算器回头去查库，
 * 是为了让到期日计算保持纯函数——转换矩阵单测才能穷举「7 阶段 × 11 动作」而不碰数据库。
 *
 * @param occurredAt        操作的业务时间（允许补录历史）
 * @param stageEnteredAt    进入目标阶段的时间；催情窗口以它为锚
 * @param matingDate        配种日，待摸胎提醒的锚
 * @param expectedBirthDate 预产期参考值；由配种日 + gestation_days 推出
 * @param birthDate         分娩日，分笼日的锚
 * @param userSpecified     用户在表单里选的下次提醒时间（推迟 / 摸胎不确定复查）
 * @param today             「当天」基准，注入以便测试；到期日不得早于它
 */
public record DueContext(
    Date occurredAt,
    Date stageEnteredAt,
    Date matingDate,
    Date expectedBirthDate,
    Date birthDate,
    Date userSpecified,
    Date today
) {
    public static Builder builder(Date occurredAt, Date today) {
        return new Builder(occurredAt, today);
    }

    public static final class Builder {
        private final Date occurredAt;
        private final Date today;
        private Date stageEnteredAt;
        private Date matingDate;
        private Date expectedBirthDate;
        private Date birthDate;
        private Date userSpecified;

        private Builder(Date occurredAt, Date today) {
            this.occurredAt = occurredAt;
            this.today = today;
        }

        public Builder stageEnteredAt(Date value) {
            this.stageEnteredAt = value;
            return this;
        }

        public Builder matingDate(Date value) {
            this.matingDate = value;
            return this;
        }

        public Builder expectedBirthDate(Date value) {
            this.expectedBirthDate = value;
            return this;
        }

        public Builder birthDate(Date value) {
            this.birthDate = value;
            return this;
        }

        public Builder userSpecified(Date value) {
            this.userSpecified = value;
            return this;
        }

        public DueContext build() {
            return new DueContext(
                occurredAt, stageEnteredAt, matingDate, expectedBirthDate, birthDate, userSpecified, today
            );
        }
    }
}
