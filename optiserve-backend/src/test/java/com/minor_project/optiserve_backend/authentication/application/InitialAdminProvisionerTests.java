package com.minor_project.optiserve_backend.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minor_project.optiserve_backend.authentication.domain.AuthUser;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

class InitialAdminProvisionerTests {

    @Test
    void provisionsAdminWithEncodedPasswordWhenEnabledComponentRuns() throws Exception {
        AuthUserRepository users = org.mockito.Mockito.mock(AuthUserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        when(encoder.encode("safe-password")).thenReturn("encoded-hash");
        InitialAdminProvisioner provisioner = new InitialAdminProvisioner(
                users, encoder, "Owner@Example.com", "safe-password");

        provisioner.run(new DefaultApplicationArguments());

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("owner@example.com");
        assertThat(captor.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-hash");
    }

    @Test
    void doesNotOverwriteAnExistingAdmin() throws Exception {
        AuthUserRepository users = org.mockito.Mockito.mock(AuthUserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(users.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(AuthUser.provisionAdmin("owner@example.com", "existing-hash")));
        InitialAdminProvisioner provisioner = new InitialAdminProvisioner(
                users, encoder, "owner@example.com", "safe-password");

        provisioner.run(new DefaultApplicationArguments());

        verify(users, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void repeatedStartupProvisionsOnlyOnce() throws Exception {
        AuthUserRepository users = org.mockito.Mockito.mock(AuthUserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(users.findByEmail("owner@example.com"))
                .thenReturn(Optional.empty(), Optional.of(AuthUser.provisionAdmin("owner@example.com", "encoded-hash")));
        when(encoder.encode("safe-password")).thenReturn("encoded-hash");
        InitialAdminProvisioner provisioner = new InitialAdminProvisioner(
                users, encoder, "owner@example.com", "safe-password");

        provisioner.run(new DefaultApplicationArguments());
        provisioner.run(new DefaultApplicationArguments());

        verify(users).save(any(AuthUser.class));
        verify(encoder).encode("safe-password");
    }

    @Test
    void refusesToPromoteAnExistingCustomerAccount() {
        AuthUserRepository users = org.mockito.Mockito.mock(AuthUserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(users.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(AuthUser.create("owner@example.com", "existing-hash")));
        InitialAdminProvisioner provisioner = new InitialAdminProvisioner(
                users, encoder, "owner@example.com", "safe-password");

        assertThatThrownBy(() -> provisioner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to change its role");
        verify(users, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void rejectsMissingBootstrapCredentialsWithoutLoggingThem() {
        AuthUserRepository users = org.mockito.Mockito.mock(AuthUserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        InitialAdminProvisioner provisioner = new InitialAdminProvisioner(users, encoder, "", "");

        assertThatThrownBy(() -> provisioner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Initial administrator email configuration is invalid.");
        verify(users, never()).save(any());
    }

    @Test
    void rejectsMalformedEmailAndShortOrOverlongPasswords() {
        AuthUserRepository users = org.mockito.Mockito.mock(AuthUserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);

        assertThatThrownBy(() -> new InitialAdminProvisioner(users, encoder, "owner@", "safe-password")
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Initial administrator email configuration is invalid.");
        assertThatThrownBy(() -> new InitialAdminProvisioner(users, encoder, "owner@example.com", "short")
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Initial administrator password must be 8 to 72 UTF-8 bytes.");
        assertThatThrownBy(() -> new InitialAdminProvisioner(users, encoder, "owner@example.com", "é".repeat(37))
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Initial administrator password must be 8 to 72 UTF-8 bytes.");
        verify(users, never()).findByEmail(any());
        verify(users, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void adminBootstrapProfileRequiresTheExistingExplicitEnableFlag() {
        provisionerContext()
                .withPropertyValues(
                        "spring.profiles.active=admin-bootstrap",
                        "optiserve.admin.email=owner@example.com",
                        "optiserve.admin.password=safe-password")
                .run(context -> assertThat(context).doesNotHaveBean(InitialAdminProvisioner.class));
    }

    @Test
    void adminBootstrapAndLocalProfilesActivateOnlyWhenOptedIn() {
        for (String profile : new String[] {"admin-bootstrap", "local"}) {
            provisionerContext()
                    .withPropertyValues(
                            "spring.profiles.active=" + profile,
                            "optiserve.admin.bootstrap.enabled=true",
                            "optiserve.admin.email=owner@example.com",
                            "optiserve.admin.password=safe-password")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(InitialAdminProvisioner.class);
                    });
        }
    }

    @Test
    void productionProfileCannotActivateTheProvisionerEvenIfTheFlagIsSet() {
        provisionerContext()
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "optiserve.admin.bootstrap.enabled=true",
                        "optiserve.admin.email=owner@example.com",
                        "optiserve.admin.password=safe-password")
                .run(context -> assertThat(context).doesNotHaveBean(InitialAdminProvisioner.class));
    }

    private ApplicationContextRunner provisionerContext() {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        ApplicationConversionService.getSharedInstance()))
                .withBean(AuthUserRepository.class, () -> org.mockito.Mockito.mock(AuthUserRepository.class))
                .withBean(PasswordEncoder.class, () -> org.mockito.Mockito.mock(PasswordEncoder.class))
                .withUserConfiguration(ProvisionerConfiguration.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InitialAdminProvisioner.class)
    static class ProvisionerConfiguration {
    }
}
