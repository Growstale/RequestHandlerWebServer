package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.service.AdminService;
import com.vodchyts.backend.feature.service.RequestService;
import com.vodchyts.backend.feature.service.ShopContractorChatService;
import com.vodchyts.backend.feature.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/bot")
public class BotController {

    private final UserService userService;
    private final AdminService adminService;
    private final ShopContractorChatService chatService;
    private final RequestService requestService;

    public BotController(UserService userService, AdminService adminService, ShopContractorChatService chatService, RequestService requestService) {
        this.userService = userService;
        this.adminService = adminService;
        this.chatService = chatService;
        this.requestService = requestService;
    }

    @GetMapping("/user/telegram/{telegramId}")
    public Mono<ResponseEntity<UserResponse>> getUserByTelegramId(@PathVariable Long telegramId) {
        return userService.findByTelegramId(telegramId)
                .flatMap(adminService::mapUserToUserResponse)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/chat/{telegramId}")
    public Mono<ResponseEntity<ShopContractorChatResponse>> getChatInfoByTelegramId(@PathVariable Long telegramId) {
        return chatService.findByTelegramId(telegramId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RequestResponse> createRequestFromBot(@Valid @RequestBody Mono<CreateRequestFromBotRequest> requestDto) {
        return requestDto.flatMap(requestService::createAndEnrichRequestFromBot);
    }
}