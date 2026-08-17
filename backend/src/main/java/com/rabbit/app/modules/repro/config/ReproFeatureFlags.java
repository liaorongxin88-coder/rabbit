package com.rabbit.app.modules.repro.config;

import com.rabbit.app.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 母兔生产流程 V2 的开关（施工计划 P2：新旧并存，默认关闭）。
 *
 * <p>P2 阶段新端点已经存在但必须默认不可用：新写路径与旧写路径操作的是同一批
 * 业务行，两条路径同时对外开放就等于把并发一致性交给运气。开关关闭时新端点
 * 一律 404，直到 P4 数据回填与对账完成后才统一放开。
 *
 * <p>返回 404 而不是 403：功能未开启对客户端而言等价于端点不存在，403 会让
 * 老客户端误以为是权限问题而去引导用户改授权。
 */
@Component
public class ReproFeatureFlags {
    private final boolean v2Enabled;

    public ReproFeatureFlags(@Value("${app.repro.v2.enabled:false}") boolean v2Enabled) {
        this.v2Enabled = v2Enabled;
    }

    public boolean isV2Enabled() {
        return v2Enabled;
    }

    public void assertV2Enabled() {
        if (!v2Enabled) {
            throw new BizException(404, "母兔生产流程 V2 未开启");
        }
    }
}
