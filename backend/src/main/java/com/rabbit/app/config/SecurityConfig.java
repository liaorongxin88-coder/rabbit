package com.rabbit.app.config;

import com.rabbit.app.common.TraceIdFilter;
import com.rabbit.app.security.PlatformAdminAuthenticationFilter;
import com.rabbit.app.security.PlatformAdminJwtUtil;
import com.rabbit.app.security.JwtAuthenticationFilter;
import com.rabbit.app.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil, PlatformAdminJwtUtil platformAdminJwtUtil) throws Exception {
        http.cors(Customizer.withDefaults());
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
