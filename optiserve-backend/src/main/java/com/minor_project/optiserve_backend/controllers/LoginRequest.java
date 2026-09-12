package com.minor_project.optiserve_backend.controllers;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username or email is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}
