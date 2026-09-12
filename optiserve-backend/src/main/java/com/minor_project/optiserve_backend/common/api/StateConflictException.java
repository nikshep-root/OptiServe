package com.minor_project.optiserve_backend.common.api;

public class StateConflictException extends IllegalStateException {

    public StateConflictException(String message, IllegalStateException cause) {
        super(message, cause);
    }
}
