package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.service.RequestService;
import com.vodchyts.backend.feature.service.UserService;
import jakarta.validation.Valid;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/requests")
@PreAuthorize("isAuthenticated()") // Доступ для всех авторизованных
public class RequestController {

    private final RequestService requestService;
    private final UserService userService; // Нужен для получения ID пользователя

    public RequestController(RequestService requestService, UserService userService) {
        this.requestService = requestService;
        this.userService = userService;
    }

    // Получение заявок
    @GetMapping
    public Mono<PagedResponse<RequestResponse>> getRequests(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) boolean archived,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) Integer shopId,
            @RequestParam(required = false) Integer workCategoryId,
            @RequestParam(required = false) Integer urgencyId,
            @RequestParam(required = false) Integer contractorId,
            @RequestParam(required = false) String status // <-- ДОБАВЬТЕ ЭТОТ ПАРАМЕТР
    ) {
        List<String> sortParams = exchange.getRequest().getQueryParams().get("sort");
        return requestService.getAllRequests(archived, searchTerm, shopId, workCategoryId, urgencyId, contractorId, status, sortParams, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // ↓↓↓ ИЗМЕНЕНИЕ 1 ↓↓↓
    public Mono<RequestResponse> createRequest(@Valid @RequestBody Mono<CreateRequestRequest> requestDto, @AuthenticationPrincipal String username) {
        return userService.findByLogin(username)
                .flatMap(user -> requestDto.flatMap(dto -> requestService.createAndEnrichRequest(dto, user.getUserID())));
    }


    // Обновление заявки
    @PutMapping("/{requestId}")
    @PreAuthorize("hasRole('RetailAdmin')") // Только админ может обновлять
    public Mono<RequestResponse> updateRequest(@PathVariable Integer requestId, @Valid @RequestBody Mono<UpdateRequestRequest> requestDto) {
        return requestDto.flatMap(dto -> requestService.updateAndEnrichRequest(requestId, dto));
    }

    // Удаление заявки
    @DeleteMapping("/{requestId}")
    @PreAuthorize("hasRole('RetailAdmin')") // Только админ
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteRequest(@PathVariable Integer requestId) {
        return requestService.deleteRequest(requestId);
    }

    // Получение комментариев
    @GetMapping("/{requestId}/comments")
    public Flux<CommentResponse> getComments(@PathVariable Integer requestId) {
        return requestService.getCommentsForRequest(requestId);
    }

    @PostMapping("/{requestId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    // ↓↓↓ ИЗМЕНЕНИЕ 2 ↓↓↓
    public Mono<CommentResponse> addComment(@PathVariable Integer requestId, @Valid @RequestBody Mono<CreateCommentRequest> commentDto, @AuthenticationPrincipal String username) {
        return userService.findByLogin(username) // Используем username напрямую
                .flatMap(user -> commentDto.flatMap(dto -> requestService.addCommentToRequest(requestId, dto, user.getUserID())));
    }


    // Получение фото
    @GetMapping("/{requestId}/photos")
    public Flux<ResponseEntity<byte[]>> getPhotos(@PathVariable Integer requestId) {
        return requestService.getPhotosForRequest(requestId)
                .map(imageData -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(imageData));
    }

    @PostMapping(value = "/{requestId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> uploadPhotos(@PathVariable Integer requestId, @RequestPart("files") Flux<FilePart> filePartFlux) {

        // Преобразуем поток файлов в поток массивов байт (один массив на один файл)
        Flux<byte[]> imagesDataFlux = filePartFlux.flatMap(filePart ->
                filePart.content()
                        // 1. Собираем все чанки (DataBuffer) одного файла в список
                        .collectList()
                        // 2. Объединяем их в один большой DataBuffer
                        .mapNotNull(dataBuffers -> {
                            if (dataBuffers.isEmpty()) {
                                return null; // Пропускаем пустые файлы
                            }
                            // Объединяем все буферы в один
                            DataBuffer joinedBuffer = dataBuffers.getFirst().factory().join(dataBuffers);
                            // Освобождаем исходные буферы, чтобы избежать утечек памяти
                            dataBuffers.forEach(DataBufferUtils::release);
                            return joinedBuffer;
                        })
                        .filter(Objects::nonNull) // Убираем пустые файлы
                        // 3. Преобразуем итоговый DataBuffer в массив байт
                        .map(dataBuffer -> {
                            assert dataBuffer != null;
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer); // Финально освобождаем объединенный буфер
                            return bytes;
                        })
        );

        return requestService.addPhotosToRequest(requestId, imagesDataFlux);
    }



    @GetMapping("/{requestId}/photos/ids")
    public Flux<Integer> getPhotoIds(@PathVariable Integer requestId) {
        return requestService.getPhotoIdsForRequest(requestId);
    }

    // ↓↓↓ ДОБАВЬТЕ НОВЫЙ GET-метод для одного фото ↓↓↓
    @GetMapping("/photos/{photoId}")
    public Mono<ResponseEntity<byte[]>> getPhoto(@PathVariable Integer photoId) {
        return requestService.getPhotoById(photoId)
                .map(imageData -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(imageData))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('RetailAdmin')") // Только админ может удалять
    public Mono<Void> deletePhoto(@PathVariable Integer photoId) {
        return requestService.deletePhoto(photoId);
    }

    @PutMapping("/{requestId}/restore")
    @PreAuthorize("hasRole('RetailAdmin')")
    public Mono<RequestResponse> restoreRequest(@PathVariable Integer requestId, @RequestBody(required = false) Mono<Void> body) {
        return requestService.restoreRequest(requestId);
    }
}