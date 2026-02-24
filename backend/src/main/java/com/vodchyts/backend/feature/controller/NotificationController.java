package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.CreateNotificationRequest;
import com.vodchyts.backend.feature.dto.NotificationResponse;
import com.vodchyts.backend.feature.dto.PagedResponse;
import com.vodchyts.backend.feature.dto.UpdateNotificationRequest;
import com.vodchyts.backend.feature.service.AuditHelper;
import com.vodchyts.backend.feature.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('RetailAdmin')")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuditHelper auditHelper;

    public NotificationController(NotificationService notificationService, AuditHelper auditHelper) {
        this.notificationService = notificationService;
        this.auditHelper = auditHelper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<NotificationResponse> createNotification(@Valid @RequestBody Mono<CreateNotificationRequest> request, ServerWebExchange exchange) {
        return request.flatMap(notificationService::createNotification)
                .flatMap(notification -> notificationService.getNotificationById(notification.getNotificationID())
                        .flatMap(response -> {
                            // Аудит создания
                            auditHelper.auditCreate("Notifications", notification.getNotificationID(), response, exchange).subscribe();
                            return Mono.just(response);
                        }));
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
            @Valid @RequestBody Mono<UpdateNotificationRequest> request,
            ServerWebExchange exchange) {
        return notificationService.getAllNotifications(null, 0, 1000)
                .flatMap(paged -> {
                    // Находим старую версию для аудита
                    NotificationResponse oldNotification = paged.content().stream()
                            .filter(n -> n.notificationID().equals(notificationId))
                            .findFirst()
                            .orElse(null);
                    
                    return request.flatMap(req -> notificationService.updateNotification(notificationId, req))
                            .flatMap(notification -> notificationService.getNotificationById(notification.getNotificationID())
                                    .flatMap(updatedNotification -> {
                                        // Аудит обновления
                                        auditHelper.auditUpdate("Notifications", notificationId, oldNotification, updatedNotification, exchange).subscribe();
                                        return Mono.just(updatedNotification);
                                    }));
                });
    }

    @DeleteMapping("/{notificationId}")
    public Mono<ResponseEntity<Void>> deleteNotification(@PathVariable Integer notificationId, ServerWebExchange exchange) {
        return notificationService.getAllNotifications(null, 0, 1000)
                .flatMap(paged -> {
                    // Находим удаляемую запись для аудита
                    NotificationResponse notificationToDelete = paged.content().stream()
                            .filter(n -> n.notificationID().equals(notificationId))
                            .findFirst()
                            .orElse(null);
                    
                    return notificationService.deleteNotification(notificationId)
                            .then(Mono.fromRunnable(() -> {
                                // Аудит удаления
                                auditHelper.auditDelete("Notifications", notificationId, notificationToDelete, exchange).subscribe();
                            }))
                            .thenReturn(ResponseEntity.noContent().build());
                });
    }
}
