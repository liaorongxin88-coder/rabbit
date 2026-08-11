package com.rabbit.app.modules.auth.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PhoneOneTapRateBucketMapper {
    int lockOrCreate(@Param("requestIp") String requestIp,
                     @Param("bucketType") String bucketType,
                     @Param("bucketStart") Date bucketStart);

    Integer selectCountForUpdate(@Param("requestIp") String requestIp,
                                 @Param("bucketType") String bucketType,
                                 @Param("bucketStart") Date bucketStart);

    int increment(@Param("requestIp") String requestIp,
                  @Param("bucketType") String bucketType,
                  @Param("bucketStart") Date bucketStart);

    int deleteBefore(@Param("cutoff") Date cutoff,
                     @Param("limit") int limit);
}
