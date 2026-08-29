package com.rabbit.app.modules.operation.mapper;

import com.rabbit.app.modules.repro.entity.ReproEvent;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 操作事件流的只读口。
 *
 * <p>和 ReproEventMapper 分开：那个负责写入与繁育视角的固定查询，这里只服务
 * 通用读接口。写口是 append-only 的，读口加过滤条件的频率高得多，混在一起
 * 迟早互相牵制。
 */
@Mapper
public interface OperationEventMapper {

    /**
     * keyset 翻页：按 (occurred_at desc, id desc) 取一页。
     *
     * <p>调用方传 limit + 1 条来判断还有没有下一页，省掉一次 count。
     */
    List<ReproEvent> selectPage(
        @Param("houseId") Long houseId,
        @Param("targetType") String targetType,
        @Param("targetId") Long targetId,
        @Param("operationCode") String operationCode,
        @Param("cageId") Long cageId,
        @Param("batchId") Long batchId,
        @Param("occurredFrom") Date occurredFrom,
        @Param("occurredTo") Date occurredTo,
        @Param("cursorOccurredAt") Date cursorOccurredAt,
        @Param("cursorId") Long cursorId,
        @Param("limit") int limit
    );
}
