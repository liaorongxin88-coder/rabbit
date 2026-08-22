package com.rabbit.app.modules.house.mapper;

import com.rabbit.app.modules.house.dto.HouseMemberItem;
import com.rabbit.app.modules.house.entity.HouseUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HouseUserMapper {
    int insert(HouseUser houseUser);

    HouseUser selectByUserAndHouse(@Param("userId") Long userId, @Param("houseId") Long houseId);

    List<HouseMemberItem> selectMembersByHouse(@Param("houseId") Long houseId);

    int updateMember(@Param("houseId") Long houseId,
                     @Param("userId") Long userId,
                     @Param("role") String role,
                     @Param("status") String status,
                     @Param("perms") String perms,
                     @Param("isAdmin") Boolean isAdmin,
                     @Param("updateBy") String updateBy);

    int deleteMember(@Param("houseId") Long houseId, @Param("userId") Long userId);

    int countAdmins(@Param("houseId") Long houseId);

    int countMembers(@Param("houseId") Long houseId);

    int countEnabledOwners(@Param("houseId") Long houseId);

    List<Long> selectMemberUserIds(@Param("houseId") Long houseId);
}
