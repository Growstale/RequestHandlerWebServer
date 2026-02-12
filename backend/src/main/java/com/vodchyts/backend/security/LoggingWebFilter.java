package com.vodchyts.backend.security;

import com.vodchyts.backend.feature.service.LoggingService;
import com.vodchyts.backend.feature.service.UserService;
import com.vodchyts.backend.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Order(-100)
public class LoggingWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingWebFilter.class);
    private final LoggingService loggingService;
    private final JwtUtils jwtUtils;
    private final UserService userService;

    public LoggingWebFilter(LoggingService loggingService, JwtUtils jwtUtils, UserService userService) {
        this.loggingService = loggingService;
        this.jwtUtils = jwtUtils;
        this.userService = userService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestID = UUID.randomUUID().toString();
        exchange.getAttributes().put("requestID", requestID);

        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String ipAddress = getClientIP(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");

        if (path.startsWith("/actuator") || path.startsWith("/favicon.ico")) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");

        // Используем Optional, чтобы Mono никогда не был пустым и zip не прерывался
        Mono<java.util.Optional<String>> userLoginMono = Mono.just(java.util.Optional.empty());
        Mono<java.util.Optional<Integer>> userIDMono = Mono.just(java.util.Optional.empty());

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtUtils.validateToken(token)) {
                    String login = jwtUtils.getUsernameFromToken(token);
                    userLoginMono = Mono.just(java.util.Optional.of(login));
                    userIDMono = userService.findByLogin(login)
                            .map(user -> java.util.Optional.of(user.getUserID()))
                            .onErrorReturn(java.util.Optional.empty());
                }
            } catch (Exception e) {
                // Игнорируем ошибки парсинга токена
            }
        }

        long startTime = System.currentTimeMillis();

        return Mono.zip(userIDMono, userLoginMono)
                .flatMap(tuple -> {
                    // Извлекаем значения из Optional
                    Integer userID = tuple.getT1().orElse(null);
                    String userLogin = tuple.getT2().orElse(null);

                    return chain.filter(exchange)
                            .doOnSuccess(aVoid -> {
                                ServerHttpResponse response = exchange.getResponse();
                                int statusCode = response.getStatusCode() != null ?
                                        response.getStatusCode().value() : 200;
                                long duration = System.currentTimeMillis() - startTime;
                                String message = String.format("%s %s - %d - %dms", method, path, statusCode, duration);

                                if (!"GET".equalsIgnoreCase(method) || statusCode >= 400) {
                                    if (statusCode >= 500) {
                                        loggingService.logError("HTTP_REQUEST", message, null, userID, userLogin, ipAddress, userAgent, path, method).subscribe();
                                    } else if (statusCode >= 400) {
                                        loggingService.logWarn("HTTP_REQUEST", message, userID, userLogin, ipAddress, userAgent, path, method).subscribe();
                                    } else {
                                        loggingService.logInfo("HTTP_REQUEST", message, userID, userLogin, ipAddress, userAgent, path, method).subscribe();
                                    }
                                }
                            })
                            .doOnError(error -> {
                                long duration = System.currentTimeMillis() - startTime;
                                String message = String.format("%s %s - ERROR - %dms - %s", method, path, duration, error.getMessage());
                                loggingService.logError("HTTP_REQUEST", message, error, userID, userLogin, ipAddress, userAgent, path, method).subscribe();
                            });
                });
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
}

