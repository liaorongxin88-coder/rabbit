package com.rabbit.app.modules.repro.service;

import com.rabbit.app.modules.repro.domain.ReproSettings;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import org.springframework.stereotype.Component;

/**
 * 解析当前兔舍生效的生产周期配置，并翻译成语义视图。
 *
 * <p>薄薄一层，但必须存在：状态机若直接调 {@link SettingService}，就得在每个分支里
 * 自己判断「用房级还是用户级」，配置优先级规则会被复制多份。这里收敛成一处。
 *
 * <h2>为什么不加缓存</h2>
 *
 * <p>实施计划曾建议在此加 Caffeine。本仓库无此依赖，也无 CacheManager，
 * 为一处调用引入缓存栈不划算。
 *
 * <p>批量端点出现后重新算过一次：{@code apply()} 逐只调用本方法，所以一次
 * 500 只的批量会触发 500 次解析。单次成本是：命中房级配置 1 次主键查询；
 * 未命中时再加 1 次用户级查询（建行为最多发生一次）。即 500-1000 次小表索引
 * 查询，约 75-150ms；而同一批量本身已要执行数千条写入、耗时以秒计。
 * 占比约 2-3%，不值得为此引入缓存失效窗口。
 *
 * <p>更不能做的是「批量入口解析一次、绕过 apply() 传入」：那会让单一写路径
 * 长出第二条分支，而消除分支漂移正是本次重构的目的。
 */
@Component
public class ReproSettingResolver {
    private final SettingService settingService;

    public ReproSettingResolver(SettingService settingService) {
        this.settingService = settingService;
    }

    /** 房级配置优先，缺失时回落到用户级；两者都没有则用内置默认值。 */
    public ReproSettings resolve(Long userId, Long houseId) {
        GlobalSetting setting = settingService.getEffectiveSetting(userId, houseId);
        return ReproSettings.from(setting);
    }
}
