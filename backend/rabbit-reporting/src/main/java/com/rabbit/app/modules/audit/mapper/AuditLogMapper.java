package com.rabbit.app.modules.audit.mapper;

import com.rabbit.app.modules.audit.entity.AuditLog;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {
    int insert(@Param("item") AuditLog item);

    List<AuditLog> selectPage(@Param("houseId") Long houseId,
                              @Param("userId") Long userId,
                              @Param("path") String path,
                              @Param("status") Integer status,
                              @Param("from") Date from,
                              @Param("to") Date to,
                              @Param("offset") int offset,
                              @Param("limit") int limit);
}
