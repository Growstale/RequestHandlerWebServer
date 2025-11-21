package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.CreateNotificationRequest;
import com.vodchyts.backend.feature.dto.NotificationResponse;
import com.vodchyts.backend.feature.dto.PagedResponse;
import com.vodchyts.backend.feature.dto.UpdateNotificationRequest;
import com.vodchyts.backend.feature.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('RetailAdmin')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<NotificationResponse> createNotification(@Valid @RequestBody Mono<CreateNotificationRequest> request) {
        return request.flatMap(notificationService::createNotification)
                .flatMap(notification -> notificationService.getNotificationById(notification.getNotificationID()));
    }

    @GetMapping
    public Mono<PagedResponse<NotificationResponse>> getAllNotifications(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size) {
        return notificationService.getAllNotifications(isActive, page, size);
    }

    @GetMapping("/{notificationId}")
    public Mono<NotificationResponse> getNotificationById(@PathVariable Integer notificationId) {
        return notificationService.getNotificationById(notificationId);
    }

    @PutMapping("/{notificationId}")
    public Mono<NotificationResponse> updateNotification(
            @PathVariable Integer notificationId,
            @Valid @RequestBody Mono<UpdateNotificationRequest> request) {
        return request.flatMap(req -> notificationService.updateNotification(notificationId, req))
                .flatMap(notification -> notificationService.getNotificationById(notification.getNotificationID()));
    }

    @DeleteMapping("/{notificationId}")
    public Mono<ResponseEntity<Void>> deleteNotification(@PathVariable Integer notificationId) {
        return notificationService.deleteNotification(notificationId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
