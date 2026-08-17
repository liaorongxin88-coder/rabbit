package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.common.BizException;
import com.rabbit.app.util.DateUtil;
import java.util.Date;

/**
 * 由锚点 + 事实 + 配置算出下一条待办的到期时间（设计 §3.2）。
 *
 * <p>纯函数、不碰数据库：转换矩阵与到期日的单测因此可以穷举全部组合。
 */
public final class DueDateCalculator {
    private DueDateCalculator() {
    }

    /**
     * @return 到期时间；{@link DueAnchor#NONE} 返回 null，表示这次转换不产生后续任务
     */
    public static Date compute(DueAnchor anchor, DueContext context, ReproSettings settings) {
        if (anchor == DueAnchor.NONE) {
            return null;
        }
        Date raw = rawDue(anchor, context, settings);
        // 补录历史操作时算出来的到期日往往已经过去（例如补登 20 天前的配种，摸胎日早就到了）。
        // 拉平到当天而不是留在过去，待办才会真的出现在「今日待办」里被人看见。
        return notBefore(raw, context.today());
    }

    /** 预产期 = 配种日 + gestation_days（配置化，取代旧实现里硬编码的 30）。 */
    public static Date expectedBirthDate(Date matingDate, ReproSettings settings) {
        if (matingDate == null) {
            return null;
        }
        return DateUtil.plusDays(matingDate, settings.gestationDays());
    }

    private static Date rawDue(DueAnchor anchor, DueContext context, ReproSettings settings) {
        return switch (anchor) {
            case ESTRUS_DURATION -> DateUtil.plusDays(
                require(context.stageEnteredAt(), "进入待配种阶段的日期"),
                settings.estrusDurationDays()
            );
            case PALPATION_WAIT -> DateUtil.plusDays(
                require(context.matingDate(), "配种日期"),
                settings.palpationWaitDays()
            );
            case PREPARTUM_LEAD -> DateUtil.minusDays(
                require(resolveExpectedBirth(context, settings), "预产期"),
                settings.prepartumLeadDays()
            );
            case EXPECTED_BIRTH -> require(resolveExpectedBirth(context, settings), "预产期");
            case WEANING_DUE -> DateUtil.plusDays(
                require(context.birthDate(), "分娩日期"),
                settings.weaningDays()
            );
            case POSTPARTUM_RECOVERY -> DateUtil.plusDays(
                require(context.occurredAt(), "操作时间"),
                settings.postpartumRecoveryDays()
            );
            case IMMEDIATE -> context.today();
            // 「待催情入轨」允许不指定，缺省当天；推迟与摸胎不确定则由调用方前置校验必填。
            case USER_SPECIFIED -> context.userSpecified() != null ? context.userSpecified() : context.today();
            case NONE -> null;
        };
    }

    private static Date resolveExpectedBirth(DueContext context, ReproSettings settings) {
        if (context.expectedBirthDate() != null) {
            return context.expectedBirthDate();
        }
        return expectedBirthDate(context.matingDate(), settings);
    }

    private static Date require(Date value, String what) {
        if (value == null) {
            throw new BizException(400, "缺少" + what + "，无法计算下次提醒时间");
        }
        return value;
    }

    private static Date notBefore(Date value, Date floor) {
        if (value == null || floor == null) {
            return value;
        }
        return value.before(floor) ? floor : value;
    }
}
