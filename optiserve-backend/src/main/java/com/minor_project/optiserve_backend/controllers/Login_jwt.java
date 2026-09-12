package com.minor_project.optiserve_backend.controllers;

import com.minor_project.optiserve_backend.Services.Login_service;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
public class Login_jwt {

    private final Login_service login;

    public Login_jwt(Login_service login) {
        this.login = login;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> jwt_auth(@RequestBody @Valid LoginRequest request) {
        Map<String, String> authResult = login.verify(request.username(), request.password());
        AuthResponse response = new AuthResponse(
                authResult.get("token"),
                authResult.get("username"),
                authResult.get("role")
        );
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
