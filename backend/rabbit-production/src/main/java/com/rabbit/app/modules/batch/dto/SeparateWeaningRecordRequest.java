package com.rabbit.app.modules.batch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** Explicit commodity-cage allocations for one pending weaning record. */
public class SeparateWeaningRecordRequest {
    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @Positive(message = "motherRabbitId必须大于0")
    private Long motherRabbitId;

    @Positive(message = "fatherRabbitId必须大于0")
    private Long fatherRabbitId;

    @Valid
    @NotEmpty(message = "至少选择一个商品兔笼位")
    private List<Allocation> allocations;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getMotherRabbitId() {
        return motherRabbitId;
    }

    public void setMotherRabbitId(Long motherRabbitId) {
        this.motherRabbitId = motherRabbitId;
    }

    public Long getFatherRabbitId() {
        return fatherRabbitId;
    }

    public void setFatherRabbitId(Long fatherRabbitId) {
        this.fatherRabbitId = fatherRabbitId;
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

        @Min(value = 0, message = "maleCount不能小于0")
        private Integer maleCount;

        @Min(value = 0, message = "femaleCount不能小于0")
        private Integer femaleCount;

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

        public Integer getMaleCount() {
            return maleCount;
        }

        public void setMaleCount(Integer maleCount) {
            this.maleCount = maleCount;
        }

        public Integer getFemaleCount() {
            return femaleCount;
        }

        public void setFemaleCount(Integer femaleCount) {
            this.femaleCount = femaleCount;
        }

        @AssertTrue(message = "maleCount和femaleCount必须同时提供且之和等于count")
        public boolean isGenderCountPairValid() {
            if (maleCount == null && femaleCount == null) {
                return true;
            }
            return maleCount != null && femaleCount != null && count != null
                && (long) maleCount + femaleCount == count;
        }
    }
}
