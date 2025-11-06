package com.vodchyts.backend.feature.init;

import com.vodchyts.backend.feature.entity.Role;
import com.vodchyts.backend.feature.entity.UrgencyCategory;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveUrgencyCategoryRepository;
import com.vodchyts.backend.feature.service.RequestUpdateService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DataLoader implements CommandLineRunner {

    private final ReactiveRoleRepository reactiveRoleRepository;
    private final ReactiveUrgencyCategoryRepository urgencyCategoryRepository;
    private final RequestUpdateService requestUpdateService;

    public DataLoader(ReactiveRoleRepository reactiveRoleRepository, ReactiveUrgencyCategoryRepository urgencyCategoryRepository, RequestUpdateService requestUpdateService) { // <-- ИЗМЕНЕНИЕ
        this.reactiveRoleRepository = reactiveRoleRepository;
        this.urgencyCategoryRepository = urgencyCategoryRepository;
        this.requestUpdateService = requestUpdateService;
    }

    @Override
    public void run(String... args) {
        Mono<Void> initialDataLoading = Mono.when(
                createRoleIfNotExists("Contractor"),
                createRoleIfNotExists("StoreManager"),
                createRoleIfNotExists("RetailAdmin"),
                createUrgencyCategoryIfNotExists("Emergency", 2),
                createUrgencyCategoryIfNotExists("Urgent", 3),
                createUrgencyCategoryIfNotExists("Planned", 14),
                createUrgencyCategoryIfNotExists("Customizable", 40)
        );

        initialDataLoading
                .then(requestUpdateService.updateOverdueStatus())
                .doOnError(err -> System.err.println("Error during application startup initialization: " + err.getMessage()))
                .doOnSuccess(v -> System.out.println("Initial data and request statuses initialization completed"))
                .block();
    }


    private Mono<Role> createRoleIfNotExists(String roleName) {
        return reactiveRoleRepository.findByRoleName(roleName)
                .switchIfEmpty(Mono.defer(() -> {
                    Role role = new Role();
                    role.setRoleName(roleName);
                    System.out.println("Creating role: " + roleName);
                    return reactiveRoleRepository.save(role);
                }));
    }

    private Mono<UrgencyCategory> createUrgencyCategoryIfNotExists(String urgencyName, int defaultDays) {
        return urgencyCategoryRepository.findByUrgencyName(urgencyName)
                .switchIfEmpty(Mono.defer(() -> {
                    UrgencyCategory category = new UrgencyCategory();
                    category.setUrgencyName(urgencyName);
                    category.setDefaultDays(defaultDays);
                    System.out.println("Creating urgency category: " + urgencyName);
                    return urgencyCategoryRepository.save(category);
                }));
    }
}