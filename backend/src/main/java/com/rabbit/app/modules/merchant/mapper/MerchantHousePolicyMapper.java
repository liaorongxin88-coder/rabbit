package com.rabbit.app.modules.merchant.mapper;

import com.rabbit.app.modules.merchant.entity.MerchantHousePolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantHousePolicyMapper {
    MerchantHousePolicy selectByMerchantId(@Param("merchantId") Long merchantId);

    int insertDefault(@Param("merchantId") Long merchantId, @Param("operator") String operator);

    int update(MerchantHousePolicy policy);
}
