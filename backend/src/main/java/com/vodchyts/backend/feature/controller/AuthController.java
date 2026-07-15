package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.config.JwtConfig;
import com.vodchyts.backend.exception.UnauthorizedException;
import com.vodchyts.backend.feature.dto.LoginRequest;
import com.vodchyts.backend.feature.dto.LoginResponse;
import com.vodchyts.backend.feature.service.AuthService;
import com.vodchyts.backend.feature.service.LoggingService;
import com.vodchyts.backend.security.JwtUtils;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtConfig jwtConfig;
    private final LoggingService loggingService;
    private final JwtUtils jwtUtils;

    public AuthController(AuthService authService, JwtConfig jwtConfig, LoggingService loggingService, JwtUtils jwtUtils) {
        this.authService = authService;
        this.jwtConfig = jwtConfig;
        this.loggingService = loggingService;
        this.jwtUtils = jwtUtils;
    }

    private String getClientIP(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIP = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }


    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@RequestBody LoginRequest request,
                                                     ServerWebExchange exchange) {
        return authService.login(request.login(), request.password())
                .map(loginResponse -> {
                    ResponseCookie cookie = ResponseCookie.from("refreshToken", loginResponse.refreshToken())
                            .httpOnly(true)
                            .secure(true)
                            .path("/")
                            .maxAge(Duration.ofMillis(jwtConfig.getRefreshExpirationMs()))
                            .build();
                    exchange.getResponse().addCookie(cookie);

                    loggingService.logInfo("AuthController", "Успешный вход в систему", null, request.login(),
                            getClientIP(exchange.getRequest()), exchange.getRequest().getHeaders().getFirst("User-Agent"),
                            "/api/auth/login", "POST").subscribe();

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
                .map(tokens -> {
                    // Устанавливаем НОВУЮ куку с новым токеном
                    ResponseCookie newCookie = ResponseCookie.from("refreshToken", tokens.refreshToken())
                            .httpOnly(true)
                            .secure(true)
                            .path("/")
                            .maxAge(Duration.ofMillis(jwtConfig.getRefreshExpirationMs()))
                            .build();
                    exchange.getResponse().addCookie(newCookie);

                    return ResponseEntity.ok(new LoginResponse(tokens.accessToken()));
                });
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

        String username = null;
        try {
            if (jwtUtils.validateToken(refreshToken)) {
                username = jwtUtils.getUsernameFromToken(refreshToken);
            }
        } catch (Exception e) {
            // Игнорируем
        }

        final String finalUsername = username;
        return authService.logout(refreshToken)
                .doOnSuccess(v -> {
                    if (finalUsername != null) {
                        loggingService.logInfo("AuthController", "Выход из системы", null, finalUsername,
                                getClientIP(exchange.getRequest()), exchange.getRequest().getHeaders().getFirst("User-Agent"),
                                "/api/auth/logout", "POST").subscribe();
                    }
                })
                .thenReturn(ResponseEntity.ok().build());
    }
}
