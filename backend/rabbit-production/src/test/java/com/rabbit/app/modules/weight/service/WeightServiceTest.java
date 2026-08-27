package com.rabbit.app.modules.weight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.modules.weight.entity.WeightLog;
import com.rabbit.app.modules.weight.mapper.WeightLogMapper;
import com.rabbit.app.tracking.OperationContext;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * 称重录入。
 *
 * <p>一次称重写两处：weight_logs 留下历史曲线，rabbits.weight 是当前体重快照。
 * 快照是出栏资格判定的输入之一——体重够不够卖，看的就是它。所以这里的错误
 * 不只是记录不准，还会让一只没长到的兔子被判定为可出栏。
 *
 * <p>用例守三件事。<b>体重合法性</b>：0 或负数会把快照打成非法值，之后所有
 * 依赖体重的判断都基于它；而这层校验必须发生在查兔子之前，否则一个明显
 * 非法的入参还要先打一次库。<b>在场校验</b>：给已出栏的兔子记体重等于往
 * 历史里塞一条不可能发生的数据。<b>历史与快照一致</b>：写进日志的那个数
 * 必须和刷进快照的是同一个，两者分叉后曲线和现值就永远对不上。
 */
class WeightServiceTest {
    private static final Long USER_ID = 9L;
    private static final Long HOUSE_ID = 1L;
    private static final Long RABBIT_ID = 7L;
    private static final String REQ = "req-1";

    private RabbitMapper rabbitMapper;
    private WeightLogMapper weightLogMapper;
    private WeightService service;

    @BeforeEach
    void setUp() {
        rabbitMapper = mock(RabbitMapper.class);
        weightLogMapper = mock(WeightLogMapper.class);
        service = new WeightService(rabbitMapper, weightLogMapper);
    }

    /** 跟踪上下文是 ThreadLocal，不清会漏给同线程的下一个用例。 */
    @AfterEach
    void tearDown() {
        OperationContext.clear();
    }

    // ---------- 幂等回放 ----------

    /**
     * 回放要取回原来那一行。重复插入会在体重曲线上留下两个同刻的点，
     * 日增重按相邻两点算，多出来的那个点会让当天的增重变成 0。
     */
    @Test
    void aReplayedWeighInReturnsTheStoredLog() {
        bindReplay();
        WeightLog stored = new WeightLog();
        when(weightLogMapper.selectByReq(HOUSE_ID, RABBIT_ID, REQ)).thenReturn(stored);

        assertSame(stored, service.create(USER_ID, HOUSE_ID, log(2.4), REQ));

        verify(weightLogMapper, never()).insert(any());
        verifyNoInteractions(rabbitMapper);
    }

    @Test
    void aReplayWithNoStoredLogEchoesTheRequest() {
        bindReplay();
        WeightLog submitted = log(2.4);
        when(weightLogMapper.selectByReq(HOUSE_ID, RABBIT_ID, REQ)).thenReturn(null);

        assertSame(submitted, service.create(USER_ID, HOUSE_ID, submitted, REQ));
        verify(weightLogMapper, never()).insert(any());
    }

    // ---------- 入参 ----------

