package com.vodchyts.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class BotAuthenticationFilter implements WebFilter {

    @Value("${bot.api-key}")
    private String apiKey;

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String FORWARDED_HEADER = "X-Forwarded-For";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (path.startsWith("/api/bot")) {
            String requestApiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
            String forwardedFor = exchange.getRequest().getHeaders().getFirst(FORWARDED_HEADER);

            if (apiKey.equals(requestApiKey) && forwardedFor == null) {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_BOT"));
                var auth = new UsernamePasswordAuthenticationToken("telegram-bot", null, authorities);
                var context = new SecurityContextImpl(auth);

                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
            }
        }

        return chain.filter(exchange);
    }

}