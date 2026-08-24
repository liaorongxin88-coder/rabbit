package com.rabbit.app.config;

import com.rabbit.app.modules.audit.support.AuditLogInterceptor;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import com.rabbit.app.modules.admin.security.PlatformAdminGuardInterceptor;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.AuthorizationInterceptor;
import com.rabbit.app.security.BusinessAuthenticationInterceptor;
import com.rabbit.app.modules.audit.service.AuditLogService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final PlatformAdminMapper platformAdminMapper;
    private final AuditLogService auditLogService;
    private final AccessControlService accessControlService;

    public WebConfig(
            PlatformAdminMapper platformAdminMapper,
            AuditLogService auditLogService,
            AccessControlService accessControlService
    ) {
        this.platformAdminMapper = platformAdminMapper;
        this.auditLogService = auditLogService;
        this.accessControlService = accessControlService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuditLogInterceptor(auditLogService))
                .addPathPatterns("/api/**");
        registry.addInterceptor(new PlatformAdminGuardInterceptor(platformAdminMapper))
                .addPathPatterns("/api/admin/**");
        registry.addInterceptor(new BusinessAuthenticationInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/admin/**",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/sms/code",
                        "/api/auth/sms/login",
                        "/api/auth/sms/reset-password",
                        "/api/auth/phone-one-tap-login",
                        "/api/auth/wechat-login",
                        "/api/app/updates/check",
                        "/api/app/updates/*/apk"
                );
        registry.addInterceptor(new AuthorizationInterceptor(accessControlService))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/admin/auth/login",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/sms/code",
                        "/api/auth/sms/login",
                        "/api/auth/sms/reset-password",
                        "/api/auth/phone-one-tap-login",
                        "/api/auth/wechat-login",
                        "/api/app/updates/check",
                        "/api/app/updates/*/apk"
                );
    }
}
