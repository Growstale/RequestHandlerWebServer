package com.vodchyts.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RateLimitingFilter implements WebFilter {

    private final RateLimitingService rateLimitingService;

    public RateLimitingFilter(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");

        if (ip == null || ip.isEmpty()) {
            ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        } else {
            ip = ip.split(",")[0].trim();
        }
        String path = exchange.getRequest().getURI().getPath();

        io.github.bucket4j.Bucket bucket;

        if (path.startsWith("/api/auth/login")) {
            bucket = rateLimitingService.resolveLoginBucket(ip);
        } else {
            bucket = rateLimitingService.resolveGeneralBucket(ip);
        }

        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        } else {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
    }
}