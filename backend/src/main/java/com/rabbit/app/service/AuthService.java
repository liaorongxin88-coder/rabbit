package com.rabbit.app.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.dto.AuthTokenResponse;
import com.rabbit.app.mapper.SysUserMapper;
import com.rabbit.app.model.SysUser;
import com.rabbit.app.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthTokenResponse register(String userName, String password) {
        SysUser exist = sysUserMapper.selectByUserName(userName);
        if (exist != null) {
            throw new BizException(400, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUserName(userName);
        u.setPassword(passwordEncoder.encode(password));
        sysUserMapper.insert(u);
        String token = jwtUtil.generateToken(u.getUserId());
        return new AuthTokenResponse(token, u.getUserId(), u.getUserName());
    }

    public AuthTokenResponse login(String userName, String password) {
        SysUser u = sysUserMapper.selectByUserName(userName);
        if (u == null || !passwordEncoder.matches(password, u.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(u.getUserId());
        return new AuthTokenResponse(token, u.getUserId(), u.getUserName());
    }

    public AuthTokenResponse wechatLogin(String openid) {
        if (openid == null) {
            throw new BizException(400, "openid不能为空");
        }
        String t = openid.trim();
        if (t.isEmpty() || t.length() > 128) {
            throw new BizException(400, "openid不合法");
        }
        String userName = "wx_" + t;
        SysUser u = sysUserMapper.selectByUserName(userName);
        if (u == null) {
            SysUser x = new SysUser();
            x.setUserName(userName);
            x.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            sysUserMapper.insert(x);
            u = x;
        }
        String token = jwtUtil.generateToken(u.getUserId());
        return new AuthTokenResponse(token, u.getUserId(), u.getUserName());
    }
}
