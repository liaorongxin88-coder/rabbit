package com.rabbit.app.modules.admin.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.AdminAccountItem;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import com.rabbit.app.security.PlatformAdminContext;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminAccountService {
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN = "ADMIN";

    private final PlatformAdminMapper platformAdminMapper;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminAccountService(PlatformAdminMapper platformAdminMapper, PasswordEncoder passwordEncoder) {
        this.platformAdminMapper = platformAdminMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<AdminAccountItem> list(String keyword, Integer page, Integer pageSize) {
        ensureSuperAdmin();
        int p = page == null || page.intValue() <= 0 ? 1 : page.intValue();
        int ps = pageSize == null || pageSize.intValue() <= 0 ? 20 : pageSize.intValue();
        if (ps > 100) {
            ps = 100;
        }
        String normalizedKeyword = trim(keyword);
        int offset = (p - 1) * ps;
        long total = platformAdminMapper.countPage(normalizedKeyword);
        List<AdminAccountItem> items = platformAdminMapper.selectPage(normalizedKeyword, offset, ps)
                .stream()
                .map(this::toItem)
                .toList();
        return new PageResult<AdminAccountItem>(items, total, p, ps);
    }

    public AdminAccountItem get(Long id) {
        ensureSuperAdmin();
        return toItem(getExisting(id));
    }

    @Transactional
    public AdminAccountItem create(String userName, String password, String role, Boolean enabled) {
        ensureSuperAdmin();
        String normalizedUserName = normalizeUserName(userName);
        ensureUserNameAvailable(normalizedUserName, null);

        PlatformAdmin admin = new PlatformAdmin();
        admin.setUserName(normalizedUserName);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(normalizeRole(role));
        admin.setEnabled(enabled == null ? Boolean.TRUE : enabled);
        platformAdminMapper.insert(admin);
        return toItem(platformAdminMapper.selectById(admin.getId()));
    }

    @Transactional
    public AdminAccountItem update(Long id, String userName, String password, String role, Boolean enabled) {
        ensureSuperAdmin();
        PlatformAdmin existing = getExisting(id);
        String normalizedUserName = normalizeUserName(userName);
        String normalizedRole = normalizeRole(role);
        Boolean normalizedEnabled = enabled == null ? Boolean.TRUE : enabled;

        ensureUserNameAvailable(normalizedUserName, id);
        ensureCanChange(existing, normalizedRole, normalizedEnabled);

        PlatformAdmin admin = new PlatformAdmin();
        admin.setId(id);
        admin.setUserName(normalizedUserName);
        admin.setPassword(normalizeOptionalPassword(password));
        admin.setRole(normalizedRole);
        admin.setEnabled(normalizedEnabled);
        int n = platformAdminMapper.update(admin);
        if (n <= 0) {
            throw new BizException(404, "管理员账号不存在");
        }
        return toItem(platformAdminMapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        ensureSuperAdmin();
        PlatformAdmin existing = getExisting(id);
        Long currentAdminId = PlatformAdminContext.getAdminId();
        if (existing.getId().equals(currentAdminId)) {
            throw new BizException(400, "不能删除当前登录账号");
        }
        if (isEnabledSuperAdmin(existing) && platformAdminMapper.countEnabledSuperAdmins() <= 1) {
            throw new BizException(400, "至少保留一个启用的超级管理员");
        }
        int n = platformAdminMapper.deleteById(id);
        if (n <= 0) {
            throw new BizException(404, "管理员账号不存在");
        }
    }

    private void ensureCanChange(PlatformAdmin existing, String nextRole, Boolean nextEnabled) {
        Long currentAdminId = PlatformAdminContext.getAdminId();
        if (existing.getId().equals(currentAdminId) && (!ROLE_SUPER_ADMIN.equals(nextRole) || !nextEnabled)) {
            throw new BizException(400, "不能停用当前账号或降低当前账号权限");
        }
        if (isEnabledSuperAdmin(existing) && (!ROLE_SUPER_ADMIN.equals(nextRole) || !nextEnabled)
                && platformAdminMapper.countEnabledSuperAdmins() <= 1) {
            throw new BizException(400, "至少保留一个启用的超级管理员");
        }
    }

    private void ensureSuperAdmin() {
        if (!ROLE_SUPER_ADMIN.equals(PlatformAdminContext.getRole())) {
            throw new BizException(403, "仅超级管理员可管理平台账号");
        }
    }

    private PlatformAdmin getExisting(Long id) {
        if (id == null || id.longValue() <= 0) {
            throw new BizException(400, "账号ID不能为空");
        }
        PlatformAdmin admin = platformAdminMapper.selectById(id);
        if (admin == null) {
            throw new BizException(404, "管理员账号不存在");
        }
        return admin;
    }

    private void ensureUserNameAvailable(String userName, Long excludeId) {
        PlatformAdmin exists = excludeId == null
                ? platformAdminMapper.selectByUserName(userName)
                : platformAdminMapper.selectByUserNameExceptId(userName, excludeId);
        if (exists != null) {
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

    private String normalizeOptionalPassword(String password) {
        String value = trim(password);
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() < 6 || value.length() > 64) {
            throw new BizException(400, "密码长度需为6-64个字符");
        }
        return passwordEncoder.encode(value);
    }

    private String normalizeRole(String role) {
        String value = role == null ? "" : role.trim().toUpperCase();
        if (!ROLE_SUPER_ADMIN.equals(value) && !ROLE_ADMIN.equals(value)) {
            throw new BizException(400, "角色不合法");
        }
        return value;
    }

    private boolean isEnabledSuperAdmin(PlatformAdmin admin) {
        return ROLE_SUPER_ADMIN.equals(admin.getRole()) && Boolean.TRUE.equals(admin.getEnabled());
    }

    private AdminAccountItem toItem(PlatformAdmin admin) {
        AdminAccountItem item = new AdminAccountItem();
        item.setId(admin.getId());
        item.setUserName(admin.getUserName());
        item.setRole(admin.getRole());
        item.setEnabled(admin.getEnabled());
        item.setLastLoginTime(admin.getLastLoginTime());
        item.setCreateTime(admin.getCreateTime());
        item.setUpdateTime(admin.getUpdateTime());
        return item;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
