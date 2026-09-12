package com.minor_project.optiserve_backend.controllers;

public record AuthResponse(
        String token,
        String username,
        String role
) {}
