package com.rabbit.app.modules.admin.mapper;

import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformAdminMapper {
    PlatformAdmin selectById(@Param("id") Long id);

    PlatformAdmin selectByUserName(@Param("userName") String userName);

    int insert(PlatformAdmin admin);

    int updateLastLoginTime(@Param("id") Long id);
}
