package com.rabbit.app.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbit.app.common.Stamped;
import com.rabbit.app.modules.auth.entity.SysUser;
import com.rabbit.app.modules.auth.mapper.SysUserMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class OperationStampInterceptorTest {

    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final OperatorNameResolver resolver = new OperatorNameResolver(sysUserMapper);
    private final ObjectProvider<OperatorNameResolver> resolverProvider = provider(resolver);
    private final OperationStampInterceptor interceptor = new OperationStampInterceptor(true, resolverProvider);

    @AfterEach
    void clearContext() {
        OperationContext.clear();
    }

    @Test
    void fillsCreateByUpdateByHouseIdAndOperatorNameOnInsert() throws Throwable {
        givenUser(7L, "王小明");
        OperationContext.bind(7L, 12L, "trace-1");
        Row row = new Row();

        interceptor.intercept(invocation(SqlCommandType.INSERT, row));

        assertEquals("7", row.getCreateBy(), "create_by 统一存数字用户 ID");
        assertEquals("7", row.getUpdateBy());
        assertEquals(12L, row.getHouseId());
        assertEquals("王小明", row.getOperatorName(), "展示名单独快照，不再混进 create_by");
    }

    @Test
    void updateTouchesUpdateByButNotCreateBy() throws Throwable {
        givenUser(7L, "王小明");
        OperationContext.bind(7L, 12L, "trace-1");
        Row row = new Row();

        interceptor.intercept(invocation(SqlCommandType.UPDATE, row));

        assertNull(row.getCreateBy(), "更新不应重写创建人");
        assertEquals("7", row.getUpdateBy());
    }

    @Test
    void neverOverwritesValuesTheCallerSetExplicitly() throws Throwable {
        givenUser(7L, "王小明");
        OperationContext.bind(7L, 12L, "trace-1");
        Row row = new Row();
        row.setCreateBy("backfill");
        row.setUpdateBy("backfill");
        row.setHouseId(99L);

        interceptor.intercept(invocation(SqlCommandType.INSERT, row));

        assertEquals("backfill", row.getCreateBy());
        assertEquals("backfill", row.getUpdateBy());
        assertEquals(99L, row.getHouseId(), "跨兔舍的运维写入依赖显式值不被改写");
    }

    @Test
    void doesNothingWithoutAnOperationContext() throws Throwable {
        Row row = new Row();

        interceptor.intercept(invocation(SqlCommandType.INSERT, row));

        assertNull(row.getCreateBy(), "定时任务等无登录态的写入不该被盖上猜出来的操作人");
        assertNull(row.getHouseId());
    }

    @Test
    void stampsEveryElementOfABatchParameterMap() throws Throwable {
        givenUser(7L, "王小明");
        OperationContext.bind(7L, 12L, "trace-1");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            rows.add(new Row());
        }

        // MyBatis 把批量写入包成含 list 键的 Map，漏掉这一形状就正好漏掉批量端点。
        interceptor.intercept(invocation(SqlCommandType.INSERT, Map.of("list", rows)));

        assertEquals(500, rows.stream().filter(r -> "7".equals(r.getCreateBy())).count());
        assertEquals(500, rows.stream().filter(r -> "王小明".equals(r.getOperatorName())).count());
    }

    @Test
    void resolvesOperatorNameOncePerRequestEvenAcrossFiveHundredRows() throws Throwable {
        givenUser(7L, "王小明");
        OperationContext.bind(7L, 12L, "trace-1");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            rows.add(new Row());
        }

        interceptor.intercept(invocation(SqlCommandType.INSERT, Map.of("list", rows)));

        org.mockito.Mockito.verify(sysUserMapper, org.mockito.Mockito.times(1)).selectById(7L);
    }

    @Test
    void canBeDisabledByConfiguration() throws Throwable {
        givenUser(7L, "王小明");
        OperationContext.bind(7L, 12L, "trace-1");
        Row row = new Row();

        new OperationStampInterceptor(false, resolverProvider).intercept(invocation(SqlCommandType.INSERT, row));

        assertNull(row.getCreateBy());
    }

    /**
     * 拦截器持有的是 {@code ObjectProvider} 而非解析器本身：MyBatis 自动配置在
     * 构造 SqlSessionFactory 时就要集齐所有 Interceptor，而解析器最终依赖
     * sqlSessionTemplate，直接注入会绕成一个无法解开的环。
     */
    private ObjectProvider<OperatorNameResolver> provider(OperatorNameResolver value) {
        return new ObjectProvider<>() {
            @Override
            public OperatorNameResolver getObject() {
                return value;
            }

            @Override
            public OperatorNameResolver getObject(Object... args) {
                return value;
            }

            @Override
            public OperatorNameResolver getIfAvailable() {
                return value;
            }

            @Override
            public OperatorNameResolver getIfUnique() {
                return value;
            }

            @Override
            public Stream<OperatorNameResolver> stream() {
                return Stream.of(value);
            }
        };
    }

    private void givenUser(Long userId, String name) {
        SysUser user = new SysUser();
        user.setUserName(name);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
    }

    private Invocation invocation(SqlCommandType command, Object parameter) throws Exception {
        Configuration configuration = new Configuration();
        SqlSource sqlSource = p -> new BoundSql(configuration, "insert into rows values (1)", List.of(), p);
        MappedStatement statement = new MappedStatement
                .Builder(configuration, "test." + command.name().toLowerCase(java.util.Locale.ROOT), sqlSource, command)
                .build();
        Executor executor = mock(Executor.class);
        when(executor.update(any(), any())).thenReturn(1);
        Method update = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        return new Invocation(executor, update, new Object[]{statement, parameter});
    }

    static class Row implements Stamped {
        private Long houseId;
        private String createBy;
        private String updateBy;
        private String operatorName;

        @Override
        public String getCreateBy() {
            return createBy;
        }

        @Override
        public void setCreateBy(String createBy) {
            this.createBy = createBy;
        }

        @Override
        public String getUpdateBy() {
            return updateBy;
        }

        @Override
        public void setUpdateBy(String updateBy) {
            this.updateBy = updateBy;
        }

        @Override
        public Long getHouseId() {
            return houseId;
        }

        @Override
        public void setHouseId(Long houseId) {
            this.houseId = houseId;
        }

        @Override
        public String getOperatorName() {
            return operatorName;
        }

        @Override
        public void setOperatorName(String operatorName) {
            this.operatorName = operatorName;
        }
    }
}
