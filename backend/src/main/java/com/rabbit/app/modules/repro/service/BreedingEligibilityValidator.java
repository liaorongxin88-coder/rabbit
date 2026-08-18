package com.rabbit.app.modules.repro.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.repro.domain.ReproStage;
import com.rabbit.app.modules.repro.entity.ReproCycle;
import com.rabbit.app.modules.repro.mapper.ReproCycleMapper;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 配种资格校验：这对兔子现在能不能配。
 *
 * <p>从 {@code BatchService.matingInternal} 迁来。旧实现把这些判断和状态流转混在
 * 191 行里，新路径此前只校验了「体配必须选公兔」，性别、类型、在栏与否全都没查——
 * 意味着可以拿一只母兔当公兔配，或者给已离场的兔子配种。
 *
 * <h2>为什么由状态机调用，而不是编排层</h2>
 *
 * <p>批量待办（{@code WorkTaskService.bulkApply}）直连状态机，不经编排层。
 * 校验若放在编排层，批量配种就会整片漏检——而批量恰恰是最容易选错兔子的入口。
 * 放进状态机的 validateFacts，API、批量、未来的任何调用方都绕不过去。
 *
 * <h2>为什么不在 openCycleAt 上也校验</h2>
 *
 * <p>入轨（存量录入、建场初始化、V27 补录）填的是历史事实，其中的公兔可能早已离场。
 * 拿今天的在栏状态去否决昨天发生过的事，只会让存量录不进来。
 */
@Component
public class BreedingEligibilityValidator {

    private final RabbitMapper rabbitMapper;
    private final ReproCycleMapper reproCycleMapper;

    public BreedingEligibilityValidator(
        RabbitMapper rabbitMapper, ReproCycleMapper reproCycleMapper
    ) {
        this.rabbitMapper = rabbitMapper;
        this.reproCycleMapper = reproCycleMapper;
    }

    /**
     * @param buckRabbitId 人工授精可为 null（混精 / 外购冻精）
     */
    public void validateMating(
        Long houseId, Long motherRabbitId, Long buckRabbitId, Date matingDate
    ) {
        requireDoe(houseId, motherRabbitId);
        if (buckRabbitId != null) {
            requireBuck(houseId, buckRabbitId);
        }
        requireNotBeforeLastBirth(houseId, motherRabbitId, matingDate);
    }

    private void requireDoe(Long houseId, Long rabbitId) {
        Rabbit doe = onSite(houseId, rabbitId, "母兔");
        if (!"0".equals(doe.getGender())) {
            throw new BizException(400, "母兔性别不正确");
        }
        // 0 种兔、1 后备兔都可以配；2 商品兔不行。
        if (!"0".equals(doe.getType()) && !"1".equals(doe.getType())) {
            throw new BizException(400, "母兔类型不正确");
        }
    }

    private void requireBuck(Long houseId, Long rabbitId) {
        Rabbit buck = onSite(houseId, rabbitId, "公兔");
        if (!"1".equals(buck.getGender())) {
            throw new BizException(400, "公兔性别不正确");
        }
        if (!"0".equals(buck.getType())) {
            throw new BizException(400, "仅种公兔可用于配种");
        }
    }

    private Rabbit onSite(Long houseId, Long rabbitId, String who) {
        Rabbit rabbit = rabbitMapper.selectById(houseId, rabbitId);
        if (rabbit == null || !houseId.equals(rabbit.getHouseId())) {
            throw new BizException(400, who + "不存在");
        }
        if (rabbit.getIsActive() == null || !rabbit.getIsActive()) {
            throw new BizException(400, who + "不在场");
        }
        return rabbit;
    }

    /**
     * 血配（哺乳期复配）的日期底线：复配日不能早于上一窝的产仔日。
     * 早于产仔日意味着这次配种发生在上一窝出生之前，属于录入错误；
     * 放过去会让「产后复配天数」变成负数，污染繁殖节律统计。
     */
    private void requireNotBeforeLastBirth(Long houseId, Long motherRabbitId, Date matingDate) {
        if (matingDate == null) {
            return;
        }
        List<ReproCycle> open = reproCycleMapper.selectOpenByMother(houseId, motherRabbitId);
        if (open == null) {
            return;
        }
        for (ReproCycle cycle : open) {
            if (!ReproStage.AWAIT_WEANING.name().equals(cycle.getStage())) {
                continue;
            }
            Date birthDate = cycle.getBirthDate();
            if (birthDate != null && DateUtil.daysBetween(birthDate, matingDate) < 0) {
                throw new BizException(400, "二次配种日期不能早于上一窝产仔日期");
            }
        }
    }
}
