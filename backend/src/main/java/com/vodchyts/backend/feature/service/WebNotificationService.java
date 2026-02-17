package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.entity.WebNotification;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveUserRepository;
import com.vodchyts.backend.feature.repository.ReactiveWebNotificationRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
public class WebNotificationService {
    private final ReactiveWebNotificationRepository repository;
    private final ReactiveUserRepository userRepository;
    private final ReactiveRoleRepository roleRepository;

    public WebNotificationService(ReactiveWebNotificationRepository repository,
                                  ReactiveUserRepository userRepository,
                                  ReactiveRoleRepository roleRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    // Главный метод отправки
    public Mono<Void> send(Integer targetRequestID, String title, String message, Integer onlyForContractorID) {
        return roleRepository.findByRoleName("RetailAdmin")
                .flatMapMany(adminRole -> userRepository.findAllByRoleID(adminRole.getRoleID()))
                .map(User::getUserID)
                .collectList()
                .flatMap(adminIds -> {
                    Set<Integer> recipients = new HashSet<>(adminIds);
                    if (onlyForContractorID != null) recipients.add(onlyForContractorID);

                    return Flux.fromIterable(recipients)
                            .flatMap(uid -> {
                                WebNotification wn = new WebNotification();
                                wn.setUserID(uid);
                                wn.setRequestID(targetRequestID);
                                wn.setTitle(title);
                                wn.setMessage(message);
                                wn.setCreatedAt(LocalDateTime.now());
                                wn.setIsRead(false);
                                return repository.save(wn);
                            }).then();
                });
    }
}