package com.vodchyts.backend.feature.init;

import com.vodchyts.backend.feature.entity.Role;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final ReactiveRoleRepository reactiveRoleRepository;

    public DataLoader(ReactiveRoleRepository reactiveRoleRepository) {
        this.reactiveRoleRepository = reactiveRoleRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        createRoleIfNotExists("Contractor");
        createRoleIfNotExists("StoreManager");
        createRoleIfNotExists("RetailAdmin");
    }

    private void createRoleIfNotExists(String roleName) {
        reactiveRoleRepository.findByRoleName(roleName)
                .switchIfEmpty(
                        reactiveRoleRepository.save(new Role() {{
                            setRoleName(roleName);
                        }})
                )
                .subscribe();
    }
}
