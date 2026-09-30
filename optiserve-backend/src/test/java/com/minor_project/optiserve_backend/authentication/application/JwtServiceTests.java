package com.minor_project.optiserve_backend.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTests {

    private static final String SECRET = "VGhpc0lzQVRlc3RPbmx5U2VjcmV0S2V5Rm9ySFRUUFN";

    @Test
    void issuesAndParsesSignedToken() {
        JwtService service = new JwtService(SECRET, Duration.ofHours(1));
        JwtService.IssuedJwt issued = service.issue("operator@example.com");

        assertThat(service.parseClaims(issued.value()).getSubject()).isEqualTo("operator@example.com");
        assertThat(issued.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService issuer = new JwtService(SECRET, Duration.ofHours(1));
        JwtService verifier = new JwtService(
                "QW5vdGhlclRlc3RPbmx5U2VjcmV0S2V5Rm9ySFRUUFN", Duration.ofHours(1));

        assertThatThrownBy(() -> verifier.parseClaims(issuer.issue("operator@example.com").value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtService(SECRET, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT expiration must be positive.");
    }
}
