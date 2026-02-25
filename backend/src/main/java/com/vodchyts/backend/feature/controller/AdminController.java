package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.CreateUserRequest;
import com.vodchyts.backend.feature.dto.PagedResponse;
import com.vodchyts.backend.feature.dto.UpdateUserRequest;
import com.vodchyts.backend.feature.dto.UserResponse;
import com.vodchyts.backend.feature.service.AdminService;
import com.vodchyts.backend.feature.service.AuditHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AuditHelper auditHelper;

    public AdminController(AdminService adminService, AuditHelper auditHelper) {
        this.adminService = adminService;
        this.auditHelper = auditHelper;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponse> createUser(@Valid @RequestBody Mono<CreateUserRequest> request, ServerWebExchange exchange) {
        return request.flatMap(adminService::createUser)
                .flatMap(user -> {
                    // Аудит создания
                    auditHelper.auditCreate("Users", user.getUserID(), user, exchange).subscribe();
                    return adminService.mapUserToUserResponse(user);
                });
    }

    @GetMapping("/users")
    public Mono<PagedResponse<UserResponse>> getAllUsers(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size) {
        List<String> sortParams = exchange.getRequest().getQueryParams().get("sort");
        String role = exchange.getRequest().getQueryParams().getFirst("role");
        return adminService.getAllUsers(role, sortParams, page, size);
    }

    @DeleteMapping("/users/{userId}")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable Integer userId, ServerWebExchange exchange) {
        return adminService.getAllUsers(null, null, 0, 1000)
                .flatMap(paged -> {
                    // Находим удаляемую запись для аудита
                    UserResponse userToDelete = paged.content().stream()
                            .filter(u -> u.userID().equals(userId))
                            .findFirst()
                            .orElse(null);
                    
                    return adminService.deleteUser(userId)
                            .then(Mono.fromRunnable(() -> {
                                // Аудит удаления
                                auditHelper.auditDelete("Users", userId, userToDelete, exchange).subscribe();
                            }))
                            .thenReturn(ResponseEntity.noContent().build());
                });
    }

    @PutMapping("/users/{userId}")
    public Mono<UserResponse> updateUser(@PathVariable Integer userId, @Valid @RequestBody Mono<UpdateUserRequest> request, ServerWebExchange exchange) {
        return adminService.getAllUsers(null, null, 0, 1000)
                .flatMap(paged -> {
                    // Находим старую версию для аудита
                    UserResponse oldUser = paged.content().stream()
                            .filter(u -> u.userID().equals(userId))
                            .findFirst()
                            .orElse(null);
                    
                    return request.flatMap(req -> adminService.updateUser(userId, req)
                            .flatMap(updatedUser -> {
                                // Аудит обновления
                                auditHelper.auditUpdate("Users", userId, oldUser, updatedUser, exchange).subscribe();
                                return Mono.just(updatedUser);
                            }));
                });
    }

}