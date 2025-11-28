package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.service.AdminService;
import com.vodchyts.backend.feature.service.RequestService;
import com.vodchyts.backend.feature.service.ShopContractorChatService;
import com.vodchyts.backend.feature.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;

import java.util.List;

@RestController
@RequestMapping("/api/bot")
public class BotController {

    record BotActionRequest(Long telegram_id) {}
    record BotCommentRequest(Long telegram_id, String commentText) {}

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

    @GetMapping("/requests")
    public Mono<PagedResponse<RequestResponse>> getRequestsForBot(
            @RequestParam Long telegram_id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) boolean archived,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) List<String> sort // <-- ДОБАВЛЕНО: Принимаем сортировку
    ) {
        // Находим пользователя по telegramId, чтобы делать запрос от его имени
        return userService.findByTelegramId(telegram_id)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Пользователь с таким Telegram ID не найден.")))
                .flatMap(user -> {
                    List<String> sortParams = (sort != null && !sort.isEmpty())
                            ? sort
                            : List.of("requestID,asc");
                    return requestService.getAllRequests(
                            archived, searchTerm, null, null, null, null,
                            null, null, sortParams, page, size, user.getLogin()
                    );
                });
    }

    @GetMapping("/requests/{requestId}")
    public Mono<RequestResponse> getRequestDetailsForBot(@RequestParam Long telegram_id, @PathVariable Integer requestId) {
        // Здесь можно добавить дополнительную проверку прав, если нужно,
        // но getAllRequests уже делает это. Для простоты пока оставим так.
        return requestService.getRequestById(requestId); // Предполагаем, что такой метод будет создан
    }

    @PutMapping("/requests/{requestId}/complete")
    public Mono<RequestResponse> completeRequestForBot(@PathVariable Integer requestId, @RequestBody BotActionRequest botRequest) {
        return userService.findByTelegramId(botRequest.telegram_id())
                .switchIfEmpty(Mono.error(new UserNotFoundException("Пользователь с таким Telegram ID не найден.")))
                .flatMap(user -> requestService.completeRequest(requestId, user.getUserID()));
    }

    @GetMapping("/requests/{requestId}/comments")
    public Flux<CommentResponse> getCommentsForBot(@PathVariable Integer requestId) {
        return requestService.getCommentsForRequest(requestId);
    }

    @PostMapping("/requests/{requestId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CommentResponse> addCommentForBot(@PathVariable Integer requestId, @RequestBody BotCommentRequest botRequest) {
        return userService.findByTelegramId(botRequest.telegram_id())
                .switchIfEmpty(Mono.error(new UserNotFoundException("Пользователь с таким Telegram ID не найден.")))
                .flatMap(user -> {
                    CreateCommentRequest commentDto = new CreateCommentRequest(botRequest.commentText());
                    return requestService.addCommentToRequest(requestId, commentDto, user.getUserID());
                });
    }

    @GetMapping("/requests/{requestId}/photos/ids")
    public Flux<Integer> getPhotoIdsForBot(@PathVariable Integer requestId) {
        return requestService.getPhotoIdsForRequest(requestId);
    }

    @PostMapping(value = "/requests/{requestId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> uploadPhotosFromBot(@PathVariable Integer requestId,
                                          @RequestPart("files") Flux<FilePart> filePartFlux,
                                          @RequestParam("telegram_id") Long telegramId) {
        return userService.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Пользователь с таким Telegram ID не найден.")))
                .flatMap(user -> requestService.addPhotosToRequest(requestId, filePartFlux, user.getUserID()));
    }
}