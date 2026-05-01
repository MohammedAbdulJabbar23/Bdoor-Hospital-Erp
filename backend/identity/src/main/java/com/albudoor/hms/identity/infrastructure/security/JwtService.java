package com.albudoor.hms.identity.infrastructure.security;

import com.albudoor.hms.identity.domain.Role;
import com.albudoor.hms.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration ttl;
    private final String issuer;

    public JwtService(
            @Value("${hms.security.jwt.secret}") String base64Secret,
            @Value("${hms.security.jwt.ttl:PT8H}") Duration ttl,
            @Value("${hms.security.jwt.issuer:hms.albudoor}") String issuer
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public Issued issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("name", user.getFullName())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new Issued(token, expiresAt);
    }

    public Parsed parse(String token) {
        Claims claims = Jwts.parser()
                .requireIssuer(issuer)
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        @SuppressWarnings("unchecked")
        List<String> roleNames = claims.get("roles", List.class);
        List<Role> roles = roleNames.stream().map(Role::valueOf).toList();
        return new Parsed(
                UUID.fromString(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("name", String.class),
                roles
        );
    }

    public record Issued(String token, Instant expiresAt) {}
    public record Parsed(UUID userId, String username, String fullName, List<Role> roles) {}
}
