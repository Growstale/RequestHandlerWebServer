package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.entity.WebNotification;
import com.vodchyts.backend.feature.repository.ReactiveWebNotificationRepository;
import com.vodchyts.backend.feature.service.UpdateBroadcaster;
import com.vodchyts.backend.feature.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/web-notifications")
public class WebNotificationController {
    private final ReactiveWebNotificationRepository repository;
    private final UserService userService;
    private final UpdateBroadcaster broadcaster;

    // Внедряем обе зависимости через конструктор
    public WebNotificationController(ReactiveWebNotificationRepository repository, UserService userService, UpdateBroadcaster broadcaster) {
        this.repository = repository;
        this.userService = userService;
        this.broadcaster = broadcaster;
    }

    @GetMapping
    public Flux<WebNotification> getMyNotifications(@AuthenticationPrincipal String login) {
        // Убираем UserService из параметров метода
        return userService.findByLogin(login)
                .flatMapMany(u -> repository.findByUserIDAndIsReadFalseOrderByCreatedAtDesc(u.getUserID()));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> markAsRead(@PathVariable Integer id, @AuthenticationPrincipal String login) {
        return repository.findById(id)
                .flatMap(n -> repository.delete(n)
                        .then(userService.findByLogin(login))
                        .doOnSuccess(u -> broadcaster.publish("WEB_NOTIFICATION_USER_" + u.getUserID())) // Оповещаем фронтенд
                ).then();
    }

    @DeleteMapping("/clear-all")
    public Mono<Void> clearAll(@AuthenticationPrincipal String login) {
        return userService.findByLogin(login)
                .flatMap(u -> repository.deleteByUserID(u.getUserID()));
    }
}