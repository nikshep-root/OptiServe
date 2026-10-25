package com.minor_project.optiserve_backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void returnsSafeInternalServerErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/example");

        var response = exceptionHandler.handleUnexpectedException(
                new IllegalStateException("internal detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .extracting(ApiErrorResponse::message, ApiErrorResponse::path, ApiErrorResponse::fieldErrors)
                .containsExactly("An unexpected error occurred.", "/api/example", java.util.Map.of());
    }

    @Test
    void returnsStandardNotFoundResponseForMissingRoutes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/env");

        var response = exceptionHandler.handleMissingRoute(
                new NoResourceFoundException(HttpMethod.GET, "/actuator/env", "/actuator/env"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .extracting(ApiErrorResponse::message, ApiErrorResponse::path, ApiErrorResponse::fieldErrors)
                .containsExactly("Resource not found.", "/actuator/env", java.util.Map.of());
    }
}
