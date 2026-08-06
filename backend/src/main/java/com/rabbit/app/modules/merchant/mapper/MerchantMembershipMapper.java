package com.rabbit.app.modules.merchant.mapper;

import com.rabbit.app.modules.merchant.dto.MerchantMemberItem;
import com.rabbit.app.modules.merchant.dto.MerchantMembershipView;
import com.rabbit.app.modules.merchant.entity.MerchantMembership;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantMembershipMapper {
    MerchantMembership selectByUserAndMerchant(@Param("userId") Long userId,
                                                @Param("merchantId") Long merchantId);

    List<MerchantMembershipView> selectByUser(@Param("userId") Long userId);

    List<MerchantMemberItem> selectMembers(@Param("merchantId") Long merchantId);

    int insert(MerchantMembership membership);

    int updateRoleAndStatus(@Param("merchantId") Long merchantId,
                            @Param("userId") Long userId,
                            @Param("role") String role,
                            @Param("status") String status,
                            @Param("updateBy") String updateBy);

    int demoteOtherOwners(@Param("merchantId") Long merchantId,
                          @Param("exceptUserId") Long exceptUserId,
                          @Param("updateBy") String updateBy);

    int delete(@Param("merchantId") Long merchantId, @Param("userId") Long userId);

    int countEnabled(@Param("merchantId") Long merchantId);
}
