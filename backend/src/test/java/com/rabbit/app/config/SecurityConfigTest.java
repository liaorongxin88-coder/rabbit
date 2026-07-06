package com.rabbit.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

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

    @Test
    void corsFilterHandlesAdminPreflightBeforeSecurity() throws Exception {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsFilter corsFilter = new CorsFilter(securityConfig.corsConfigurationSource(
                "http://localhost:5173,http://127.0.0.1:5173,https://admin.dzht.top,https://rabbit.host.dzht.top",
                "https://*.dzht.top"
        ));

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/admin/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, "https://admin.dzht.top");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();

        corsFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("https://admin.dzht.top");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).contains("content-type", "authorization");
    }
}
