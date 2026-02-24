package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.entity.ShopContractorChat;
import com.vodchyts.backend.feature.service.AuditHelper;
import com.vodchyts.backend.feature.service.ShopContractorChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shop-contractor-chats")
@PreAuthorize("hasRole('RetailAdmin')")
public class ShopContractorChatController {

    private final ShopContractorChatService chatService;
    private final AuditHelper auditHelper;

    public ShopContractorChatController(ShopContractorChatService chatService, AuditHelper auditHelper) {
        this.chatService = chatService;
        this.auditHelper = auditHelper;
    }

    @GetMapping
    public Mono<PagedResponse<ShopContractorChatResponse>> getAllChats(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size) {
        List<String> sortParams = exchange.getRequest().getQueryParams().get("sort");
        return chatService.getAllChats(sortParams, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ShopContractorChat> createChat(@Valid @RequestBody Mono<CreateShopContractorChatRequest> request, ServerWebExchange exchange) {
        return request.flatMap(chatService::createChat)
                .flatMap(chat -> {
                    // Аудит создания
                    auditHelper.auditCreate("ShopContractorChats", chat.getChatID(), chat, exchange).subscribe();
                    return Mono.just(chat);
                });
    }

    @PutMapping("/{chatId}")
    public Mono<ShopContractorChat> updateChat(@PathVariable Integer chatId, @Valid @RequestBody Mono<UpdateShopContractorChatRequest> request, ServerWebExchange exchange) {
        return chatService.getAllChats(null, 0, 1000)
                .flatMap(paged -> {
                    // Находим старую версию для аудита
                    ShopContractorChatResponse oldChat = paged.content().stream()
                            .filter(c -> c.chatID().equals(chatId))
                            .findFirst()
                            .orElse(null);
                    
                    return request.flatMap(req -> chatService.updateChat(chatId, req)
                            .flatMap(updatedChat -> {
                                // Аудит обновления
                                auditHelper.auditUpdate("ShopContractorChats", chatId, oldChat, updatedChat, exchange).subscribe();
                                return Mono.just(updatedChat);
                            }));
                });
    }

    @DeleteMapping("/{chatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteChat(@PathVariable Integer chatId, ServerWebExchange exchange) {
        return chatService.getAllChats(null, 0, 1000)
                .flatMap(paged -> {
                    // Находим удаляемую запись для аудита
                    ShopContractorChatResponse chatToDelete = paged.content().stream()
                            .filter(c -> c.chatID().equals(chatId))
                            .findFirst()
                            .orElse(null);
                    
                    return chatService.deleteChat(chatId)
                            .then(Mono.fromRunnable(() -> {
                                // Аудит удаления
                                auditHelper.auditDelete("ShopContractorChats", chatId, chatToDelete, exchange).subscribe();
                            }));
                });
    }

    @GetMapping("/exists")
    public Mono<Boolean> checkChatExists(@RequestParam Integer shopId, @RequestParam Integer contractorId) {
        return chatService.checkIfExists(shopId, contractorId);
    }
}