package com.minor_project.optiserve_backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

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
}
