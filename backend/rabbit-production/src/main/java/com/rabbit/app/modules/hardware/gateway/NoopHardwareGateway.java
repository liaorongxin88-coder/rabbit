package com.rabbit.app.modules.hardware.gateway;

import java.util.List;

public class NoopHardwareGateway implements HardwareGateway {
    @Override
    public void aphrodisiacStart(Long houseId, Long batchId, List<Long> rabbitIds) {
    }

    @Override
    public void aphrodisiacFinish(Long houseId, Long batchId, List<Long> rabbitIds) {
    }

    @Override
    public String getType() {
        return "noop";
    }
}
