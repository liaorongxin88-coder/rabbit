package com.rabbit.app.tracking;

import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

/**
 * 把 userId 解析成写入审计快照的展示名。
 *
 * <p>从 {@code rabbit-production/modules/repro} 移到 access：它依赖的
 * {@code SysUserMapper} 就在 access，原先的位置让 production 的多个模块
 * （batch、rabbit、repro）都要反向 import 一个繁育包里的类，模块方向是反的。
 * 原位置留了一个 {@code @Deprecated} 的转发壳，让既有调用方不必同步改动
 * （那些文件此刻正被其他改造占用），壳在 T2/T4 铺开注解时删除。
 *
 * <p>为什么存快照而不是存 ID 让读取方 join：{@code sys_user.user_name} 可改，
 * 事后 join 出来的是「现在的名字」，不是「当时的名字」，而事故复盘要的正是后者。
 *
 * <p><b>与旧实现的唯一行为差异：加了请求级缓存。</b>旧实现的类注释写着
 * 「每请求解析一次，再往下传」，靠调用方自律；一旦下沉到自动盖章的写路径，
 * 批量端点单次 500 只兔就是 500 次主键查询。缓存挂在 {@link OperationContext}
 * 上而不是 Spring 的 request scope：ThreadLocal 在定时任务、直接调 service 的
 * 测试里同样有效，不需要 Web 请求存在。用户改名最迟下个请求生效，
 * 与旧实现「不缓存」的取舍差异可以忽略。
 */
@Component
public class OperatorNameResolver {

    private final SysUserMapper sysUserMapper;

    public OperatorNameResolver(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 返回展示名；查不到返回 null，由调用方决定回落策略。
     *
     * <p>解析失败不抛异常：操作者姓名是审计的锦上添花，不该让一次用户表
     * 查询故障阻断生产写入。
     */
    public String resolve(Long userId) {
        if (userId == null) {
            return null;
        }
        OperationContext context = OperationContext.current();
        if (context == null) {
            return load(userId);
        }
        return context.cachedOperatorName(userId, this::load);
    }

    private String load(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getUserName() == null || user.getUserName().isBlank()) {
            return null;
        }
        return user.getUserName().trim();
    }
}
