package com.rabbit.app.modules.house.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.dto.HouseMemberItem;
import com.rabbit.app.modules.house.dto.UserSearchItem;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseMemberService {
    private final HouseUserMapper houseUserMapper;
    private final SysUserMapper sysUserMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final RequestDedupService requestDedupService;

    public HouseMemberService(
            HouseUserMapper houseUserMapper,
            SysUserMapper sysUserMapper,
            RabbitHouseMapper rabbitHouseMapper,
            RequestDedupService requestDedupService
    ) {
        this.houseUserMapper = houseUserMapper;
        this.sysUserMapper = sysUserMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.requestDedupService = requestDedupService;
    }

    public List<HouseMemberItem> listMembers(Long houseId) {
        return houseUserMapper.selectMembersByHouse(houseId);
    }

    public List<UserSearchItem> searchCandidates(Long houseId, String keyword, int limit) {
        RabbitHouse house = rabbitHouseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(404, "兔舍不存在");
        }
        if (house.getMerchantId() == null) {
            throw new BizException(500, "兔舍未归属商户");
        }
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        if (limit <= 0) {
            limit = 10;
        }
        if (limit > 20) {
            limit = 20;
        }
        List<Long> exclude = houseUserMapper.selectMemberUserIds(houseId);
        List<SysUser> users = sysUserMapper.searchByMerchant(house.getMerchantId(), q, exclude, limit);
        List<UserSearchItem> items = new ArrayList<UserSearchItem>();
        for (SysUser user : users) {
            UserSearchItem item = new UserSearchItem();
            item.setUserId(user.getUserId());
            item.setUserName(user.getUserName());
            items.add(item);
        }
        return items;
    }

    @Transactional
    public void addMember(Long houseId, Long operatorUserId, String operator, String userName, String perms, Boolean isAdmin, String requestId) {
        String api = "houseMember.add";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            if (Boolean.TRUE.equals(isAdmin)) {
                throw new BizException(400, "新增成员不能设为管理员，请使用转让管理员");
            }
            SysUser user = sysUserMapper.selectByUserName(userName);
            if (user == null) {
                throw new BizException(404, "用户不存在");
            }
            assertSameMerchant(houseId, user);
            if (houseUserMapper.selectByUserAndHouse(user.getUserId(), houseId) != null) {
                throw new BizException(409, "用户已是兔舍成员");
            }
            perms = normalizeMemberPerms(perms, false);
            HouseUser hu = new HouseUser();
            hu.setHouseId(houseId);
            hu.setUserId(user.getUserId());
            hu.setPerms(perms);
            hu.setIsAdmin(Boolean.FALSE);
            hu.setCreateBy(operator);
            hu.setUpdateBy(operator);
            try {
                houseUserMapper.insert(hu);
            } catch (DuplicateKeyException e) {
                throw new BizException(409, "用户已是兔舍成员");
            }
            requestDedupService.markDone(houseId, operatorUserId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, operatorUserId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void updateMember(Long houseId, Long targetUserId, Long operatorUserId, String operator, String perms, Boolean isAdmin, String requestId) {
        String api = "houseMember.update";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            HouseUser current = houseUserMapper.selectByUserAndHouse(targetUserId, houseId);
            if (current == null) {
                throw new BizException(404, "成员不存在");
            }
            String newPerms = perms != null ? perms : current.getPerms();
            Boolean newAdmin = isAdmin != null ? isAdmin : current.getIsAdmin();
            if (Boolean.TRUE.equals(newAdmin)) {
                newPerms = "control";
                houseUserMapper.clearOtherAdmins(houseId, targetUserId, operator);
                newAdmin = Boolean.TRUE;
            } else {
                newPerms = normalizeMemberPerms(newPerms, false);
                newAdmin = Boolean.FALSE;
                int countAdmins = houseUserMapper.countAdmins(houseId);
                if (Boolean.TRUE.equals(current.getIsAdmin()) && countAdmins <= 1) {
                    throw new BizException(400, "请先转让管理员后再调整该成员权限");
                }
            }
            int n = houseUserMapper.updateMember(houseId, targetUserId, newPerms, newAdmin, operator);
            if (n <= 0) {
                throw new BizException(400, "更新失败");
            }
            requestDedupService.markDone(houseId, operatorUserId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, operatorUserId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void removeMember(Long houseId, Long targetUserId, Long operatorUserId, String requestId) {
        String api = "houseMember.remove";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            HouseUser current = houseUserMapper.selectByUserAndHouse(targetUserId, houseId);
            if (current == null) {
                throw new BizException(404, "成员不存在");
            }
            if (Boolean.TRUE.equals(current.getIsAdmin()) && houseUserMapper.countAdmins(houseId) <= 1) {
                throw new BizException(400, "请先转让管理员后再移除");
            }
            int n = houseUserMapper.deleteMember(houseId, targetUserId);
            if (n <= 0) {
                throw new BizException(400, "移除失败");
            }
            requestDedupService.markDone(houseId, operatorUserId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, operatorUserId, api, requestId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void leaveHouse(Long houseId, Long userId, String requestId) {
        String api = "houseMember.leave";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, userId, api, requestId);
        try {
            HouseUser current = houseUserMapper.selectByUserAndHouse(userId, houseId);
            if (current == null) {
                throw new BizException(404, "您不是该兔舍成员");
            }
            if (Boolean.TRUE.equals(current.getIsAdmin()) && houseUserMapper.countAdmins(houseId) <= 1) {
                throw new BizException(400, "唯一管理员不能直接退出，请先转让管理员");
            }
            int n = houseUserMapper.deleteMember(houseId, userId);
            if (n <= 0) {
                throw new BizException(400, "退出失败");
            }
            requestDedupService.markDone(houseId, userId, api, requestId);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, requestId, e.getMessage());
            throw e;
        }
    }

    private void assertSameMerchant(Long houseId, SysUser user) {
        RabbitHouse house = rabbitHouseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(404, "兔舍不存在");
        }
        if (house.getMerchantId() == null) {
            throw new BizException(500, "兔舍未归属商户");
        }
        if (user.getMerchantId() == null || !house.getMerchantId().equals(user.getMerchantId())) {
            throw new BizException(400, "只能添加同商户下的账号");
        }
    }

    private String normalizeMemberPerms(String perms, boolean admin) {
        if (admin) {
            return "control";
        }
        if (perms == null || perms.trim().isEmpty()) {
            return "edit";
        }
        String p = perms.trim();
        if ("view".equals(p) || "edit".equals(p) || "control".equals(p)) {
            return p;
        }
        throw new BizException(400, "成员权限不合法");
    }
}
