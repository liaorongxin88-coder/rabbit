package com.rabbit.app.modules.admin.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.MerchantOverview;
import com.rabbit.app.modules.admin.dto.MerchantAccountSummary;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.entity.Merchant;
import com.rabbit.app.modules.admin.mapper.MerchantAccountMapper;
import com.rabbit.app.modules.admin.mapper.MerchantMapper;
import com.rabbit.app.modules.admin.mapper.MerchantOverviewMapper;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.security.PlatformAdminContext;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantAdminService {
    private final MerchantMapper merchantMapper;
    private final MerchantAccountMapper merchantAccountMapper;
    private final MerchantOverviewMapper merchantOverviewMapper;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public MerchantAdminService(
            MerchantMapper merchantMapper,
            MerchantAccountMapper merchantAccountMapper,
            MerchantOverviewMapper merchantOverviewMapper,
            SysUserMapper sysUserMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.merchantMapper = merchantMapper;
        this.merchantAccountMapper = merchantAccountMapper;
        this.merchantOverviewMapper = merchantOverviewMapper;
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<Merchant> list(String keyword, String status, Integer page, Integer pageSize) {
        int p = page == null || page.intValue() <= 0 ? 1 : page.intValue();
        int ps = pageSize == null || pageSize.intValue() <= 0 ? 20 : pageSize.intValue();
        if (ps > 100) {
            ps = 100;
        }
        String normalizedStatus = normalizeOptionalStatus(status);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        int offset = (p - 1) * ps;
        long total = merchantMapper.countPage(normalizedKeyword, normalizedStatus);
        List<Merchant> items = merchantMapper.selectPage(normalizedKeyword, normalizedStatus, offset, ps);
        return new PageResult<Merchant>(items, total, p, ps);
    }

    public Merchant get(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(404, "商户不存在");
        }
        return merchant;
    }

    @Transactional
    public Merchant create(
            String name,
            String contactName,
            String contactPhone,
            String remark,
            String userName,
            String password,
            String confirmPassword
    ) {
        Merchant merchant = new Merchant();
        merchant.setName(trim(name));
        merchant.setContactName(trim(contactName));
        merchant.setContactPhone(trim(contactPhone));
        merchant.setRemark(trim(remark));
        merchant.setStatus("ENABLED");
        merchant.setCreateBy(operator());
        merchant.setUpdateBy(operator());
        merchantMapper.insert(merchant);

        createBusinessUser(merchant.getId(), userName, password, confirmPassword);
        return merchantMapper.selectById(merchant.getId());
    }

    public Merchant update(Long merchantId, String name, String contactName, String contactPhone, String remark) {
        ensureExists(merchantId);
        int n = merchantMapper.updateBasic(merchantId, trim(name), trim(contactName), trim(contactPhone), trim(remark), operator());
        if (n <= 0) {
            throw new BizException(404, "商户不存在");
        }
        return merchantMapper.selectById(merchantId);
    }

    public Merchant updateStatus(Long merchantId, String status) {
        ensureExists(merchantId);
        String s = normalizeRequiredStatus(status);
        int n = merchantMapper.updateStatus(merchantId, s, operator());
        if (n <= 0) {
            throw new BizException(404, "商户不存在");
        }
        return merchantMapper.selectById(merchantId);
    }

    public List<MerchantAccountSummary> listAccounts(Long merchantId) {
        ensureExists(merchantId);
        return merchantAccountMapper.selectByMerchantId(merchantId);
    }

    @Transactional
    public void createAccount(Long merchantId, String userName, String password, String confirmPassword) {
        ensureExists(merchantId);
        createBusinessUser(merchantId, userName, password, confirmPassword);
    }

    public MerchantOverview overview(Long merchantId) {
        ensureExists(merchantId);
        MerchantOverview overview = new MerchantOverview();
        overview.setHouseCount(merchantOverviewMapper.countHouses(merchantId));
        overview.setUserCount(merchantAccountMapper.countByMerchantId(merchantId));
        overview.setCageCount(merchantOverviewMapper.countCages(merchantId));
        overview.setRabbitCount(merchantOverviewMapper.countRabbits(merchantId));
        overview.setHouses(merchantOverviewMapper.selectHouses(merchantId, 10));
        overview.setRecentAuditLogs(merchantOverviewMapper.selectRecentAuditLogs(merchantId, 10));
        return overview;
    }

    private void ensureExists(Long merchantId) {
        if (merchantId == null || merchantId.longValue() <= 0) {
            throw new BizException(400, "merchantId不能为空");
        }
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(404, "商户不存在");
        }
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        return normalizeRequiredStatus(status);
    }

    private String normalizeRequiredStatus(String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!"ENABLED".equals(s) && !"DISABLED".equals(s)) {
            throw new BizException(400, "状态不合法");
        }
        return s;
    }

    private String operator() {
        Long adminId = PlatformAdminContext.getAdminId();
        return adminId == null ? "platform" : String.valueOf(adminId);
    }

    private String normalizeUserName(String userName) {
        String value = trim(userName);
        if (value == null || value.isEmpty()) {
            throw new BizException(400, "登录用户名不能为空");
        }
        if (value.length() > 64) {
            throw new BizException(400, "登录用户名不能超过64个字符");
        }
        return value;
    }

    private SysUser createBusinessUser(Long merchantId, String userName, String password, String confirmPassword) {
        String normalizedUserName = normalizeUserName(userName);
        String normalizedPassword = normalizePassword(password, confirmPassword);
        if (sysUserMapper.selectByUserName(normalizedUserName) != null) {
            throw new BizException(400, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setMerchantId(merchantId);
        user.setUserName(normalizedUserName);
        user.setPassword(passwordEncoder.encode(normalizedPassword));
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BizException(400, "用户名已存在");
        }
        return user;
    }

    private String normalizePassword(String password, String confirmPassword) {
        if (password == null || password.length() < 6 || password.length() > 64) {
            throw new BizException(400, "初始密码长度需为6-64个字符");
        }
        if (!password.equals(confirmPassword)) {
            throw new BizException(400, "两次输入的密码不一致");
        }
        return password;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
