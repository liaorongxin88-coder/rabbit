package com.rabbit.app.hardware;

import java.util.List;

public interface HardwareGateway {
    void aphrodisiacStart(Long houseId, Long batchId, List<Long> rabbitIds);

    void aphrodisiacFinish(Long houseId, Long batchId, List<Long> rabbitIds);

    String getType();
}

