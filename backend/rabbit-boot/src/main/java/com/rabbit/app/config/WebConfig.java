package com.rabbit.app.config;

import com.rabbit.app.modules.audit.support.AuditLogInterceptor;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import com.rabbit.app.modules.admin.security.PlatformAdminGuardInterceptor;
import com.rabbit.app.security.AccessControlService;
import com.rabbit.app.security.AuthorizationInterceptor;
import com.rabbit.app.security.BusinessAuthenticationInterceptor;
import com.rabbit.app.modules.audit.service.AuditLogService;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
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

    /**
     * OTA 升级包几十 MB，场区网络又慢，容器默认的异步超时会把下载从中间提早掉。
     * 本地快网碰不到这个阈值，真机走慢链路时会以
     * AsyncRequestTimeoutException 方式断在半路，客户端只看到「连接超时」。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(Duration.ofMinutes(30).toMillis());
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
                        "/api/app-updates/check",
                        // 升级包下载不能要登录：强制升级时用户本就进不去，
                        // 客户端下载也不带 Authorization 头。
                        "/api/app-updates/*/download"
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
                        "/api/app-updates/check",
                        "/api/app-updates/*/download"
                );
    }
}
