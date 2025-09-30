package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.LoginRequest;
import com.vodchyts.backend.feature.dto.RegisterRequest;
import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.repository.RoleRepository;
import com.vodchyts.backend.feature.repository.UserRepository;
import com.vodchyts.backend.feature.service.AuthService;
import com.vodchyts.backend.security.JwtUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request.login(), request.password(), request.roleName()));
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.login(), request.password()));
    }
}
