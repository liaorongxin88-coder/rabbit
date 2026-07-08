package com.rabbit.app.modules.admin.mapper;

import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformAdminMapper {
    PlatformAdmin selectById(@Param("id") Long id);

    PlatformAdmin selectByUserName(@Param("userName") String userName);

    PlatformAdmin selectByUserNameExceptId(@Param("userName") String userName, @Param("id") Long id);

    List<PlatformAdmin> selectPage(@Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword);

    long countEnabledSuperAdmins();

    int insert(PlatformAdmin admin);

    int update(PlatformAdmin admin);

    int deleteById(@Param("id") Long id);

    int updateLastLoginTime(@Param("id") Long id);
}
