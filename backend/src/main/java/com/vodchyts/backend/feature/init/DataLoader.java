package com.vodchyts.backend.feature.init;

import com.vodchyts.backend.feature.entity.Role;
import com.vodchyts.backend.feature.entity.UrgencyCategory;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveUrgencyCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DataLoader implements CommandLineRunner {

    private final ReactiveRoleRepository reactiveRoleRepository;
    private final ReactiveUrgencyCategoryRepository urgencyCategoryRepository;

    public DataLoader(ReactiveRoleRepository reactiveRoleRepository, ReactiveUrgencyCategoryRepository urgencyCategoryRepository) { // <-- ИЗМЕНЕНИЕ
        this.reactiveRoleRepository = reactiveRoleRepository;
        this.urgencyCategoryRepository = urgencyCategoryRepository;
    }

    @Override
    public void run(String... args) {
        Mono.when(
                        createRoleIfNotExists("Contractor"),
                        createRoleIfNotExists("StoreManager"),
                        createRoleIfNotExists("RetailAdmin"),
                        createUrgencyCategoryIfNotExists("Emergency", 2),
                        createUrgencyCategoryIfNotExists("Urgent", 3),
                        createUrgencyCategoryIfNotExists("Planned", 14),
                        createUrgencyCategoryIfNotExists("Customizable", 40)
                )
                .doOnError(err -> System.err.println("Error creating initial data: " + err.getMessage()))
                .doOnSuccess(v -> System.out.println("Initial data initialization completed"))
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