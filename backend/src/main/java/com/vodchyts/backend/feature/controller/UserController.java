package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.exception.UnauthorizedException;
import com.vodchyts.backend.feature.dto.UserInfoResponse;
import com.vodchyts.backend.feature.service.AuthService;
import com.vodchyts.backend.feature.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
}
