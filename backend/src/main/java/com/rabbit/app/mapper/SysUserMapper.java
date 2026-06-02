package com.rabbit.app.mapper;

import com.rabbit.app.model.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {
    SysUser selectByUserName(@Param("userName") String userName);

    SysUser selectById(@Param("userId") Long userId);

    int insert(SysUser user);
}
