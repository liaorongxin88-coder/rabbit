package com.rabbit.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

public class PlatformAdminJwtUtil {
    private static final String SUBJECT_PREFIX = "platform-admin:";

    private final Key key;
    private final long expireMillis;

    public PlatformAdminJwtUtil(String secret, long expireSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireSeconds * 1000L;
    }

    public String generateToken(long adminId, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireMillis);
        return Jwts.builder()
                .setSubject(SUBJECT_PREFIX + adminId)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public PlatformAdminToken parse(String token) {
        Jws<Claims> claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
        String sub = claims.getBody().getSubject();
        if (sub == null || !sub.startsWith(SUBJECT_PREFIX)) {
            throw new IllegalArgumentException("invalid platform admin token subject");
        }
        Long adminId = Long.valueOf(sub.substring(SUBJECT_PREFIX.length()));
        String role = claims.getBody().get("role", String.class);
        return new PlatformAdminToken(adminId, role);
    }

    public static class PlatformAdminToken {
        private final Long adminId;
        private final String role;

        public PlatformAdminToken(Long adminId, String role) {
            this.adminId = adminId;
            this.role = role;
        }

        public Long getAdminId() {
            return adminId;
        }

        public String getRole() {
            return role;
        }
    }
}
