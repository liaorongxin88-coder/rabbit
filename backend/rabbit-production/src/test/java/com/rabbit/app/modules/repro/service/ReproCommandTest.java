package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.rabbit.app.modules.repro.domain.MatingMethod;
import com.rabbit.app.modules.repro.domain.PalpationResult;
import com.rabbit.app.modules.repro.domain.ReproAction;
import com.rabbit.app.util.DateUtil;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 状态推进的入参载体。
 *
 * <p>它是六个动作共用的唯一入参，字段平铺、按动作各取所需。风险恰恰来自这个形状：
 * builder 里两个同类型字段接错（比如公兔 id 落进笼位 id、活仔数落进留仔数）编译期
 * 完全看不出来，运行时也不会报错，只会把错的数字安静写进窝记录。下面逐个把
 * 「setter 落在哪个字段」钉死。
 */
class ReproCommandTest {

    /**
     * 每个 builder 方法必须落在它自己的字段上。这些字段随后被状态机分别用于校验、
     * 计数和事件 payload，串位一次就是一条永久错误的繁殖记录。
     */
    @Test
    void everyBuilderFieldLandsOnItsOwnProperty() {
        Date occurred = DateUtil.plusDays(DateUtil.now(), -1);
        Date remind = DateUtil.plusDays(DateUtil.now(), 3);
        List<String> files = List.of("f1", "f2");

        ReproCommand command = ReproCommand.builder()
            .houseId(1L)
            .userId(7L)
            .operatorName("张三")
            .cycleId(100L)
            .motherRabbitId(10L)
            .batchId(30L)
            .action(ReproAction.DELIVERY)
            .outcome("BORN")
            .occurredAt(occurred)
            .requestId("req-1")
            .remark("顺产")
            .reason("淘汰")
            .maleRabbitId(20L)
            .matingMethod(MatingMethod.AI)
            .palpationResult(PalpationResult.PREGNANT)
            .nextRemindAt(remind)
            .totalKits(9)
            .liveKits(7)
            .keptKits(6)
            .stillbirthCount(2)
            .weanedCount(5)
            .avgWeaningWeight(0.62)
            .nursingCageId(55L)
            .attachmentFileIds(files)
            .build();

        assertEquals(1L, command.getHouseId());
        assertEquals(7L, command.getUserId());
        assertEquals("张三", command.getOperatorName());
        assertEquals(100L, command.getCycleId());
        assertEquals(10L, command.getMotherRabbitId());
        assertEquals(30L, command.getBatchId());
        assertEquals(ReproAction.DELIVERY, command.getAction());
        assertEquals("BORN", command.getOutcome());
        assertSame(occurred, command.getOccurredAt());
        assertEquals("req-1", command.getRequestId());
        assertEquals("顺产", command.getRemark());
        assertEquals("淘汰", command.getReason());
        assertEquals(20L, command.getMaleRabbitId());
        assertEquals(MatingMethod.AI, command.getMatingMethod());
        assertEquals(PalpationResult.PREGNANT, command.getPalpationResult());
        assertSame(remind, command.getNextRemindAt());
        assertEquals(9, command.getTotalKits());
        assertEquals(7, command.getLiveKits());
        assertEquals(6, command.getKeptKits());
        assertEquals(2, command.getStillbirthCount());
        assertEquals(5, command.getWeanedCount());
        assertEquals(0.62, command.getAvgWeaningWeight());
        assertEquals(55L, command.getNursingCageId());
        assertSame(files, command.getAttachmentFileIds());
    }

    /**
     * 未设置的动作专属字段必须留 null，不能有隐式默认值。状态机对「缺必要事实」的
     * 判定全靠 null，给了默认值就等于把校验绕过去，缺仔数的接产会被当成 0 仔接产。
     */
    @Test
    void unsetActionSpecificFieldsStayNullSoTheValidatorCanSeeThemMissing() {
        ReproCommand command = ReproCommand.builder()
            .houseId(1L)
            .action(ReproAction.DELIVERY)
            .build();

        assertNull(command.getTotalKits());
        assertNull(command.getLiveKits());
        assertNull(command.getKeptKits());
        assertNull(command.getWeanedCount());
        assertNull(command.getStillbirthCount());
        assertNull(command.getOutcome());
        assertNull(command.getPalpationResult());
        assertNull(command.getMatingMethod());
        assertNull(command.getNextRemindAt());
        assertNull(command.getOccurredAt());
        assertNull(command.getAttachmentFileIds());
    }

    /**
     * 附件是唯一可写回的字段：校验时把文件 id 规范化后写回，落库和事件 payload 都读
     * 这个结果。写回若不生效，事件里记的还是用户提交的原始列表，与实际落库的附件对不上。
     */
    @Test
    void theNormalisedAttachmentListReplacesTheSubmittedOne() {
        ReproCommand command = ReproCommand.builder()
            .attachmentFileIds(List.of("raw"))
            .build();

        command.setAttachmentFileIds(List.of("checked-1", "checked-2"));

        assertEquals(List.of("checked-1", "checked-2"), command.getAttachmentFileIds());
    }

    /** 每次 builder() 都要产出独立的命令对象，否则批量操作里各只兔子会互相污染入参。 */
    @Test
    void eachBuilderProducesAnIndependentCommand() {
        ReproCommand first = ReproCommand.builder().cycleId(1L).build();
        ReproCommand second = ReproCommand.builder().cycleId(2L).build();

        assertEquals(1L, first.getCycleId());
        assertEquals(2L, second.getCycleId());
    }
}
