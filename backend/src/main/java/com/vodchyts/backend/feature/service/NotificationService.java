package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.NotificationAlreadyExistsException;
import com.vodchyts.backend.feature.dto.CreateNotificationRequest;
import com.vodchyts.backend.feature.dto.NotificationResponse;
import com.vodchyts.backend.feature.dto.PagedResponse;
import com.vodchyts.backend.feature.dto.UpdateNotificationRequest;
import com.vodchyts.backend.feature.entity.Notification;
import com.vodchyts.backend.feature.entity.NotificationRecipient;
import com.vodchyts.backend.feature.repository.ReactiveNotificationRecipientRepository;
import com.vodchyts.backend.feature.repository.ReactiveNotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class NotificationService {

    private final ReactiveNotificationRepository notificationRepository;
    private final ReactiveNotificationRecipientRepository recipientRepository;
    private final NotificationSchedulerService schedulerService;

    public NotificationService(ReactiveNotificationRepository notificationRepository,
                             ReactiveNotificationRecipientRepository recipientRepository,
                             NotificationSchedulerService schedulerService) {
        this.notificationRepository = notificationRepository;
        this.recipientRepository = recipientRepository;
        this.schedulerService = schedulerService;
    }

    public Mono<Notification> createNotification(CreateNotificationRequest request) {
        return notificationRepository.existsByTitle(request.getTitle())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new NotificationAlreadyExistsException(
                                "Уведомление с названием '" + request.getTitle() + "' уже существует"));
                    }

                    Notification notification = new Notification();
                    notification.setTitle(request.getTitle());
                    notification.setMessage(request.getMessage());
                    notification.setImageData(request.getImageData());
                    notification.setCronExpression(request.getCronExpression());
                    notification.setIsActive(request.getIsActive());

                    return notificationRepository.save(notification)
                            .flatMap(savedNotification -> {
                                if (request.getRecipientChatIds() != null && !request.getRecipientChatIds().isEmpty()) {
                                    return saveRecipients(savedNotification.getNotificationID(), request.getRecipientChatIds())
                                            .then(Mono.just(savedNotification));
                                }
                                return Mono.just(savedNotification);
                            })
                            .doOnNext(savedNotification -> {
                                if (Boolean.TRUE.equals(savedNotification.getIsActive())) {
                                    schedulerService.scheduleNotification(savedNotification);
                                }
                            });
                });
    }

    public Mono<Notification> updateNotification(Integer notificationId, UpdateNotificationRequest request) {
        return notificationRepository.findById(notificationId)
                .switchIfEmpty(Mono.error(new RuntimeException("Уведомление не найдено")))
                .flatMap(notification -> {
                    Mono<Void> uniquenessCheck = Mono.empty();
                    if (request.getTitle() != null && !request.getTitle().equals(notification.getTitle())) {
                        uniquenessCheck = notificationRepository.existsByTitle(request.getTitle())
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new NotificationAlreadyExistsException(
                                                "Уведомление с названием '" + request.getTitle() + "' уже существует"));
                                    }
                                    return Mono.empty();
                                });
                    }

                    return uniquenessCheck.then(Mono.defer(() -> {
                        if (request.getTitle() != null) {
                            notification.setTitle(request.getTitle());
                        }
                        if (request.getMessage() != null) {
                            notification.setMessage(request.getMessage());
                        }
                        if (request.getImageData() != null) {
                            notification.setImageData(request.getImageData());
                        }
                        if (request.getCronExpression() != null) {
                            notification.setCronExpression(request.getCronExpression());
                        }
                        if (request.getIsActive() != null) {
                            notification.setIsActive(request.getIsActive());
                        }

                        return notificationRepository.save(notification)
                                .flatMap(savedNotification -> {
                                    if (request.getRecipientChatIds() != null) {
                                        return recipientRepository.deleteByNotificationID(notificationId)
                                                .then(saveRecipients(notificationId, request.getRecipientChatIds()))
                                                .then(Mono.just(savedNotification));
                                    }
                                    return Mono.just(savedNotification);
                                })
                                .doOnNext(schedulerService::rescheduleNotification);
                    }));
                });
    }

    public Mono<Void> deleteNotification(Integer notificationId) {
        return notificationRepository.deleteById(notificationId)
                .doOnSuccess(unused -> schedulerService.unscheduleNotification(notificationId));
    }

    public Mono<PagedResponse<NotificationResponse>> getAllNotifications(Boolean isActive, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        
        Mono<Long> totalCount = isActive != null 
            ? notificationRepository.countByIsActive(isActive)
            : notificationRepository.count();

        Flux<Notification> notifications = isActive != null
            ? notificationRepository.findByIsActive(isActive, pageable)
            : notificationRepository.findAll();

        return totalCount.flatMap(count -> {
            int totalPages = (int) Math.ceil((double) count / size);
            
            return notifications
                    .flatMap(this::mapNotificationToResponse)
                    .collectList()
                    .map(content -> new PagedResponse<>(content, page, count, totalPages));
        });
    }

    public Mono<NotificationResponse> getNotificationById(Integer notificationId) {
        return notificationRepository.findById(notificationId)
                .switchIfEmpty(Mono.error(new RuntimeException("Уведомление не найдено")))
                .flatMap(this::mapNotificationToResponse);
    }

    public Flux<Notification> getActiveNotifications() {
        return notificationRepository.findActiveNotifications();
    }

    private Mono<Void> saveRecipients(Integer notificationId, List<Integer> chatIds) {
        return Flux.fromIterable(chatIds)
                .map(chatId -> {
                    NotificationRecipient recipient = new NotificationRecipient();
                    recipient.setNotificationID(notificationId);
                    recipient.setShopContractorChatID(chatId);
                    return recipient;
                })
                .flatMap(recipientRepository::save)
                .then();
    }

    private Mono<NotificationResponse> mapNotificationToResponse(Notification notification) {
        return recipientRepository.findByNotificationID(notification.getNotificationID())
                .map(NotificationRecipient::getShopContractorChatID)
                .collectList()
                .map(chatIds -> {
                    NotificationResponse response = new NotificationResponse();
                    response.setNotificationID(notification.getNotificationID());
                    response.setTitle(notification.getTitle());
                    response.setMessage(notification.getMessage());
                    response.setHasImage(notification.getImageData() != null && notification.getImageData().length > 0);
                    response.setCronExpression(notification.getCronExpression());
                    response.setIsActive(notification.getIsActive());
                    response.setRecipientChatIds(chatIds);
                    return response;
                });
    }
}
