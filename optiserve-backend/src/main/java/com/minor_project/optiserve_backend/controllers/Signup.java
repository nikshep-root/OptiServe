package com.minor_project.optiserve_backend.controllers;

import com.minor_project.optiserve_backend.Services.Login_service;
import com.minor_project.optiserve_backend.operations.domain.Customer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
public class Signup {

    private final Login_service login;
    public Signup(Login_service login)
    {
        this.login=login;
    }
    @PostMapping("/register")
    public ResponseEntity<Customer> signup(@RequestBody @Valid Customer customer)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(login.save(customer));
    }
}
