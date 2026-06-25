package com.rabbit.app.modules.house.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.dto.HouseMemberItem;
import com.rabbit.app.modules.house.entity.HouseUser;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseMemberService {
    private final HouseUserMapper houseUserMapper;
    private final SysUserMapper sysUserMapper;
    private final RequestDedupService requestDedupService;

    public HouseMemberService(HouseUserMapper houseUserMapper, SysUserMapper sysUserMapper, RequestDedupService requestDedupService) {
        this.houseUserMapper = houseUserMapper;
        this.sysUserMapper = sysUserMapper;
        this.requestDedupService = requestDedupService;
    }

    public List<HouseMemberItem> listMembers(Long houseId) {
        return houseUserMapper.selectMembersByHouse(houseId);
    }

    @Transactional
    public void addMember(Long houseId, Long operatorUserId, String operator, String userName, String perms, Boolean isAdmin, String requestId) {
        String api = "houseMember.add";
        if (requestDedupService.shouldSkipAsDone(houseId, operatorUserId, api, requestId)) {
            return;
        }
        requestDedupService.markProcessing(houseId, operatorUserId, api, requestId);
        try {
            SysUser user = sysUserMapper.selectByUserName(userName);
            if (user == null) {
                throw new BizException(404, "用户不存在");
            }
            if (perms == null || perms.trim().isEmpty()) {
                perms = "view";
            }
            if (!"view".equals(perms) && !"edit".equals(perms) && !"control".equals(perms)) {
                throw new BizException(400, "perms不合法");
            }
            if (isAdmin == null) {
                isAdmin = false;
            }
            HouseUser hu = new HouseUser();
            hu.setHouseId(houseId);
            hu.setUserId(user.getUserId());
            hu.setPerms(perms);
            hu.setIsAdmin(isAdmin);
            hu.setCreateBy(operator);
            hu.setUpdateBy(operator);
            try {
                houseUserMapper.insert(hu);
            } catch (DuplicateKeyException e) {
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
            if (!"view".equals(newPerms) && !"edit".equals(newPerms) && !"control".equals(newPerms)) {
                throw new BizException(400, "perms不合法");
            }
            int countAdmins = houseUserMapper.countAdmins(houseId);
            if (Boolean.TRUE.equals(current.getIsAdmin()) && !Boolean.TRUE.equals(newAdmin) && countAdmins <= 1) {
                throw new BizException(400, "至少保留1个管理员");
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
            int countAdmins = houseUserMapper.countAdmins(houseId);
            if (Boolean.TRUE.equals(current.getIsAdmin()) && countAdmins <= 1) {
                throw new BizException(400, "至少保留1个管理员");
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
}
