package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.service.AuditHelper;
import com.vodchyts.backend.feature.service.RequestService;
import com.vodchyts.backend.feature.service.UserService;
import jakarta.validation.Valid;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;
    private final UserService userService;
    private final AuditHelper auditHelper;

    public RequestController(RequestService requestService, UserService userService, AuditHelper auditHelper) {
        this.requestService = requestService;
        this.userService = userService;
        this.auditHelper = auditHelper;
    }

    @GetMapping
    public Mono<PagedResponse<RequestResponse>> getRequests(
            ServerWebExchange exchange,
            @AuthenticationPrincipal String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) boolean archived,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) Integer shopId,
            @RequestParam(required = false) Integer workCategoryId,
            @RequestParam(required = false) Integer urgencyId,
            @RequestParam(required = false) Integer contractorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        List<String> sortParams = exchange.getRequest().getQueryParams().get("sort");
        return requestService.getAllRequests(archived, searchTerm, shopId, workCategoryId, urgencyId, contractorId, status, overdue, startDate, endDate, sortParams, page, size, username);
    }


    @PostMapping
    @PreAuthorize("hasRole('RetailAdmin')")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RequestResponse> createRequest(@Valid @RequestBody Mono<CreateRequestRequest> requestDto, @AuthenticationPrincipal String username, ServerWebExchange exchange) {
        return userService.findByLogin(username)
                .flatMap(user -> requestDto.flatMap(dto -> requestService.createAndEnrichRequest(dto, user.getUserID())
                        .flatMap(request -> {
                            // Аудит создания
                            auditHelper.auditCreate("Requests", request.requestID(), request, exchange).subscribe();
                            return Mono.just(request);
                        })));
    }


    @PutMapping("/{requestId}")
    @PreAuthorize("hasRole('RetailAdmin')")
    public Mono<RequestResponse> updateRequest(@PathVariable Integer requestId, @Valid @RequestBody Mono<UpdateRequestRequest> requestDto, @AuthenticationPrincipal String username, ServerWebExchange exchange) {
        return requestDto.flatMap(dto ->
                requestService.getRequestById(requestId)
                        .map(java.util.Optional::of)
                        .defaultIfEmpty(java.util.Optional.empty())
                        .onErrorReturn(java.util.Optional.empty())
                        .flatMap(oldRequestOpt ->
                                requestService.updateAndEnrichRequest(requestId, dto)
                                        .doOnSuccess(updatedRequest -> {
                                            // Аудит обновления
                                            auditHelper.auditUpdate("Requests", requestId, oldRequestOpt.orElse(null), updatedRequest, exchange).subscribe();
                                        })
                        )
        );
    }

    @DeleteMapping("/{requestId}")
    @PreAuthorize("hasRole('RetailAdmin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteRequest(@PathVariable Integer requestId, ServerWebExchange exchange) {
        return requestService.getRequestById(requestId)
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .onErrorReturn(java.util.Optional.empty())
                .flatMap(requestToDeleteOpt ->
                        requestService.deleteRequest(requestId)
                                .doOnSuccess(v -> {
                                    // Аудит удаления
                                    auditHelper.auditDelete("Requests", requestId, requestToDeleteOpt.orElse(null), exchange).subscribe();
                                })
                );
    }


    @GetMapping("/{requestId}/comments")
    public Flux<CommentResponse> getComments(@PathVariable Integer requestId) {
        return requestService.getCommentsForRequest(requestId);
    }

    @PostMapping("/{requestId}/comments")
    @PreAuthorize("hasAnyRole('RetailAdmin', 'Contractor')")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CommentResponse> addComment(@PathVariable Integer requestId, @Valid @RequestBody Mono<CreateCommentRequest> commentDto, @AuthenticationPrincipal String username, ServerWebExchange exchange) {
        return userService.findByLogin(username)
                .flatMap(user -> commentDto.flatMap(dto ->
                        requestService.addCommentToRequest(requestId, dto, user.getUserID())
                                .doOnSuccess(comment -> {
                                    auditHelper.auditCreate("RequestComments", comment.commentID(), comment, exchange).subscribe();
                                })
                ));
    }


    @GetMapping("/{requestId}/photos")
    public Flux<ResponseEntity<byte[]>> getPhotos(@PathVariable Integer requestId) {
        return requestService.getPhotosForRequest(requestId)
                .map(imageData -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(imageData));
    }

    @PostMapping(value = "/{requestId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('RetailAdmin', 'Contractor')")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> uploadPhotos(@PathVariable Integer requestId,
                                   @RequestPart("files") Flux<FilePart> filePartFlux,
                                   @AuthenticationPrincipal String username,
                                   ServerWebExchange exchange) {
        return userService.findByLogin(username)
                .flatMap(user -> requestService.addPhotosToRequest(requestId, filePartFlux, user.getUserID())
                        .doOnSuccess(v -> {
                            auditHelper.auditCreate("RequestPhotos", requestId, "Загружены новые фотографии к заявке", exchange).subscribe();
                        })
                );
    }

    @PutMapping("/{requestId}/complete")
    @PreAuthorize("hasRole('Contractor')")
    public Mono<RequestResponse> completeRequest(@PathVariable Integer requestId, @AuthenticationPrincipal String username, ServerWebExchange exchange) {
        return userService.findByLogin(username)
                .flatMap(user -> requestService.getRequestById(requestId)
                        .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()).onErrorReturn(java.util.Optional.empty())
                        .flatMap(oldReqOpt -> requestService.completeRequest(requestId, user.getUserID())
                                .doOnSuccess(updatedReq -> {
                                    auditHelper.auditUpdate("Requests", requestId, oldReqOpt.orElse(null), updatedReq, exchange).subscribe();
                                })
                        )
                );
    }


    @GetMapping("/{requestId}/photos/ids")
    public Flux<Integer> getPhotoIds(@PathVariable Integer requestId) {
        return requestService.getPhotoIdsForRequest(requestId);
    }

    @GetMapping("/photos/{photoId}")
    public Mono<ResponseEntity<byte[]>> getPhoto(@PathVariable Integer photoId) {
        return requestService.getPhotoById(photoId)
                .map(imageData -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(imageData))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('RetailAdmin')")
    public Mono<Void> deletePhoto(@PathVariable Integer photoId, ServerWebExchange exchange) {
        return requestService.deletePhoto(photoId)
                .doOnSuccess(v -> {
                    auditHelper.auditDelete("RequestPhotos", photoId, "Фотография удалена", exchange).subscribe();
                });
    }

    @PutMapping("/{requestId}/restore")
    @PreAuthorize("hasRole('RetailAdmin')")
    public Mono<RequestResponse> restoreRequest(@PathVariable Integer requestId, @RequestBody(required = false) Mono<Void> body, ServerWebExchange exchange) {
        return requestService.getRequestById(requestId)
                .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()).onErrorReturn(java.util.Optional.empty())
                .flatMap(oldReqOpt -> requestService.restoreRequest(requestId)
                        .doOnSuccess(updatedReq -> {
                            auditHelper.auditUpdate("Requests", requestId, oldReqOpt.orElse(null), updatedReq, exchange).subscribe();
                        })
                );
    }

    @DeleteMapping("/comments/{commentId}")
    @PreAuthorize("hasRole('RetailAdmin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteComment(@PathVariable Integer commentId, ServerWebExchange exchange) {
        return requestService.deleteComment(commentId)
                .doOnSuccess(v -> {
                    auditHelper.auditDelete("RequestComments", commentId, "Комментарий удален", exchange).subscribe();
                });
    }
}