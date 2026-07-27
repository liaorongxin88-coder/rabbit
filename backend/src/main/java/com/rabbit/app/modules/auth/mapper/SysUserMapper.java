package com.rabbit.app.modules.auth.mapper;

import com.rabbit.app.modules.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserMapper {
    SysUser selectByUserName(@Param("userName") String userName);

    SysUser selectByOpenid(@Param("openid") String openid);

    SysUser selectById(@Param("userId") Long userId);

    List<SysUser> searchByMerchant(@Param("merchantId") Long merchantId,
                                   @Param("keyword") String keyword,
                                   @Param("excludeUserIds") List<Long> excludeUserIds,
                                   @Param("limit") int limit);

    int insert(SysUser user);

    int updateOpenid(@Param("userId") Long userId, @Param("openid") String openid);

    int updateUserName(@Param("userId") Long userId, @Param("userName") String userName);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);
}
