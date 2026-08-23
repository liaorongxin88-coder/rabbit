package com.rabbit.app.modules.batch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Explicit commodity-cage allocations for one pending weaning record. */
public class SeparateWeaningRecordRequest {
    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @Valid
    @NotEmpty(message = "至少选择一个商品兔笼位")
    private List<Allocation> allocations;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public List<Allocation> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<Allocation> allocations) {
        this.allocations = allocations;
    }

    public static class Allocation {
        @NotNull(message = "cageId不能为空")
        private Long cageId;

        @NotNull(message = "count不能为空")
        @Min(value = 1, message = "count必须大于0")
        private Integer count;

        public Long getCageId() {
            return cageId;
        }

        public void setCageId(Long cageId) {
            this.cageId = cageId;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
