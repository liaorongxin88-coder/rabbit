package com.rabbit.app.modules.auth.mapper;

import com.rabbit.app.modules.auth.entity.SmsVerificationCode;
import java.util.Date;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SmsVerificationCodeMapper {
    int insert(SmsVerificationCode item);

    int countRecentByPhone(@Param("phoneHash") String phoneHash,
                           @Param("purpose") String purpose,
                           @Param("fromTime") Date fromTime);

    int countRecentByIp(@Param("requestIp") String requestIp,
                        @Param("fromTime") Date fromTime);

    SmsVerificationCode selectLatestActiveForUpdate(@Param("phoneHash") String phoneHash,
                                                     @Param("purpose") String purpose,
                                                     @Param("now") Date now);

    int markSent(@Param("id") Long id);

    int markFailed(@Param("id") Long id);

    int recordFailedAttempt(@Param("id") Long id,
                            @Param("maxAttempts") int maxAttempts);

    int markConsumed(@Param("id") Long id,
                     @Param("consumedTime") Date consumedTime);

    int deleteCreatedBefore(@Param("cutoff") Date cutoff,
                            @Param("limit") int limit);
}
