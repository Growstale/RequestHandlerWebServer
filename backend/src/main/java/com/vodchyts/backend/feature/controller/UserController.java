package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.exception.UnauthorizedException;
import com.vodchyts.backend.feature.dto.UserInfoResponse;
import com.vodchyts.backend.feature.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.vodchyts.backend.feature.dto.UserResponse;
import com.vodchyts.backend.feature.service.AdminService;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    private final AdminService adminService;

    public UserController(UserService userService, AdminService adminService) {
        this.userService = userService;
        this.adminService = adminService;
    }

    @GetMapping("/whoami")
    public Mono<ResponseEntity<UserInfoResponse>> whoAmI(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(new UnauthorizedException("Заголовок Authorization отсутствует или недействителен"));
        }

        String accessToken = authHeader.substring(7);

        return userService.whoAmI(accessToken)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/contractors")
    public Flux<UserResponse> getContractors() {
        return adminService.getAllUsers("Contractor", null, 0, 1000)
                .flatMapMany(pagedResponse -> Flux.fromIterable(pagedResponse.content()));
    }

}
