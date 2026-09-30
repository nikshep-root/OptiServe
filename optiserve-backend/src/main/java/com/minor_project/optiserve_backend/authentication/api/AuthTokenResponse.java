package com.minor_project.optiserve_backend.authentication.api;

import java.time.Instant;

public record AuthTokenResponse(String accessToken, String tokenType, Instant expiresAt) {
}
