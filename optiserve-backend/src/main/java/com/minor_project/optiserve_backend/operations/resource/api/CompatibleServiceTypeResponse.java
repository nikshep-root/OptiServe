package com.minor_project.optiserve_backend.operations.resource.api;

import java.util.UUID;

public record CompatibleServiceTypeResponse(UUID id, String name, boolean active) {
}
