package com.rabbit.app.modules.house.mapper;

import com.rabbit.app.modules.house.entity.HouseInvitation;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HouseInvitationMapper {
    HouseInvitation selectByRequestIdForUpdate(@Param("houseId") Long houseId,
                                               @Param("invitedByUserId") Long invitedByUserId,
                                               @Param("requestId") String requestId);

    List<HouseInvitation> selectPendingByPhoneForUpdate(@Param("phoneHash") String phoneHash,
                                                        @Param("now") Date now);

    int insertOrKeepExisting(HouseInvitation invitation);

    int markAcceptedByHouseAndPhone(@Param("houseId") Long houseId,
                                    @Param("phoneHash") String phoneHash,
                                    @Param("acceptedUserId") Long acceptedUserId,
                                    @Param("acceptedTime") Date acceptedTime);
}