    @Test
    void anEmptyOrUnaddressedLogIsRejected() {
        assertEquals("称重记录不能为空", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, null, REQ)).getMessage());

        WeightLog noRabbit = log(2.4);
        noRabbit.setRabbitId(null);
        assertEquals("rabbitId不合法", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, noRabbit, REQ)).getMessage());

        WeightLog badRabbit = log(2.4);
        badRabbit.setRabbitId(-1L);
        assertEquals("rabbitId不合法", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, badRabbit, REQ)).getMessage());
    }

    /**
     * 0 或负体重必须挡住，而且要在查兔子之前就挡住。放进去会把 rabbits.weight
     * 刷成一个不可能的值，出栏资格随即按这个值判定。
     */
    @Test
    void aNonPositiveWeightIsRejectedBeforeAnythingIsRead() {
        WeightLog zero = log(0.0);
        assertEquals("weightKg不合法", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, zero, REQ)).getMessage());

        WeightLog negative = log(-1.5);
        assertEquals("weightKg不合法", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, negative, REQ)).getMessage());

        WeightLog missing = log(null);
        assertEquals("weightKg不合法", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, missing, REQ)).getMessage());

        verifyNoInteractions(rabbitMapper, weightLogMapper);
    }

    // ---------- 在场校验 ----------

    @Test
    void weighingAnUnknownRabbitIsRejected() {
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, log(2.4), REQ));
        assertEquals(400, error.getCode());
        assertEquals("兔子不存在", error.getMessage());
        verifyNoInteractions(weightLogMapper);
    }

    @Test
    void aRabbitFromAnotherHouseIsRejected() {
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(rabbit(99L, true));

        assertEquals("兔子不存在", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, log(2.4), REQ)).getMessage());
        verify(rabbitMapper, never()).updateWeight(anyLong(), anyLong(), anyDouble(), anyString());
    }

    /**
     * 已出栏的兔不能再称。它的体重快照是出栏当时的凭证，改掉就把销售单
     * 对应的重量依据一起改了。
     */
    @Test
    void aRabbitThatHasLeftCannotBeWeighed() {
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(rabbit(HOUSE_ID, false));

        assertEquals("兔子不在场", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, log(2.4), REQ)).getMessage());

        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(rabbit(HOUSE_ID, null));
        assertEquals("兔子不在场", assertThrows(BizException.class,
                () -> service.create(USER_ID, HOUSE_ID, log(2.4), REQ)).getMessage());

        verifyNoInteractions(weightLogMapper);
    }

    // ---------- 落库 ----------

    /**
     * 日志里的重量和刷进兔只快照的重量必须是同一个数，且历史先落、快照后刷。
     * 两者分叉的话，体重曲线的最后一点和列表页显示的现值会对不上，
     * 而没人分得清哪个是对的。
     */
    @Test
    void theLoggedWeightIsTheSameOnePushedOntoTheRabbitSnapshot() {
        stubActiveRabbit();

        service.create(USER_ID, HOUSE_ID, log(2.45), REQ);

        ArgumentCaptor<WeightLog> saved = ArgumentCaptor.forClass(WeightLog.class);
        InOrder order = inOrder(weightLogMapper, rabbitMapper);
        order.verify(weightLogMapper).insert(saved.capture());
        order.verify(rabbitMapper).updateWeight(HOUSE_ID, RABBIT_ID, 2.45, "9");
        assertEquals(2.45, saved.getValue().getWeightKg());
    }

    @Test
    void theLogIsScopedToTheHouseAndTheRequest() {
        stubActiveRabbit();
        WeightLog submitted = log(2.45);

        WeightLog saved = service.create(USER_ID, HOUSE_ID, submitted, REQ);

        assertSame(submitted, saved);
        assertEquals(HOUSE_ID, saved.getHouseId());
        assertEquals(REQ, saved.getRequestId());
    }

    /**
     * 不传称重时间就补当下。留空会让这条记录在按时间排序的曲线里位置不定，
     * 日增重也就算不出来。
     */
    @Test
    void anAbsentWeighTimeFallsBackToNow() {
        stubActiveRabbit();
        WeightLog submitted = log(2.45);
        submitted.setWeighTime(null);

        assertNotNull(service.create(USER_ID, HOUSE_ID, submitted, REQ).getWeighTime());
    }

    @Test
    void anExplicitWeighTimeIsKept() {
        stubActiveRabbit();
        Date weighTime = new Date(1_700_000_000_000L);
        WeightLog submitted = log(2.45);
        submitted.setWeighTime(weighTime);

        assertEquals(weighTime, service.create(USER_ID, HOUSE_ID, submitted, REQ).getWeighTime());
    }

    // ---------- 查询 ----------

    @Test
    void historyLimitsAreClamped() {
        service.listByRabbit(HOUSE_ID, RABBIT_ID, 0);
        verify(weightLogMapper).selectByRabbit(HOUSE_ID, RABBIT_ID, 50);

        service.listByRabbit(HOUSE_ID, RABBIT_ID, 9999);
        verify(weightLogMapper).selectByRabbit(HOUSE_ID, RABBIT_ID, 200);
    }

    // ---------- 夹具 ----------

    private void bindReplay() {
        OperationContext context = OperationContext.bind();
        context.setDedupReplay(true);
    }

    private void stubActiveRabbit() {
        when(rabbitMapper.selectById(HOUSE_ID, RABBIT_ID)).thenReturn(rabbit(HOUSE_ID, true));
    }

    private Rabbit rabbit(Long houseId, Boolean active) {
        Rabbit r = new Rabbit();
        r.setId(RABBIT_ID);
        r.setHouseId(houseId);
        r.setIsActive(active);
        return r;
    }

    private WeightLog log(Double weightKg) {
        WeightLog r = new WeightLog();
        r.setRabbitId(RABBIT_ID);
        r.setWeightKg(weightKg);
        r.setWeighTime(new Date(1_700_000_000_000L));
        return r;
    }
}
