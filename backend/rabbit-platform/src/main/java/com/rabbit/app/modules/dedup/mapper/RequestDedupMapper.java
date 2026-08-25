package com.rabbit.app.modules.dedup.mapper;

import com.rabbit.app.modules.dedup.entity.RequestDedup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RequestDedupMapper {
    RequestDedup selectByKey(@Param("houseId") Long houseId,
                             @Param("userId") Long userId,
                             @Param("api") String api,
                             @Param("requestId") String requestId);

    int insert(@Param("item") RequestDedup item);

    int insertIgnore(@Param("item") RequestDedup item);

    int updateStatus(@Param("houseId") Long houseId,
                     @Param("userId") Long userId,
                     @Param("api") String api,
                     @Param("requestId") String requestId,
                     @Param("status") String status,
                     @Param("errorMessage") String errorMessage);

    int updateStatusWithResponse(@Param("houseId") Long houseId,
                                 @Param("userId") Long userId,
                                 @Param("api") String api,
                                 @Param("requestId") String requestId,
                                 @Param("status") String status,
                                 @Param("responsePayload") String responsePayload);
}
