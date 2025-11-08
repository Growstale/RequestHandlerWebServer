package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.SendMessageRequest;
import com.vodchyts.backend.feature.service.MessagingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/messaging")
@PreAuthorize("hasRole('RetailAdmin')")
public class MessagingController {

    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> sendMessage(@Valid @RequestBody Mono<SendMessageRequest> request) {
        return request.flatMap(messagingService::sendMessage);
    }
}