package com.vodchyts.backend.feature.init;

import com.vodchyts.backend.feature.entity.Role;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DataLoader implements CommandLineRunner {

    private final ReactiveRoleRepository reactiveRoleRepository;

    public DataLoader(ReactiveRoleRepository reactiveRoleRepository) {
        this.reactiveRoleRepository = reactiveRoleRepository;
    }

    @Override
    public void run(String... args) {
        Mono.when(
                    createRoleIfNotExists("Contractor"),
                    createRoleIfNotExists("StoreManager"),
                    createRoleIfNotExists("RetailAdmin")
                )
                .doOnError(err -> System.err.println("Error creating roles: " + err.getMessage()))
                .doOnSuccess(v -> System.out.println("Roles initialization completed"))
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
}
