package com.minor_project.optiserve_backend.authentication.application;

import com.minor_project.optiserve_backend.authentication.domain.AuthUser;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "optiserve.admin.bootstrap", name = "enabled", havingValue = "true")
public class InitialAdminProvisioner implements ApplicationRunner {

    private final AuthUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public InitialAdminProvisioner(
            AuthUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${optiserve.admin.email:}") String email,
            @Value("${optiserve.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank() || normalizedEmail.length() > 320 || normalizedEmail.contains(" ")
                || !normalizedEmail.contains("@")) {
            throw new IllegalStateException("Initial administrator email configuration is invalid.");
        }
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Initial administrator password must be 8 to 72 UTF-8 bytes.");
        }

        var existing = users.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            if (!"ADMIN".equals(existing.orElseThrow().getRole())) {
                throw new IllegalStateException(
                        "Initial administrator email belongs to an existing non-admin account; refusing to change its role.");
            }
            return;
        }

        users.save(AuthUser.provisionAdmin(normalizedEmail, passwordEncoder.encode(password)));
    }
}
