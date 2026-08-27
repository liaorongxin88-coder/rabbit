package com.rabbit.app.modules.batch.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeparateWeaningRecordRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCountOnlyAndOptionalParents() {
        SeparateWeaningRecordRequest request = request(allocation(4, null, null));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void acceptsACompleteGenderPair() {
        SeparateWeaningRecordRequest request = request(allocation(4, 1, 3));
        request.setMotherRabbitId(11L);
        request.setFatherRabbitId(12L);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingNegativeAndMismatchedGenderCounts() {
        assertEquals(1, validator.validate(request(allocation(4, 1, null))).size());
        assertEquals(1, validator.validate(request(allocation(4, -1, 5))).size());
        assertEquals(1, validator.validate(request(allocation(4, 1, 2))).size());
    }

    private SeparateWeaningRecordRequest request(SeparateWeaningRecordRequest.Allocation allocation) {
        SeparateWeaningRecordRequest request = new SeparateWeaningRecordRequest();
        request.setRequestId("request-1");
        request.setAllocations(List.of(allocation));
        return request;
    }

    private SeparateWeaningRecordRequest.Allocation allocation(
        int count,
        Integer maleCount,
        Integer femaleCount
    ) {
        SeparateWeaningRecordRequest.Allocation allocation =
            new SeparateWeaningRecordRequest.Allocation();
        allocation.setCageId(9L);
        allocation.setCount(count);
        allocation.setMaleCount(maleCount);
        allocation.setFemaleCount(femaleCount);
        return allocation;
    }
}
