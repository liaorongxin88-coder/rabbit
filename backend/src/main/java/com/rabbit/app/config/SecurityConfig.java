package com.rabbit.app.config;

import com.rabbit.app.common.TraceIdFilter;
import com.rabbit.app.security.PlatformAdminAuthenticationFilter;
import com.rabbit.app.security.PlatformAdminJwtUtil;
import com.rabbit.app.security.JwtAuthenticationFilter;
import com.rabbit.app.security.JwtUtil;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtUtil jwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expire-seconds}") long expireSeconds
    ) {
        return new JwtUtil(secret, expireSeconds);
    }

    @Bean
    public PlatformAdminJwtUtil platformAdminJwtUtil(
            @Value("${app.admin.jwt.secret:${app.jwt.secret}}") String secret,
            @Value("${app.admin.jwt.expire-seconds:${app.jwt.expire-seconds}}") long expireSeconds
    ) {
        return new PlatformAdminJwtUtil(secret, expireSeconds);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,https://admin.dzht.top,https://rabbit.host.dzht.top}") String allowedOrigins,
            @Value("${app.cors.allowed-origin-patterns:https://*.dzht.top}") String allowedOriginPatterns
    ) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(splitCsv(allowedOrigins));
        config.setAllowedOriginPatterns(splitCsv(allowedOriginPatterns));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-House-Id", "X-Trace-Id", "X-Requested-With"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static List<String> splitCsv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtUtil jwtUtil,
            PlatformAdminJwtUtil platformAdminJwtUtil,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/auth/login").permitAll()
                .anyRequest().permitAll());
        http.addFilterBefore(new TraceIdFilter(), UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(new PlatformAdminAuthenticationFilter(platformAdminJwtUtil), UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
