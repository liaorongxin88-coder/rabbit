package com.rabbit.app.modules.admin.mapper;

import com.rabbit.app.modules.admin.dto.AdminFarmItem;
import com.rabbit.app.modules.audit.entity.AuditLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminFarmMapper {
    long count(@Param("keyword") String keyword, @Param("status") String status);

    List<AdminFarmItem> selectPage(@Param("keyword") String keyword,
                                   @Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    AdminFarmItem selectById(@Param("houseId") Long houseId);

    long countOwnerMembershipByUserId(@Param("houseId") Long houseId,
                                      @Param("userId") Long userId);

    long countOwnerMembershipByPhoneHash(@Param("houseId") Long houseId,
                                         @Param("phoneHash") String phoneHash);

    long countBatches(@Param("houseId") Long houseId);

    List<AuditLog> selectRecentAuditLogs(@Param("houseId") Long houseId,
                                         @Param("limit") int limit);
}
