package com.rabbit.app.modules.admin.service;

import com.rabbit.app.modules.admin.entity.PlatformAdmin;
import com.rabbit.app.modules.admin.mapper.PlatformAdminMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {
    private final PlatformAdminMapper platformAdminMapper;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String userName;
    private final String password;

    public PlatformAdminBootstrap(
            PlatformAdminMapper platformAdminMapper,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap.enabled:true}") boolean enabled,
            @Value("${app.admin.bootstrap.username:admin}") String userName,
            @Value("${app.admin.bootstrap.password:admin123456}") String password
    ) {
        this.platformAdminMapper = platformAdminMapper;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.userName = userName;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureBootstrapAdmin();
    }

    public void ensureBootstrapAdmin() {
        if (!enabled || userName == null || userName.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return;
        }
        PlatformAdmin existing = platformAdminMapper.selectByUserName(userName.trim());
        if (existing != null) {
            return;
        }
        PlatformAdmin admin = new PlatformAdmin();
        admin.setUserName(userName.trim());
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("SUPER_ADMIN");
        admin.setEnabled(Boolean.TRUE);
        platformAdminMapper.insert(admin);
    }
}
