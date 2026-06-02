package com.rabbit.app.mapper;

import com.rabbit.app.model.RequestDedup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RequestDedupMapper {
    RequestDedup selectByKey(@Param("houseId") Long houseId,
                             @Param("userId") Long userId,
                             @Param("api") String api,
                             @Param("requestId") String requestId);

    int insert(@Param("item") RequestDedup item);

    int updateStatus(@Param("houseId") Long houseId,
                     @Param("userId") Long userId,
                     @Param("api") String api,
                     @Param("requestId") String requestId,
                     @Param("status") String status,
                     @Param("errorMessage") String errorMessage);
}

