package com.rabbit.app.mapper;

import com.rabbit.app.model.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

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
