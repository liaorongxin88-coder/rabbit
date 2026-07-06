package com.rabbit.app.config;

import com.rabbit.app.modules.audit.support.AuditLogInterceptor;
import com.rabbit.app.modules.admin.mapper.MerchantMapper;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import com.rabbit.app.modules.house.mapper.HouseUserMapper;
import com.rabbit.app.modules.house.mapper.RabbitHouseMapper;
import com.rabbit.app.security.HouseGuardInterceptor;
import com.rabbit.app.security.PermissionInterceptor;
import com.rabbit.app.security.PlatformAdminGuardInterceptor;
import com.rabbit.app.modules.audit.service.AuditLogService;
import com.rabbit.app.modules.house.service.HouseService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final HouseUserMapper houseUserMapper;
    private final RabbitHouseMapper rabbitHouseMapper;
    private final MerchantMapper merchantMapper;
    private final PlatformAdminMapper platformAdminMapper;
    private final AuditLogService auditLogService;
    private final HouseService houseService;

    public WebConfig(
            HouseUserMapper houseUserMapper,
            RabbitHouseMapper rabbitHouseMapper,
            MerchantMapper merchantMapper,
            PlatformAdminMapper platformAdminMapper,
            AuditLogService auditLogService,
            HouseService houseService
    ) {
        this.houseUserMapper = houseUserMapper;
        this.rabbitHouseMapper = rabbitHouseMapper;
        this.merchantMapper = merchantMapper;
        this.platformAdminMapper = platformAdminMapper;
        this.auditLogService = auditLogService;
        this.houseService = houseService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuditLogInterceptor(auditLogService))
                .addPathPatterns("/api/**");
        registry.addInterceptor(new PlatformAdminGuardInterceptor(platformAdminMapper))
                .addPathPatterns("/api/admin/**");
        registry.addInterceptor(new HouseGuardInterceptor(houseUserMapper, rabbitHouseMapper, merchantMapper))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/**");
        registry.addInterceptor(new PermissionInterceptor(houseService))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/**");
    }
}
