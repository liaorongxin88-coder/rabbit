package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.common.BizException;
import java.util.EnumSet;
import java.util.Set;

/**
 * 任意阶段入周期的锚点表（设计 §3.2「任意阶段入周期」，T1 的泛化）。
 *
 * <p>为什么需要它：兔场上线时母兔早已在生产中，不可能人人从「待催情」重新走一遍。
 * 存量录入、兔场初始化、后备转种母三条路径复用同一个 {@code openCycleAt}
 * 机制，就是靠这张表约束「从这个阶段入轨，必须补录哪些事实、首任务怎么算」。
 *
 * <p><b>V27 历史回填不走这里。</b>设计文档原本计划让回填也复用 openCycleAt，
 * 实际改成了集合式 SQL：一是 Flyway 迁移发布即冻结、只跑一次，不存在「日后
 * 各自演进」的漂移风险；二是逐只走状态机在万只规模下撞不进 30 分钟停写窗口。
 * 两者的口径一致性改由 {@code V27BackfillIT} 断言保障。修改本表时请同步核对
 * V27 的步骤 3（补建周期）与步骤 6（首任务到期日）。
 *
 * <p>{@link #AWAIT_WEANING} 入轨刻意不占管线锁：直接进哺乳段与血配规则一致
 * （见 {@link ReproStage#isPipeline()}），并且要在同事务建一条 NURSING 窝。
 */
public enum EntryPoint {
    /** 待催情：只需知道何时进入该阶段；首任务默认当天，用户可指定。 */
    ESTRUS(ReproStage.AWAIT_ESTRUS, DueAnchor.USER_SPECIFIED, EnumSet.of(RequiredFact.STAGE_ENTERED_AT)),
    /** 待配种：催情已完成，按催情持续期推算配种窗口。 */
    MATING(ReproStage.AWAIT_MATING, DueAnchor.ESTRUS_DURATION, EnumSet.of(RequiredFact.STAGE_ENTERED_AT)),
    /** 待摸胎：必须补录配种日（公兔可选），否则算不出摸胎日。 */
    PALPATION(ReproStage.AWAIT_PALPATION, DueAnchor.PALPATION_WAIT, EnumSet.of(RequiredFact.MATING_DATE)),
    /** 待备产：给配种日或直接给预产期均可。 */
    PREPARTUM(ReproStage.AWAIT_PREPARTUM, DueAnchor.PREPARTUM_LEAD, EnumSet.of(RequiredFact.GESTATION_ANCHOR)),
    /** 待分娩：同上，首任务落在预产期当天。 */
    DELIVERY(ReproStage.AWAIT_DELIVERY, DueAnchor.EXPECTED_BIRTH, EnumSet.of(RequiredFact.GESTATION_ANCHOR)),
    /** 待分笼：必须补录分娩日与活仔数，同事务建 NURSING 窝。 */
    WEANING(
        ReproStage.AWAIT_WEANING,
        DueAnchor.WEANING_DUE,
        EnumSet.of(RequiredFact.BIRTH_DATE, RequiredFact.LIVE_KITS)
    );

    /** 入轨时必须补录的事实；缺失即拒绝，不允许用默认值糊过去。 */
    public enum RequiredFact {
        STAGE_ENTERED_AT("进入该阶段的日期"),
        MATING_DATE("配种日期"),
        /** 配种日或预产期，二者给一个即可。 */
        GESTATION_ANCHOR("配种日期或预产期"),
        BIRTH_DATE("分娩日期"),
        LIVE_KITS("活仔数");

        private final String label;

        RequiredFact(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final ReproStage stage;
    private final DueAnchor dueAnchor;
    private final Set<RequiredFact> requiredFacts;

    EntryPoint(ReproStage stage, DueAnchor dueAnchor, Set<RequiredFact> requiredFacts) {
        this.stage = stage;
        this.dueAnchor = dueAnchor;
        this.requiredFacts = Set.copyOf(requiredFacts);
    }

    public ReproStage stage() {
        return stage;
    }

    public DueAnchor dueAnchor() {
        return dueAnchor;
    }

    public Set<RequiredFact> requiredFacts() {
        return requiredFacts;
    }

    /** 该入轨点是否占用管线互斥锁。 */
    public boolean occupiesPipeline() {
        return stage.isPipeline();
    }

    public static EntryPoint forStage(ReproStage stage) {
        for (EntryPoint entry : values()) {
            if (entry.stage == stage) {
                return entry;
            }
        }
        throw new BizException(400, "不支持从【" + stage.label() + "】入轨开启周期");
    }
}
