package com.rabbit.app.modules.repro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
import com.rabbit.app.modules.rabbit.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 接产后的记账与失败产处置。
 *
 * <p>被测类的注释写明它与旧实现有一处有意的业务差异：旧 {@code BatchService.parturition}
 * 在分娩失败时会<b>直接淘汰母兔</b>（退批次、退笼位、写离场记录），现在改为只挂一条
 * 「流产」健康预警，把淘汰与否交回给人显式决定。
 *
 * <p>这个差异没有编译期约束护着，一次「恢复旧行为」的重构就能把隐式淘汰改回来，
 * 而被误淘汰的是种母兔——不可逆。所以这里把它钉成用例：失败产只产生预警，
 * 并且预警必须是未处理状态，否则它一生成就沉底，等于没提醒。
 *
 * <p>另一半是绩效记账：无论成败都必须累加，漏记会让繁殖率统计偏高。
 */
class DeliveryAftercareServiceTest {

    private static final Long HOUSE_ID = 8L;
    private static final Long MOTHER_ID = 100L;

    private BreedingPerformanceRecorder performanceRecorder;
    private RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper;
    private RabbitMapper rabbitMapper;
    private DeliveryAftercareService service;

    @BeforeEach
    void setUp() {
        performanceRecorder = mock(BreedingPerformanceRecorder.class);
        rabbitAbnormalConditionMapper = mock(RabbitAbnormalConditionMapper.class);
        rabbitMapper = mock(RabbitMapper.class);
        service = new DeliveryAftercareService(performanceRecorder, rabbitAbnormalConditionMapper, rabbitMapper);
    }

    /** 正常产只记绩效，不该凭空给母兔挂一条健康预警。 */
    @Test
    void aSuccessfulDeliveryOnlyRecordsPerformance() {
        Date birthDate = new Date(1_700_000_000_000L);

        service.record(HOUSE_ID, MOTHER_ID, 9, 8, birthDate, false, "顺产", "op");

        verify(performanceRecorder).recordParturition(HOUSE_ID, MOTHER_ID, 9, 8, birthDate);
        verifyNoInteractions(rabbitAbnormalConditionMapper);
    }

    /**
     * 失败产的处置是「挂预警让人看见」，而不是替人把种母兔淘汰掉。
     * 这条用例守的正是与旧实现的那处有意差异。
     */
    @Test
    void aFailedDeliveryRaisesAnUnhandledWarningInsteadOfRetiringTheDoe() {
        Date birthDate = new Date(1_700_000_000_000L);

        service.record(HOUSE_ID, MOTHER_ID, 6, 0, birthDate, true, "全窝死胎", "op");

        ArgumentCaptor<RabbitAbnormalCondition> captor =
            ArgumentCaptor.forClass(RabbitAbnormalCondition.class);
        verify(rabbitAbnormalConditionMapper).insert(captor.capture());
        RabbitAbnormalCondition warning = captor.getValue();
        assertEquals(HOUSE_ID, warning.getHouseId());
        assertEquals(MOTHER_ID, warning.getRabbitId());
        assertEquals("流产", warning.getWarningStatus());
        assertEquals(birthDate, warning.getWarningTime());
        assertEquals("全窝死胎", warning.getRemark());
        assertEquals("op", warning.getCreateBy());
    }

    /**
     * 预警必须是未处理状态。生成即已处理的预警不会出现在待处理列表里，
     * 等于这条提醒从未发出过。
     */
    @Test
    void theWarningIsCreatedAsUnhandled() {
        service.record(HOUSE_ID, MOTHER_ID, 6, 0, new Date(), true, null, "op");

        ArgumentCaptor<RabbitAbnormalCondition> captor =
            ArgumentCaptor.forClass(RabbitAbnormalCondition.class);
        verify(rabbitAbnormalConditionMapper).insert(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getIsDeal());
    }

    /** 失败产同样要记绩效：漏记会让繁殖成绩统计只算成功那部分，偏高。 */
    @Test
    void aFailedDeliveryStillCountsTowardsPerformance() {
        Date birthDate = new Date(1_700_000_000_000L);

        service.record(HOUSE_ID, MOTHER_ID, 6, 0, birthDate, true, null, "op");

        verify(performanceRecorder).recordParturition(HOUSE_ID, MOTHER_ID, 6, 0, birthDate);
    }

    /** 有活仔就不是失败产，即使死了一部分也不该挂流产预警。 */
    @Test
    void aPartialLossWithSurvivorsRaisesNoWarning() {
        service.record(HOUSE_ID, MOTHER_ID, 10, 3, new Date(), false, "死了7只", "op");

        verifyNoInteractions(rabbitAbnormalConditionMapper);
    }
}
