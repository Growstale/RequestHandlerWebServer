package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.CreateMessageTemplateRequest;
import com.vodchyts.backend.feature.dto.MessageTemplateResponse;
import com.vodchyts.backend.feature.service.MessagingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/message-templates")
@PreAuthorize("hasRole('RetailAdmin')")
public class MessageTemplateController {

    private final MessagingService messagingService;

    public MessageTemplateController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping
    public Flux<MessageTemplateResponse> getAllTemplates() {
        return messagingService.getAllTemplates();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MessageTemplateResponse> createTemplate(@Valid @RequestBody Mono<CreateMessageTemplateRequest> request) {
        return request.flatMap(messagingService::createTemplate);
    }

    @PutMapping("/{templateId}")
    public Mono<MessageTemplateResponse> updateTemplate(@PathVariable Integer templateId, @Valid @RequestBody Mono<CreateMessageTemplateRequest> request) {
        return request.flatMap(dto -> messagingService.updateTemplate(templateId, dto));
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTemplate(@PathVariable Integer templateId) {
        return messagingService.deleteTemplate(templateId);
    }
}