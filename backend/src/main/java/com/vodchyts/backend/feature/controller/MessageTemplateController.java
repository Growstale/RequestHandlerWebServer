package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.CreateMessageTemplateRequest;
import com.vodchyts.backend.feature.dto.MessageTemplateResponse;
import com.vodchyts.backend.feature.service.AuditHelper;
import com.vodchyts.backend.feature.service.MessagingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/admin/message-templates")
@PreAuthorize("hasRole('RetailAdmin')")
public class MessageTemplateController {
    private final MessagingService messagingService;
    private final AuditHelper auditHelper;

    public MessageTemplateController(MessagingService messagingService, AuditHelper auditHelper) {
        this.messagingService = messagingService;
        this.auditHelper = auditHelper;
    }

    @GetMapping
    public Flux<MessageTemplateResponse> getAllTemplates() {
        return messagingService.getAllTemplates();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MessageTemplateResponse> createTemplate(
            @RequestPart("title") String title,
            @RequestPart(name = "message", required = false) String message,
            @RequestPart(name = "recipientChatIds", required = false) String recipientChatIdsStr,
            @RequestPart(name = "image", required = false) Mono<FilePart> imageFile,
            ServerWebExchange exchange) {

        List<Integer> recipientChatIds = (recipientChatIdsStr == null || recipientChatIdsStr.isEmpty()) ? List.of() :
                Arrays.stream(recipientChatIdsStr.split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());

        CreateMessageTemplateRequest request = new CreateMessageTemplateRequest(title, message, recipientChatIds);
        return messagingService.createTemplate(request, imageFile)
                .flatMap(template -> {
                    // Аудит создания
                    auditHelper.auditCreate("MessageTemplates", template.messageID(), template, exchange).subscribe();
                    return Mono.just(template);
                });
    }

    @PutMapping(value = "/{templateId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<MessageTemplateResponse> updateTemplate(
            @PathVariable Integer templateId,
            @RequestPart("title") String title,
            @RequestPart(name = "message", required = false) String message,
            @RequestPart(name = "recipientChatIds", required = false) String recipientChatIdsStr,
            @RequestPart(name = "image", required = false) Mono<FilePart> imageFile,
            ServerWebExchange exchange) {

        List<Integer> recipientChatIds = (recipientChatIdsStr == null || recipientChatIdsStr.isEmpty()) ? List.of() :
                Arrays.stream(recipientChatIdsStr.split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());

        CreateMessageTemplateRequest request = new CreateMessageTemplateRequest(title, message, recipientChatIds);
        return messagingService.getAllTemplates()
                .collectList()
                .flatMap(templates -> {
                    // Находим старую версию для аудита
                    MessageTemplateResponse oldTemplate = templates.stream()
                            .filter(t -> t.messageID().equals(templateId))
                            .findFirst()
                            .orElse(null);
                    
                    return messagingService.updateTemplate(templateId, request, imageFile)
                            .flatMap(updatedTemplate -> {
                                // Аудит обновления
                                auditHelper.auditUpdate("MessageTemplates", templateId, oldTemplate, updatedTemplate, exchange).subscribe();
                                return Mono.just(updatedTemplate);
                            });
                });
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTemplate(@PathVariable Integer templateId, ServerWebExchange exchange) {
        return messagingService.getAllTemplates()
                .collectList()
                .flatMap(templates -> {
                    // Находим удаляемую запись для аудита
                    MessageTemplateResponse templateToDelete = templates.stream()
                            .filter(t -> t.messageID().equals(templateId))
                            .findFirst()
                            .orElse(null);
                    
                    return messagingService.deleteTemplate(templateId)
                            .then(Mono.fromRunnable(() -> {
                                // Аудит удаления
                                auditHelper.auditDelete("MessageTemplates", templateId, templateToDelete, exchange).subscribe();
                            }));
                });
    }

    @GetMapping("/{templateId}/image")
    public Mono<ResponseEntity<byte[]>> getTemplateImage(@PathVariable Integer templateId) {
        return messagingService.getTemplateImage(templateId)
                .map(imageData -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(imageData))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{templateId}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTemplateImage(@PathVariable Integer templateId) {
        return messagingService.deleteTemplateImage(templateId);
    }
}