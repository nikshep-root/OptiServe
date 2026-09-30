package com.minor_project.optiserve_backend.authentication.api;

import com.minor_project.optiserve_backend.authentication.application.AuthApplicationService;
import com.minor_project.optiserve_backend.authentication.application.JwtService.IssuedJwt;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthApplicationService authService;

    public AuthController(AuthApplicationService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ResponseEntity<AuthTokenResponse> register(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(response(
                authService.register(request.email(), request.password())));
    }

    @PostMapping("/login")
    ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(response(authService.login(request.email(), request.password())));
    }

    private static AuthTokenResponse response(IssuedJwt token) {
        return new AuthTokenResponse(token.value(), "Bearer", token.expiresAt());
    }
}
