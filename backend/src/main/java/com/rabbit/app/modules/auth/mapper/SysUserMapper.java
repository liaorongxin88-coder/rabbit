package com.rabbit.app.modules.auth.mapper;

import com.rabbit.app.modules.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {
    SysUser selectByUserName(@Param("userName") String userName);

    SysUser selectByOpenid(@Param("openid") String openid);

    SysUser selectByPhoneHash(@Param("phoneHash") String phoneHash);

    SysUser selectByPhoneHashForUpdate(@Param("phoneHash") String phoneHash);

    SysUser selectById(@Param("userId") Long userId);

    SysUser selectByIdForUpdate(@Param("userId") Long userId);

    int insert(SysUser user);

    int updateOpenid(@Param("userId") Long userId, @Param("openid") String openid);

    int updateUserName(@Param("userId") Long userId, @Param("userName") String userName);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    int updatePasswordAndInitialize(@Param("userId") Long userId,
                                    @Param("password") String password);

    int updatePhone(@Param("userId") Long userId,
                    @Param("phoneCountryCode") String phoneCountryCode,
                    @Param("phoneHash") String phoneHash,
                    @Param("phoneMasked") String phoneMasked);

    int updateStatus(@Param("userId") Long userId,
                     @Param("status") String status);
}
