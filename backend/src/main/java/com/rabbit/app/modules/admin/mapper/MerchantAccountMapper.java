package com.rabbit.app.modules.admin.mapper;

import com.rabbit.app.modules.admin.dto.MerchantAccountItem;
import com.rabbit.app.modules.admin.dto.MerchantAccountSummary;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantAccountMapper {
    List<MerchantAccountSummary> selectByMerchantId(@Param("merchantId") Long merchantId);

    long countByMerchantId(@Param("merchantId") Long merchantId);

    long countAccounts(@Param("keyword") String keyword);

    List<MerchantAccountItem> selectAccounts(@Param("keyword") String keyword,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    MerchantAccountItem selectAccountByUserId(@Param("userId") Long userId);
}
