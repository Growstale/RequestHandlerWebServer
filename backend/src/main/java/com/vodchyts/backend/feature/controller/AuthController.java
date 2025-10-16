package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.exception.UnauthorizedException;
import com.vodchyts.backend.feature.dto.LoginRequest;
import com.vodchyts.backend.feature.dto.LoginResponse;
import com.vodchyts.backend.feature.service.AuthService;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@RequestBody LoginRequest request,
                                                     ServerWebExchange exchange) {
        return authService.login(request.login(), request.password())
                .map(loginResponse -> {
                    ResponseCookie cookie = ResponseCookie.from("refreshToken", loginResponse.refreshToken())
                            .httpOnly(true)
                            .path("/")
                            .maxAge(Duration.ofDays(7))
                            .build();
                    exchange.getResponse().addCookie(cookie);

                    return ResponseEntity.ok(
                            new LoginResponse(
                                    loginResponse.accessToken()
                            )
                    );
                });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<LoginResponse>> refresh(ServerWebExchange exchange) {
        var cookie = exchange.getRequest().getCookies().getFirst("refreshToken");
        if (cookie == null) {
            return Mono.error(new UnauthorizedException("Refresh token not found"));
        }
        String refreshToken = cookie.getValue();

        return authService.refresh(refreshToken)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange) {
        var cookie = exchange.getRequest().getCookies().getFirst("refreshToken");
        if (cookie == null) {
            return Mono.error(new UnauthorizedException("Токен обновления не найден"));
        }
        String refreshToken = cookie.getValue();

        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        exchange.getResponse().addCookie(deleteCookie);

        return authService.logout(refreshToken)
                .thenReturn(ResponseEntity.ok().build());
    }
}
