package com.rabbit.app.modules.feed.mapper;

import com.rabbit.app.modules.feed.entity.FeedAllocationCandidateRow;
import com.rabbit.app.modules.feed.entity.FeedLogBatchAllocation;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FeedLogBatchAllocationMapper {
    int insertBatch(@Param("allocations") List<FeedLogBatchAllocation> allocations);

    List<FeedAllocationCandidateRow> selectCandidates(
        @Param("houseId") Long houseId,
        @Param("rabbitIds") List<Long> rabbitIds,
        @Param("feedTime") Date feedTime
    );
}
