package com.minor_project.optiserve_backend.authentication.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(
            @Value("${security.jwt.secret}") String base64Secret,
            @Value("${security.jwt.expiration:PT1H}") Duration expiration) {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        if (expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("JWT expiration must be positive.");
        }
        this.expiration = expiration;
    }

    public IssuedJwt issue(String subject) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);
        String token = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedJwt(token, expiresAt);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload();
    }

    public record IssuedJwt(String value, Instant expiresAt) {
    }
}
