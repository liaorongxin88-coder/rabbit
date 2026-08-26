package com.rabbit.app.modules.batch.mapper;

import com.rabbit.app.modules.batch.entity.Batch;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BatchMapper {
    int insert(Batch batch);

    Batch selectById(@Param("houseId") Long houseId, @Param("id") Long id);

    Batch selectByIdForUpdate(@Param("houseId") Long houseId, @Param("id") Long id);

    List<Batch> selectByHouse(@Param("houseId") Long houseId);

    List<Batch> selectPageByHouse(@Param("houseId") Long houseId,
                                  @Param("q") String q,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    Batch selectByHouseAndRequestId(@Param("houseId") Long houseId, @Param("requestId") String requestId);

    int updateStatusAndDates(@Param("houseId") Long houseId, @Param("id") Long id, @Param("status") String status, @Param("startDate") java.util.Date startDate, @Param("endDate") java.util.Date endDate, @Param("updateBy") String updateBy);

    int updateBatchCode(@Param("houseId") Long houseId, @Param("id") Long id, @Param("batchCode") String batchCode, @Param("updateBy") String updateBy);
}
