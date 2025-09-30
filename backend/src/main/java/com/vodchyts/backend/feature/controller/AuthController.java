package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.LoginRequest;
import com.vodchyts.backend.feature.dto.RegisterRequest;
import com.vodchyts.backend.feature.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<String>> register(@RequestBody RegisterRequest request) {
        return authService.register(request.login(), request.password(), request.roleName())
                .map(ResponseEntity::ok);
    }


    @PostMapping("/login")
    public Mono<ResponseEntity<String>> login(@RequestBody LoginRequest request) {
        return authService.login(request.login(), request.password())
                .map(ResponseEntity::ok);
    }
}
