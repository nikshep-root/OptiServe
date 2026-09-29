package com.minor_project.optiserve_backend.authentication.application;

import com.minor_project.optiserve_backend.authentication.domain.AuthUser;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import com.minor_project.optiserve_backend.common.api.ConflictException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {

    private final AuthUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthApplicationService(
            AuthUserRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public JwtService.IssuedJwt register(String email, String password) {
        String normalizedEmail = normalize(email);
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes.");
        }
        if (repository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists.");
        }
        AuthUser user = repository.save(AuthUser.create(normalizedEmail, passwordEncoder.encode(password)));
        return jwtService.issue(user.getUsername());
    }

    @Transactional(readOnly = true)
    public JwtService.IssuedJwt login(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidCredentialsException();
        }
        AuthUser user = repository.findByEmail(normalize(email))
                .filter(account -> passwordEncoder.matches(password, account.getPassword()))
                .orElseThrow(InvalidCredentialsException::new);
        return jwtService.issue(user.getUsername());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
