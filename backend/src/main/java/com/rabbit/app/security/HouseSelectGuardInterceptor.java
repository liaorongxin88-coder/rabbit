package com.rabbit.app.security;

import com.rabbit.app.common.BizException;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, BoundSql.class})
})
public class HouseSelectGuardInterceptor implements Interceptor {
    private final boolean enabled;
    private final String ignoreIds;

    public HouseSelectGuardInterceptor(
            @Value("${app.mybatis.select-guard.enabled:true}") boolean enabled,
            @Value("${app.mybatis.select-guard.ignore-ids:}") String ignoreIds
    ) {
        this.enabled = enabled;
        this.ignoreIds = ignoreIds;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!enabled) {
            return invocation.proceed();
        }
        Object[] args = invocation.getArgs();
        if (args == null || args.length < 2) {
            return invocation.proceed();
        }
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        if (ms == null) {
            return invocation.proceed();
        }
        if (ms.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }
        if (shouldIgnore(ms.getId(), ignoreIds)) {
            return invocation.proceed();
        }
        if (!hasHouseId(parameter)) {
            return invocation.proceed();
        }
        BoundSql boundSql;
        if (args.length >= 6 && args[5] instanceof BoundSql) {
            boundSql = (BoundSql) args[5];
        } else {
            boundSql = ms.getBoundSql(parameter);
        }
        if (boundSql == null) {
            return invocation.proceed();
        }
        String sql = boundSql.getSql();
        if (sql == null) {
            return invocation.proceed();
        }
        String s = normalizeSql(sql);
        if (!s.contains("house_id")) {
            throw new BizException(500, "SQL缺少house_id过滤: " + ms.getId());
        }
        return invocation.proceed();
    }

    private boolean hasHouseId(Object parameter) {
        if (parameter == null) {
            return false;
        }
        if (parameter instanceof Map) {
            return ((Map<?, ?>) parameter).containsKey("houseId");
        }
        return false;
    }

    private String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean shouldIgnore(String msId, String config) {
        if (msId == null || msId.trim().isEmpty()) {
            return false;
        }
        if (config == null || config.trim().isEmpty()) {
            return false;
        }
        String[] parts = config.split(",");
        for (String raw : parts) {
            if (raw == null) {
                continue;
            }
            String p = raw.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.endsWith("*")) {
                String prefix = p.substring(0, p.length() - 1);
                if (!prefix.isEmpty() && msId.startsWith(prefix)) {
                    return true;
                }
            } else if (msId.equals(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object plugin(Object target) {
        return org.apache.ibatis.plugin.Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}

