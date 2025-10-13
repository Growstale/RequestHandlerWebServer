package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.CreateUserRequest;
import com.vodchyts.backend.feature.dto.UpdateUserRequest;
import com.vodchyts.backend.feature.dto.UserResponse;
import com.vodchyts.backend.feature.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('RetailAdmin')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponse> createUser(@Valid @RequestBody Mono<CreateUserRequest> request) {
        return request.flatMap(adminService::createUser)
                .flatMap(user -> adminService.getAllUsers(null, null)
                        .filter(u -> u.userID().equals(user.getUserID()))
                        .next());
    }

    @GetMapping("/users")
    public Flux<UserResponse> getAllUsers(@RequestParam(required = false) String role,
                                          @RequestParam(required = false) List<String> sort) {
        return adminService.getAllUsers(role, sort);
    }

    @DeleteMapping("/users/{userId}")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable Integer userId) {
        return adminService.deleteUser(userId).thenReturn(ResponseEntity.noContent().build());
    }

    @PutMapping("/users/{userId}")
    public Mono<UserResponse> updateUser(@PathVariable Integer userId, @Valid @RequestBody Mono<UpdateUserRequest> request) {
        return request.flatMap(req -> adminService.updateUser(userId, req));
    }

}