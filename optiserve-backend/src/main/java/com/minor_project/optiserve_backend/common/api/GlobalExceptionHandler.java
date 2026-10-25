package com.minor_project.optiserve_backend.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import com.minor_project.optiserve_backend.authentication.application.InvalidCredentialsException;
import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "Validation failed.", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Constraint validation failed.", request, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request body is invalid.", request, Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> handleMissingRoute(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Resource not found.", request, Map.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Invalid email or password.", request, Map.of());
    }

    @ExceptionHandler({ConflictException.class, StateConflictException.class, IllegalStateException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiErrorResponse> handleConflictException(
            Exception exception,
            HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "The request conflicts with existing data.", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Unhandled request exception; sanitized stack trace follows: {}", sanitizedStackTrace(exception));
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request, Map.of());
    }

    private static String sanitizedStackTrace(Throwable exception) {
        StringBuilder trace = new StringBuilder();
        Set<Throwable> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = exception;
        while (current != null && visited.add(current)) {
            if (!trace.isEmpty()) {
                trace.append("\nCaused by: ");
            }
            trace.append(current.getClass().getName());
            for (StackTraceElement element : current.getStackTrace()) {
                trace.append("\n  at ").append(element);
            }
            current = current.getCause();
        }
        return trace.toString();
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                Map.copyOf(fieldErrors));
        return ResponseEntity.status(status).body(body);
    }
}
