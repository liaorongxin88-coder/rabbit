package com.rabbit.app.modules.admin.mapper;

import com.rabbit.app.modules.audit.entity.AuditLog;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantOverviewMapper {
    long countHouses(@Param("merchantId") Long merchantId);

    long countCages(@Param("merchantId") Long merchantId);

    long countRabbits(@Param("merchantId") Long merchantId);

    List<RabbitHouse> selectHouses(@Param("merchantId") Long merchantId, @Param("limit") int limit);

    List<AuditLog> selectRecentAuditLogs(@Param("merchantId") Long merchantId, @Param("limit") int limit);
}
