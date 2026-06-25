package com.rabbit.app.modules.admin.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.MerchantOverview;
import com.rabbit.app.modules.admin.dto.MerchantUserItem;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.entity.Merchant;
import com.rabbit.app.modules.admin.entity.MerchantUser;
import com.rabbit.app.modules.admin.mapper.MerchantMapper;
import com.rabbit.app.modules.admin.mapper.MerchantOverviewMapper;
import com.rabbit.app.modules.admin.mapper.MerchantUserMapper;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.security.PlatformAdminContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantAdminService {
    private final MerchantMapper merchantMapper;
    private final MerchantUserMapper merchantUserMapper;
    private final MerchantOverviewMapper merchantOverviewMapper;
    private final SysUserMapper sysUserMapper;

    public MerchantAdminService(
            MerchantMapper merchantMapper,
            MerchantUserMapper merchantUserMapper,
            MerchantOverviewMapper merchantOverviewMapper,
            SysUserMapper sysUserMapper
    ) {
        this.merchantMapper = merchantMapper;
        this.merchantUserMapper = merchantUserMapper;
        this.merchantOverviewMapper = merchantOverviewMapper;
        this.sysUserMapper = sysUserMapper;
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
    public Merchant create(String name, String contactName, String contactPhone, String remark) {
        Merchant merchant = new Merchant();
        merchant.setName(trim(name));
        merchant.setContactName(trim(contactName));
        merchant.setContactPhone(trim(contactPhone));
        merchant.setRemark(trim(remark));
        merchant.setStatus("ENABLED");
        merchant.setCreateBy(operator());
        merchant.setUpdateBy(operator());
        merchantMapper.insert(merchant);
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

    public List<MerchantUserItem> listUsers(Long merchantId) {
        ensureExists(merchantId);
        return merchantUserMapper.selectUsersByMerchant(merchantId);
    }

    public void addUser(Long merchantId, Long userId) {
        ensureExists(merchantId);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        MerchantUser mu = new MerchantUser();
        mu.setMerchantId(merchantId);
        mu.setUserId(userId);
        mu.setCreateBy(operator());
        mu.setUpdateBy(operator());
        merchantUserMapper.insertIgnore(mu);
    }

    public void removeUser(Long merchantId, Long userId) {
        ensureExists(merchantId);
        int n = merchantUserMapper.delete(merchantId, userId);
        if (n <= 0) {
            throw new BizException(404, "商户用户关系不存在");
        }
    }

    public MerchantOverview overview(Long merchantId) {
        ensureExists(merchantId);
        MerchantOverview overview = new MerchantOverview();
        overview.setHouseCount(merchantOverviewMapper.countHouses(merchantId));
        overview.setUserCount(merchantUserMapper.countUsersByMerchant(merchantId));
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

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
