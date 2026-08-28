package com.rabbit.app.tracking;

import com.rabbit.app.common.Stamped;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 写入时自动盖章：给实现 {@link Stamped} 的实体补 {@code create_by}、
 * {@code update_by}、{@code house_id}、{@code operator_name}。
 *
 * <p>结构照抄同包的 {@code HouseSqlGuardInterceptor}：同样挂
 * {@code Executor.update}，同样由 {@code app.mybatis.*} 开关控制。差别在于
 * 那个是<b>只读校验</b>（检查 SQL 文本里有没有 where 和 house_id），
 * 这个是<b>写入补全</b>（改参数对象的字段）。
 *
 * <p>存在的理由是消灭样板：全仓 65 处 {@code setCreateBy} 和 70 处
 * {@code setUpdateBy} 散在 23 个 service 里，靠人手写就必然有人漏写、
 * 有人写展示名有人写数字 ID——跨表归因错误正是这么来的。口径收进一处，
 * 就只有一个地方需要被审。
 *
 * <p><b>只在有上下文时盖章。</b>没有 {@link OperationContext} 说明这不是
 * 一次用户发起的写入（定时任务、启动引导），此时盖一个猜出来的操作人
 * 比留空更有害。
 *
 * <p><b>已有值一律不覆盖。</b>拦截器补的是「调用方没说」的部分，
 * 不去改写调用方明确写下的值——数据回填、跨兔舍运维写入都依赖这一点。
 *
 * <p><b>一个已知边界</b>：本拦截器在 {@code Executor.update} 里改参数字段，
 * 而 {@code HouseSqlGuardInterceptor} 会调 {@code ms.getBoundSql(parameter)}
 * 生成 SQL 文本。若某个 mapper 用 {@code <if test="createBy != null">}
 * 这类动态 SQL 依赖被盖章的字段，两者的相对顺序就会影响结果。
 * 现有 mapper 的写语句都是静态 SQL，不受影响；此处用 {@code @Order} 把本
 * 拦截器排到注册链末尾（MyBatis 后注册者在外层、先执行），
 * 保证盖章发生在守卫读取 BoundSql 之前。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class OperationStampInterceptor implements Interceptor {

    private final boolean enabled;

    /**
     * 必须是 {@code ObjectProvider} 而不能直接注入。MyBatis 的自动配置在构造
     * {@code SqlSessionFactory} 时就要把所有 {@code Interceptor} bean 集齐，
     * 而 {@link OperatorNameResolver} 依赖 {@code SysUserMapper}，后者又依赖
     * {@code sqlSessionTemplate}——直接注入会绕成一个无法解开的环，容器启动
     * 失败。推迟到真正用到的时候再取，环就断了。
     */
    private final ObjectProvider<OperatorNameResolver> operatorNameResolver;

    public OperationStampInterceptor(
            @Value("${app.mybatis.operation-stamp.enabled:true}") boolean enabled,
            ObjectProvider<OperatorNameResolver> operatorNameResolver
    ) {
        this.enabled = enabled;
        this.operatorNameResolver = operatorNameResolver;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        if (!enabled || args == null || args.length < 2) {
            return invocation.proceed();
        }
        OperationContext context = OperationContext.current();
        if (context == null) {
            return invocation.proceed();
        }
        MappedStatement ms = (MappedStatement) args[0];
        SqlCommandType command = ms.getSqlCommandType();
        if (command != SqlCommandType.INSERT && command != SqlCommandType.UPDATE) {
            return invocation.proceed();
        }
        stampParameter(args[1], command, context);
        return invocation.proceed();
    }

    /**
     * 参数可能是单个实体、集合，或 {@code @Param} 组成的 Map（MyBatis 会把
     * 多参数方法包成 Map，批量插入则包成含 list/collection 键的 Map）。
     * 三种形状都要走到，否则批量写入正好是漏网的那一类。
     */
    private void stampParameter(Object parameter, SqlCommandType command, OperationContext context) {
        if (parameter == null) {
            return;
        }
        if (parameter instanceof Stamped stamped) {
            stamp(stamped, command, context);
            return;
        }
        if (isLegacyStamped(parameter)) {
            stampLegacy(parameter, command, context);
            return;
        }
        if (parameter instanceof Collection<?> collection) {
            for (Object item : collection) {
                stampParameter(item, command, context);
            }
            return;
        }
        if (parameter instanceof Object[] array) {
            for (Object item : array) {
                stampParameter(item, command, context);
            }
            return;
        }
        if (parameter instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value instanceof Stamped || isLegacyStamped(value) || value instanceof Collection<?> || value instanceof Object[]) {
                    stampParameter(value, command, context);
                }
            }
        }
    }

    private void stamp(Stamped entity, SqlCommandType command, OperationContext context) {
        String operator = context.getUserId() == null ? null : String.valueOf(context.getUserId());
        if (command == SqlCommandType.INSERT && isBlank(entity.getCreateBy()) && operator != null) {
            entity.setCreateBy(operator);
        }
        if (isBlank(entity.getUpdateBy()) && operator != null) {
            entity.setUpdateBy(operator);
        }
        if (entity.getHouseId() == null && context.getHouseId() != null) {
            entity.setHouseId(context.getHouseId());
        }
        if (isBlank(entity.getOperatorName())) {
            String name = operatorName(context);
            if (name != null) {
                entity.setOperatorName(name);
            }
        }
    }

    /**
     * T2 transitions pre-existing entities that expose the conventional JavaBean audit fields
     * but have not yet declared {@link Stamped}. It is deliberately property-based rather than
     * field-reflective so objects without every optional snapshot column remain unaffected.
     */
    private boolean isLegacyStamped(Object candidate) {
        if (candidate == null || candidate instanceof CharSequence || candidate instanceof Number
                || candidate instanceof Boolean || candidate instanceof Enum<?>) {
            return false;
        }
        BeanWrapper bean = PropertyAccessorFactory.forBeanPropertyAccess(candidate);
        return bean.isReadableProperty("createBy")
                && bean.isWritableProperty("createBy")
                && bean.isReadableProperty("updateBy")
                && bean.isWritableProperty("updateBy");
    }

    private void stampLegacy(Object entity, SqlCommandType command, OperationContext context) {
        BeanWrapper bean = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        String operator = context.getUserId() == null ? null : String.valueOf(context.getUserId());
        if (command == SqlCommandType.INSERT && isBlank(value(bean, "createBy")) && operator != null) {
            bean.setPropertyValue("createBy", operator);
        }
        if (isBlank(value(bean, "updateBy")) && operator != null) {
            bean.setPropertyValue("updateBy", operator);
        }
        if (bean.isReadableProperty("houseId") && bean.isWritableProperty("houseId")
                && bean.getPropertyValue("houseId") == null && context.getHouseId() != null) {
            bean.setPropertyValue("houseId", context.getHouseId());
        }
        if (bean.isReadableProperty("operatorName") && bean.isWritableProperty("operatorName")
                && isBlank(value(bean, "operatorName"))) {
            String name = operatorName(context);
            if (name != null) {
                bean.setPropertyValue("operatorName", name);
            }
        }
    }

    private String operatorName(OperationContext context) {
        String name = context.getOperatorName();
        if (name != null) {
            return name;
        }
        OperatorNameResolver resolver = operatorNameResolver.getIfAvailable();
        return resolver == null ? null : resolver.resolve(context.getUserId());
    }

    private String value(BeanWrapper bean, String property) {
        Object value = bean.getPropertyValue(property);
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
