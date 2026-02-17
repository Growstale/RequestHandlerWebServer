package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.entity.WebNotification;
import com.vodchyts.backend.feature.repository.ReactiveWebNotificationRepository;
import com.vodchyts.backend.feature.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/web-notifications")
public class WebNotificationController {
    private final ReactiveWebNotificationRepository repository;
    private final UserService userService; // Добавляем поле

    // Внедряем обе зависимости через конструктор
    public WebNotificationController(ReactiveWebNotificationRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @GetMapping
    public Flux<WebNotification> getMyNotifications(@AuthenticationPrincipal String login) {
        // Убираем UserService из параметров метода
        return userService.findByLogin(login)
                .flatMapMany(u -> repository.findByUserIDAndIsReadFalseOrderByCreatedAtDesc(u.getUserID()));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> markAsRead(@PathVariable Integer id) {
        return repository.deleteById(id);
    }

    @DeleteMapping("/clear-all")
    public Mono<Void> clearAll(@AuthenticationPrincipal String login) {
        return userService.findByLogin(login)
                .flatMap(u -> repository.deleteByUserID(u.getUserID()));
    }
}