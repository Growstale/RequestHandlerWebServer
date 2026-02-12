package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.security.JwtUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuditHelper {

    private final AuditService auditService;
    private final JwtUtils jwtUtils;
    private final UserService userService;

    public AuditHelper(AuditService auditService, JwtUtils jwtUtils, UserService userService) {
        this.auditService = auditService;
        this.jwtUtils = jwtUtils;
        this.userService = userService;
    }

    public Mono<Void> auditAction(String action, String tableName, Integer recordID, 
                                  Object oldValue, Object newValue, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String ipAddress = getClientIP(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String endpoint = request.getURI().getPath();
        String method = request.getMethod().name();

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtUtils.validateToken(token)) {
                    String userLogin = jwtUtils.getUsernameFromToken(token);
                    return userService.findByLogin(userLogin)
                            .flatMap(user -> {
                                Integer userID = user.getUserID();
                                return auditService.audit(action, tableName, recordID, oldValue, newValue,
                                        userID, userLogin, ipAddress, userAgent, endpoint, method);
                            })
                            .onErrorResume(e -> {
                                // Если не удалось найти пользователя, логируем без userID
                                return auditService.audit(action, tableName, recordID, oldValue, newValue,
                                        null, userLogin, ipAddress, userAgent, endpoint, method);
                            });
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        return auditService.audit(action, tableName, recordID, oldValue, newValue,
                null, null, ipAddress, userAgent, endpoint, method);
    }

    public Mono<Void> auditCreate(String tableName, Integer recordID, Object newValue, ServerWebExchange exchange) {
        return auditAction("CREATE", tableName, recordID, null, newValue, exchange);
    }

    public Mono<Void> auditUpdate(String tableName, Integer recordID, Object oldValue, Object newValue, ServerWebExchange exchange) {
        return auditAction("UPDATE", tableName, recordID, oldValue, newValue, exchange);
    }

    public Mono<Void> auditDelete(String tableName, Integer recordID, Object oldValue, ServerWebExchange exchange) {
        return auditAction("DELETE", tableName, recordID, oldValue, null, exchange);
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

