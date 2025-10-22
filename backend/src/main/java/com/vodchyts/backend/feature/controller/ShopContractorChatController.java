package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.entity.ShopContractorChat;
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

    public ShopContractorChatController(ShopContractorChatService chatService) {
        this.chatService = chatService;
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
    public Mono<ShopContractorChat> createChat(@Valid @RequestBody Mono<CreateShopContractorChatRequest> request) {
        return request.flatMap(chatService::createChat);
    }

    @PutMapping("/{chatId}")
    public Mono<ShopContractorChat> updateChat(@PathVariable Integer chatId, @Valid @RequestBody Mono<UpdateShopContractorChatRequest> request) {
        return request.flatMap(req -> chatService.updateChat(chatId, req));
    }

    @DeleteMapping("/{chatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteChat(@PathVariable Integer chatId) {
        return chatService.deleteChat(chatId);
    }

    @GetMapping("/exists")
    public Mono<Boolean> checkChatExists(@RequestParam Integer shopId, @RequestParam Integer contractorId) {
        return chatService.checkIfExists(shopId, contractorId);
    }
}