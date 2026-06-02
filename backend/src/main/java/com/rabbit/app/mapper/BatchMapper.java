package com.rabbit.app.mapper;

import com.rabbit.app.model.Batch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BatchMapper {
    int insert(Batch batch);

    Batch selectById(@Param("houseId") Long houseId, @Param("id") Long id);

    List<Batch> selectByHouse(@Param("houseId") Long houseId);

    List<Batch> selectPageByHouse(@Param("houseId") Long houseId,
                                  @Param("q") String q,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    Batch selectByHouseAndRequestId(@Param("houseId") Long houseId, @Param("requestId") String requestId);

    int updateStatusAndDates(@Param("houseId") Long houseId, @Param("id") Long id, @Param("status") String status, @Param("startDate") java.util.Date startDate, @Param("endDate") java.util.Date endDate, @Param("updateBy") String updateBy);
}
