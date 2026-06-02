package com.rabbit.app.config;

import com.rabbit.app.audit.AuditLogInterceptor;
import com.rabbit.app.mapper.HouseUserMapper;
import com.rabbit.app.mapper.RabbitHouseMapper;
import com.rabbit.app.security.HouseGuardInterceptor;
import com.rabbit.app.security.PermissionInterceptor;
import com.rabbit.app.service.AuditLogService;
import com.rabbit.app.service.HouseService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final HouseUserMapper houseUserMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final AuditLogService auditLogService;
    private final HouseService houseService;

    public WebConfig(HouseUserMapper houseUserMapper, RabbitHouseMapper rabbitHouseMapper, AuditLogService auditLogService, HouseService houseService) {
        this.houseUserMapper = houseUserMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.auditLogService = auditLogService;
        this.houseService = houseService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuditLogInterceptor(auditLogService))
                .addPathPatterns("/api/**");
        registry.addInterceptor(new HouseGuardInterceptor(houseUserMapper, rabbitHouseMapper))
                .addPathPatterns("/api/**");
        registry.addInterceptor(new PermissionInterceptor(houseService))
                .addPathPatterns("/api/**");
    }
}
