package com.rabbit.app.modules.rabbit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import java.util.List;
import org.junit.jupiter.api.Test;

class RabbitServiceTest {
    @Test
    void convertToReplacementRequiresControlAtServiceBoundary() {
        HouseService houseService = new HouseService(null, null, null, null, null, null) {
            @Override
            public void assertHousePermission(Long userId, Long houseId, String requiredPerm) {
                throw new BizException(403, "权限不足");
            }
        };
        RabbitService service = new RabbitService(
                null, null, null, null, null, null, null, null,
                null, houseService, 10
        );

        BizException error = assertThrows(BizException.class,
                () -> service.convertToReplacement(
                        7L, 8L, List.of(1L), false, null, "request-1"
                ));

        assertEquals(403, error.getCode());
    }
}
