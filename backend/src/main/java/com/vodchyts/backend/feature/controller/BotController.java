package com.vodchyts.backend.feature.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/bot")
public class BotController {

    @GetMapping("/health")
    public Mono<String> checkHealth() {
        // Здесь вы можете разместить логику, которую будет вызывать ваш бот
        return Mono.just("{\"status\": \"Bot endpoint is active\"}");
    }

}
