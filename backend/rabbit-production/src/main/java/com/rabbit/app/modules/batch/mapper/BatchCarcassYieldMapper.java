package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.entity.BatchCarcassYieldVersion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BatchCarcassYieldMapper {
    int insert(BatchCarcassYieldVersion version);

    BatchCarcassYieldVersion selectByRequestId(
        @Param("houseId") Long houseId,
        @Param("requestId") String requestId
    );

    List<BatchCarcassYieldVersion> selectPage(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    long countByBatch(
        @Param("houseId") Long houseId,
        @Param("batchId") Long batchId
    );
}
