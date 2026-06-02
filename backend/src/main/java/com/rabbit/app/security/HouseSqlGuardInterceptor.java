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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class HouseSqlGuardInterceptor implements Interceptor {
    private final int maxAffectedRows;
    private final String ignoreRowLimitIds;
    private final String ignoreNoWhereIds;

    public HouseSqlGuardInterceptor(
            @Value("${app.mybatis.write-guard.max-affected-rows:2000}") int maxAffectedRows,
            @Value("${app.mybatis.write-guard.ignore-row-limit-ids:}") String ignoreRowLimitIds,
            @Value("${app.mybatis.write-guard.ignore-no-where-ids:}") String ignoreNoWhereIds
    ) {
        this.maxAffectedRows = maxAffectedRows;
        this.ignoreRowLimitIds = ignoreRowLimitIds;
        this.ignoreNoWhereIds = ignoreNoWhereIds;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        if (args == null || args.length < 2) {
            return invocation.proceed();
        }
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        SqlCommandType cmd = ms.getSqlCommandType();
        if (cmd != SqlCommandType.UPDATE && cmd != SqlCommandType.DELETE) {
            return invocation.proceed();
        }
        BoundSql boundSql = ms.getBoundSql(parameter);
        if (boundSql == null) {
            return invocation.proceed();
        }
        String sql = boundSql.getSql();
        if (sql == null) {
            return invocation.proceed();
        }
        String s = normalizeSql(sql);
        if (!shouldIgnore(ms.getId(), ignoreNoWhereIds) && !s.contains(" where ")) {
            throw new BizException(500, "SQL缺少WHERE过滤: " + ms.getId());
        }
        if (hasHouseId(parameter) && !s.contains("house_id")) {
            throw new BizException(500, "SQL缺少house_id过滤: " + ms.getId());
        }
        Object result = invocation.proceed();
        int rows = result instanceof Integer ? (Integer) result : 0;
        if (maxAffectedRows > 0 && rows > maxAffectedRows && !shouldIgnore(ms.getId(), ignoreRowLimitIds)) {
            throw new BizException(500, "SQL影响行数过多(" + rows + ">" + maxAffectedRows + "): " + ms.getId());
        }
        return result;
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
