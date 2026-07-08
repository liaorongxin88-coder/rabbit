package com.rabbit.app.modules.admin.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.MerchantAccountItem;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.mapper.MerchantUserMapper;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.security.PlatformAdminContext;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantAccountAdminService {
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final MerchantUserMapper merchantUserMapper;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public MerchantAccountAdminService(
            MerchantUserMapper merchantUserMapper,
            SysUserMapper sysUserMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.merchantUserMapper = merchantUserMapper;
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<MerchantAccountItem> list(String keyword, Integer page, Integer pageSize) {
        ensureSuperAdmin();
        int p = page == null || page.intValue() <= 0 ? 1 : page.intValue();
        int ps = pageSize == null || pageSize.intValue() <= 0 ? 20 : pageSize.intValue();
        if (ps > 100) {
            ps = 100;
        }
        String normalizedKeyword = trim(keyword);
        int offset = (p - 1) * ps;
        long total = merchantUserMapper.countMerchantAccounts(normalizedKeyword);
        List<MerchantAccountItem> items = merchantUserMapper.selectMerchantAccounts(normalizedKeyword, offset, ps);
        return new PageResult<MerchantAccountItem>(items, total, p, ps);
    }

    @Transactional
    public MerchantAccountItem update(Long userId, String userName, String password, String confirmPassword) {
        ensureSuperAdmin();
        SysUser user = getExistingMerchantUser(userId);
        String normalizedUserName = normalizeUserName(userName);
        ensureUserNameAvailable(normalizedUserName, user.getUserId());

        int nameUpdated = sysUserMapper.updateUserName(user.getUserId(), normalizedUserName);
        if (nameUpdated <= 0) {
            throw new BizException(404, "商户账号不存在");
        }

        String normalizedPassword = normalizeOptionalPassword(password, confirmPassword);
        if (normalizedPassword != null) {
            int passwordUpdated = sysUserMapper.updatePassword(user.getUserId(), passwordEncoder.encode(normalizedPassword));
            if (passwordUpdated <= 0) {
                throw new BizException(404, "商户账号不存在");
            }
        }

        return merchantUserMapper.selectMerchantAccountByUserId(user.getUserId());
    }

    private SysUser getExistingMerchantUser(Long userId) {
        if (userId == null || userId.longValue() <= 0) {
            throw new BizException(400, "用户ID不能为空");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || merchantUserMapper.countUserBindings(userId) <= 0) {
            throw new BizException(404, "商户账号不存在");
        }
        return user;
    }

    private void ensureUserNameAvailable(String userName, Long userId) {
        SysUser exists = sysUserMapper.selectByUserName(userName);
        if (exists != null && !exists.getUserId().equals(userId)) {
            throw new BizException(400, "用户名已存在");
        }
    }

    private String normalizeUserName(String userName) {
        String value = trim(userName);
        if (value == null || value.isEmpty()) {
            throw new BizException(400, "用户名不能为空");
        }
        return value;
    }

    private String normalizeOptionalPassword(String password, String confirmPassword) {
        String value = trim(password);
        String confirm = trim(confirmPassword);
        if (value == null || value.isEmpty()) {
            if (confirm != null && !confirm.isEmpty()) {
                throw new BizException(400, "请输入新密码");
            }
            return null;
        }
        if (value.length() < 6 || value.length() > 64) {
            throw new BizException(400, "密码长度需为6-64个字符");
        }
        if (!value.equals(confirm)) {
            throw new BizException(400, "两次输入的密码不一致");
        }
        return value;
    }

    private void ensureSuperAdmin() {
        if (!ROLE_SUPER_ADMIN.equals(PlatformAdminContext.getRole())) {
            throw new BizException(403, "仅超级管理员可管理商户账号");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
