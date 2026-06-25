package com.rabbit.app.modules.hardware.gateway;

import java.util.List;

public interface HardwareGateway {
    void aphrodisiacStart(Long houseId, Long batchId, List<Long> rabbitIds);

    void aphrodisiacFinish(Long houseId, Long batchId, List<Long> rabbitIds);

    String getType();
}

