package com.rabbit.app.modules.auth.mapper;

import com.rabbit.app.modules.auth.entity.PhoneOneTapAttempt;
import java.util.Date;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PhoneOneTapAttemptMapper {
    int insert(PhoneOneTapAttempt attempt);

    PhoneOneTapAttempt selectByRequestIdForUpdate(@Param("requestId") String requestId);

    PhoneOneTapAttempt selectByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    int replaceLease(@Param("id") Long id,
                     @Param("expectedLeaseId") String expectedLeaseId,
                     @Param("leaseId") String leaseId,
                     @Param("leaseExpiresTime") Date leaseExpiresTime);

    PhoneOneTapAttempt selectOwnedProcessingForUpdate(@Param("id") Long id,
                                                       @Param("leaseId") String leaseId,
                                                       @Param("now") Date now);

    int markSucceeded(@Param("id") Long id,
                      @Param("leaseId") String leaseId,
                      @Param("userId") Long userId,
                      @Param("successTime") Date successTime);

    int markFailed(@Param("id") Long id,
                   @Param("leaseId") String leaseId,
                   @Param("responseCode") int responseCode,
                   @Param("responseMessage") String responseMessage);

    int deleteExpiredAttempts(@Param("cutoff") Date cutoff,
                              @Param("limit") int limit);
}
