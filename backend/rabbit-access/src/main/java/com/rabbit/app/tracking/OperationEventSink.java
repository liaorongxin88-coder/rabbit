package com.rabbit.app.tracking;

import java.util.List;

/**
 * 事件落库出口。
 *
 * <p>本阶段刻意<b>不建表</b>：事件表扩列（{@code repro_events} 加
 * {@code cage_id} 与 {@code target_type/target_id}）属于 T4 的 V46，
 * 迁移版本号已预分配，提前建表会和后续迁移撞车。所以 T1 只定契约，
 * 让基座可以先跑通、先被测试钉死。
 *
 * <p><b>方法签名只有批量版</b>，没有 {@code append(OperationEvent)}。这是刻意的：
 * 单次可处理 500 只兔，一旦存在单条 API，调用方几乎必然写成循环里逐条插入。
 * 不给单条入口，逐条插入就写不出来。
 *
 * <p>实现方约定：本方法在<b>业务事务内</b>被调用，因此不要自己开事务、
 * 不要吞异常——抛出即回滚，事件与业务数据同生共死。
 */
public interface OperationEventSink {

    /**
     * @param events 非空、非 null 元素的事件列表，按业务发生顺序排列
     */
    void append(List<OperationEvent> events);
}
