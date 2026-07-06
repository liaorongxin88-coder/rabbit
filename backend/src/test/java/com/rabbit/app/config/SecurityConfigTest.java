package com.rabbit.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {
    @Test
    void corsConfigurationAllowsAdminPreflight() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(
                "http://localhost:5173,http://127.0.0.1:5173,https://admin.dzht.top,https://rabbit.host.dzht.top",
                "https://*.dzht.top"
        );

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/admin/auth/login");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.checkOrigin("https://admin.dzht.top")).isEqualTo("https://admin.dzht.top");
        assertThat(config.checkOrigin("https://merchant.dzht.top")).isEqualTo("https://merchant.dzht.top");
        assertThat(config.checkHttpMethod(HttpMethod.POST)).contains(HttpMethod.POST);
        assertThat(config.checkHeaders(List.of("content-type", "authorization")))
                .contains("content-type", "authorization");
    }
}
