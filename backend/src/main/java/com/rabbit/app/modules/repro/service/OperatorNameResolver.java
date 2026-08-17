package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

/**
 * 把 userId 解析成写入 {@code repro_events.operator_name} 的展示名。
 *
 * <p>为什么需要它：事件流是给人看的审计记录，「12 在 3 月 4 日做了摸胎」
 * 没有可读性，而事故复盘恰恰要靠这条流回答「谁做的」。存 ID 等于把这个
 * 问题推给每个读取方去 join 用户表——而 sys_user 的名字可改，事后 join
 * 出来的是「现在的名字」，不是「当时的名字」。所以这里做的是<b>快照</b>。
 *
 * <p>取 {@code sys_user.user_name}：这正是产品在成员列表里展示的字段
 * （见 {@code HouseMemberItem.userName}），不另造一套展示名口径。
 *
 * <p>调用约定：<b>每请求解析一次</b>，再往下传。批量端点一次可处理 500 只，
 * 若下沉到 apply() 里逐只解析就是 500 次重复查询。
 *
 * <p>不缓存：单行主键查询，且用户改名后应尽快反映到新事件上；
 * 与 {@code ReproSettingResolver} 保持同一取舍。
 */
@Component
public class OperatorNameResolver {

    private final SysUserMapper sysUserMapper;

    public OperatorNameResolver(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 返回展示名；查不到时返回 null，由状态机的 operatorOf 回落到
     * userId 字符串、再回落到 "system"（回填/系统任务无登录态）。
     *
     * <p>解析失败不抛异常：操作者姓名是审计的锦上添花，不该让一次
     * 用户表查询故障阻断母兔的生产流程写入。
     */
    public String resolve(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getUserName() == null || user.getUserName().isBlank()) {
            return null;
        }
        return user.getUserName().trim();
    }
}
