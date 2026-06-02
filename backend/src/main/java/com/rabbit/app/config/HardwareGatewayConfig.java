package com.rabbit.app.config;

import com.rabbit.app.hardware.HardwareGateway;
import com.rabbit.app.hardware.HttpHardwareGateway;
import com.rabbit.app.hardware.NoopHardwareGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HardwareGatewayConfig {
    @Bean
    public HardwareGateway hardwareGateway(
            @Value("${app.hardware.gateway:noop}") String gateway,
            @Value("${app.hardware.http.base-url:}") String httpBaseUrl,
            @Value("${app.hardware.http.token:}") String httpToken,
            @Value("${app.hardware.http.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.hardware.http.read-timeout-ms:5000}") int readTimeoutMs
    ) {
        String type = gateway == null ? "" : gateway.trim().toLowerCase();
        if ("http".equals(type)) {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(Math.max(100, connectTimeoutMs));
            f.setReadTimeout(Math.max(100, readTimeoutMs));
            RestTemplate rt = new RestTemplate(f);
            return new HttpHardwareGateway(rt, httpBaseUrl, httpToken);
        }
        return new NoopHardwareGateway();
    }
}

