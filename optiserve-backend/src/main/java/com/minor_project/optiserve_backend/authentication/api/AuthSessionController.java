package com.minor_project.optiserve_backend.authentication.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthSessionController {

    @GetMapping("/me")
    public AuthSessionResponse currentUser(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")) ? "ADMIN" : "USER";
        return new AuthSessionResponse(authentication.getName(), role);
    }
}
