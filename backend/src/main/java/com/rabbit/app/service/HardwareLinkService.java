package com.rabbit.app.service;

import com.rabbit.app.hardware.HardwareGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HardwareLinkService {
    private final HardwareGateway gateway;
    private final boolean enabled;

    public HardwareLinkService(HardwareGateway gateway, @Value("${app.hardware.enabled:false}") boolean enabled) {
        this.gateway = gateway;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getType() {
        return gateway == null ? "none" : gateway.getType();
    }

    public void aphrodisiacStart(Long houseId, Long batchId, List<Long> rabbitIds) {
        if (!enabled || gateway == null) {
            return;
        }
        gateway.aphrodisiacStart(houseId, batchId, rabbitIds);
    }

    public void aphrodisiacFinish(Long houseId, Long batchId, List<Long> rabbitIds) {
        if (!enabled || gateway == null) {
            return;
        }
        gateway.aphrodisiacFinish(houseId, batchId, rabbitIds);
    }
}

