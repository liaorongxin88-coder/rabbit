package com.rabbit.app.common;

/**
 * 可被写入拦截器自动盖章的业务实体。
 *
 * <p>为什么放在 platform：盖章的<b>值</b>来自 access 的 {@code OperationContext}，
 * 但盖章的<b>目标</b>是 production 里的实体。两边唯一共同的下游是 platform，
 * 所以契约必须落在这里，否则 production 就要反向依赖 access 的类型，
 * 破坏 {@code FarmingModuleArchitectureTest} 已经钉死的模块方向。
 *
 * <p>为什么是接口而不是注解：拦截器拿到的是运行期对象，接口一次
 * {@code instanceof} 就能判定并直接调 setter；注解要走反射找字段、
 * 处理继承与桥接方法，在写路径上按 500 只兔的批量算并不划算。
 *
 * <p><b>盖的是什么值</b>：{@code createBy}/{@code updateBy} 统一写数字用户 ID
 * 的字符串形式，与 {@code WeightService}、{@code SaleService}、{@code BatchService}
 * 现有口径一致；展示名走 {@link #setOperatorName(String)} 单独快照，
 * 不再混进 {@code create_by}。存量表里存展示名的那一半由 T2 的清洗迁移收敛，
 * 本阶段不改数据。
 *
 * <p>{@code houseId} 与 {@code operatorName} 给默认空实现：不是每张表都有这两列，
 * 实现者只需覆写自己真有的那些。覆写了但 mapper XML 没写该列也无害——
 * 拦截器只改 Java 字段，写不写进 SQL 由 XML 决定。
 */
public interface Stamped {

    String getCreateBy();

    void setCreateBy(String createBy);

    String getUpdateBy();

    void setUpdateBy(String updateBy);

    /**
     * 租户维度。返回 null 表示「尚未确定」，拦截器会用当前上下文的兔舍补上；
     * 已有值一律不覆盖，避免拦截器悄悄改写跨兔舍的运维写入。
     */
    default Long getHouseId() {
        return null;
    }

    default void setHouseId(Long houseId) {
        // 默认不支持：该表没有 house_id 列。
    }

    /**
     * 操作人展示名快照。审计要回答的是「当时是谁」，而 sys_user.user_name 可改，
     * 事后 join 出来的是「现在的名字」，所以必须在写入时定格。
     */
    default String getOperatorName() {
        return null;
    }

    default void setOperatorName(String operatorName) {
        // 默认不支持：该表还没有 operator_name 列（由 T2 的 V44 补齐）。
    }
}
