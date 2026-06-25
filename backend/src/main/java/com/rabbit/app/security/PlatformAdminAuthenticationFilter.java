package com.rabbit.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class PlatformAdminAuthenticationFilter extends OncePerRequestFilter {
    private final PlatformAdminJwtUtil jwtUtil;

    public PlatformAdminAuthenticationFilter(PlatformAdminJwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring("Bearer ".length());
            try {
                PlatformAdminJwtUtil.PlatformAdminToken adminToken = jwtUtil.parse(token);
                PlatformAdminContext.set(adminToken.getAdminId(), adminToken.getRole());
            } catch (Exception ignored) {
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            PlatformAdminContext.clear();
        }
    }
}
