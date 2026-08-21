package com.rabbit.app.modules.repro.mapper;

import com.rabbit.app.modules.repro.entity.ReproEvent;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * repro_events 的读写口。append-only：刻意没有 update / delete。
 *
 * <p>纠错靠追加补偿事件，而不是改历史——否则「可重放」这条设计承诺就不成立了。
 */
@Mapper
public interface ReproEventMapper {

    int insert(ReproEvent event);

    /**
     * 幂等回查：uk_re_request 冲突时用它取回首次写入的事件，把重复提交变成「返回上次结果」。
     */
    ReproEvent selectByRequestId(
        @Param("houseId") Long houseId,
        @Param("requestId") String requestId
    );

    /** 单周期完整事件流，供追溯页按时间正序展示。 */
    List<ReproEvent> selectByCycle(@Param("houseId") Long houseId, @Param("cycleId") Long cycleId);

    /** 单只母兔近期事件，跨周期，倒序。 */
    List<ReproEvent> selectByMother(
        @Param("houseId") Long houseId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("limit") int limit
    );

    /** 一个批次标签下的近期生产操作，倒序。 */
    List<ReproEvent> selectByBatchAndMother(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("motherRabbitId") Long motherRabbitId,
        @Param("limit") int limit
    );
}
