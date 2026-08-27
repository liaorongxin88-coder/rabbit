package com.rabbit.app.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 写入盖章接口的默认实现契约。
 *
 * <p>{@code houseId} 和 {@code operatorName} 给了默认空实现，因为不是每张表都有这两列。
 * 这个设计有个容易踩空的地方：实现类若忘了覆写自己真有的列，编译器不会报错，
 * 拦截器照常调用，值就静默丢了 —— 表现为审计字段一片空白，但没有任何异常。
 *
 * <p>所以这里钉住两件事：没覆写时默认值必须是 null（表示「尚未确定」，让拦截器
 * 去补），以及 setter 的空实现必须真的安全可调、不能抛异常。
 */
class StampedTest {

    /**
     * 返回 null 表示「尚未确定」，拦截器据此决定是否用当前上下文补值。
     * 若默认返回 0 或其它哨兵值，拦截器会误判为「已有值」而跳过补全。
     */
    @Test
    void aTableWithoutTenantColumnReportsNoTenant() {
        assertNull(new MinimalStamped().getHouseId());
    }

    @Test
    void aTableWithoutOperatorNameColumnReportsNoName() {
        assertNull(new MinimalStamped().getOperatorName());
    }

    /**
     * 默认 setter 必须可以安全调用。拦截器不会先判断实现类支不支持，它只管盖章，
     * 这里抛异常就会把整条写入路径打断。
     */
    @Test
    void stampingAnUnsupportedColumnIsSilentlyIgnored() {
        MinimalStamped entity = new MinimalStamped();

        entity.setHouseId(42L);
        entity.setOperatorName("张三");

        assertNull(entity.getHouseId(), "没有该列，写入应被忽略而不是留在内存里");
        assertNull(entity.getOperatorName());
    }

    /**
     * createBy / updateBy 是必须实现的，没有默认值可退。
     */
    @Test
    void theAuditColumnsEveryTableHasAreRoundTripped() {
        MinimalStamped entity = new MinimalStamped();

        entity.setCreateBy("1001");
        entity.setUpdateBy("1002");

        assertEquals("1001", entity.getCreateBy());
        assertEquals("1002", entity.getUpdateBy());
    }

    /**
     * 覆写了的实现要正常生效，默认实现不能挡住它。
     */
    @Test
    void aTableWithTheseColumnsOverridesTheDefaults() {
        FullStamped entity = new FullStamped();

        entity.setHouseId(7L);
        entity.setOperatorName("李四");

        assertEquals(7L, entity.getHouseId());
        assertEquals("李四", entity.getOperatorName());
    }

    /** 只有 create_by / update_by 两列的表。 */
    private static final class MinimalStamped implements Stamped {
        private String createBy;
        private String updateBy;

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
    }

    /** 四列俱全的表。 */
    private static final class FullStamped implements Stamped {
        private String createBy;
        private String updateBy;
        private Long houseId;
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
