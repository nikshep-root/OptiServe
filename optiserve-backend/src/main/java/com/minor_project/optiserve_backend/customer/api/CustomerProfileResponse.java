package com.minor_project.optiserve_backend.customer.api;

import java.util.UUID;

public record CustomerProfileResponse(UUID id, String name, String phone) {
}
