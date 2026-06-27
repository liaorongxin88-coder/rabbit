package com.rabbit.app.modules.auth.mapper;

import com.rabbit.app.modules.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {
    SysUser selectByUserName(@Param("userName") String userName);

    SysUser selectByOpenid(@Param("openid") String openid);

    SysUser selectById(@Param("userId") Long userId);

    int insert(SysUser user);

    int updateOpenid(@Param("userId") Long userId, @Param("openid") String openid);

    int updateUserName(@Param("userId") Long userId, @Param("userName") String userName);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);
}
