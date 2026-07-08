package com.rabbit.app.modules.admin.mapper;

import com.rabbit.app.modules.admin.dto.MerchantUserItem;
import com.rabbit.app.modules.admin.dto.MerchantAccountItem;
import com.rabbit.app.modules.admin.entity.MerchantUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantUserMapper {
    int insert(MerchantUser merchantUser);

    int insertIgnore(MerchantUser merchantUser);

    int delete(@Param("merchantId") Long merchantId, @Param("userId") Long userId);

    MerchantUser selectByMerchantAndUser(@Param("merchantId") Long merchantId, @Param("userId") Long userId);

    Long selectSingleMerchantIdByUser(@Param("userId") Long userId);

    List<MerchantUserItem> selectUsersByMerchant(@Param("merchantId") Long merchantId);

    long countUsersByMerchant(@Param("merchantId") Long merchantId);

    long countUserBindings(@Param("userId") Long userId);

    long countMerchantAccounts(@Param("keyword") String keyword);

    List<MerchantAccountItem> selectMerchantAccounts(@Param("keyword") String keyword,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    MerchantAccountItem selectMerchantAccountByUserId(@Param("userId") Long userId);
}
