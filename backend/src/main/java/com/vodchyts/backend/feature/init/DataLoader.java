package com.vodchyts.backend.feature.init;

import com.vodchyts.backend.feature.entity.Role;
import com.vodchyts.backend.feature.entity.UrgencyCategory;
import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveUrgencyCategoryRepository;
import com.vodchyts.backend.feature.repository.ReactiveUserRepository;
import com.vodchyts.backend.feature.service.RequestUpdateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DataLoader implements CommandLineRunner {

    private final ReactiveRoleRepository reactiveRoleRepository;
    private final ReactiveUrgencyCategoryRepository urgencyCategoryRepository;
    private final ReactiveUserRepository userRepository;
    private final RequestUpdateService requestUpdateService;
    private final PasswordEncoder passwordEncoder;

    @Value("${initial.admin.login}")
    private String adminLogin;

    @Value("${initial.admin.password}")
    private String adminPassword;

    public DataLoader(ReactiveRoleRepository reactiveRoleRepository,
                      ReactiveUrgencyCategoryRepository urgencyCategoryRepository,
                      ReactiveUserRepository userRepository,
                      RequestUpdateService requestUpdateService,
                      PasswordEncoder passwordEncoder) {
        this.reactiveRoleRepository = reactiveRoleRepository;
        this.urgencyCategoryRepository = urgencyCategoryRepository;
        this.userRepository = userRepository;
        this.requestUpdateService = requestUpdateService;
        this.passwordEncoder = passwordEncoder;
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
                .then(createAdminUserIfNotExists())
                .then(requestUpdateService.updateOverdueStatus())
                .doOnError(err -> System.err.println("Error during application startup initialization: " + err.getMessage()))
                .doOnSuccess(v -> System.out.println("Initial data, admin user and request statuses initialization completed"))
                .block();
    }

    private Mono<User> createAdminUserIfNotExists() {
        if (adminLogin == null || adminPassword == null || adminLogin.isBlank()) {
            System.out.println("Admin credentials not provided in environment variables. Skipping admin creation.");
            return Mono.empty();
        }

        return userRepository.findByLogin(adminLogin)
                .switchIfEmpty(Mono.defer(() -> reactiveRoleRepository.findByRoleName("RetailAdmin")
                        .flatMap(adminRole -> {
                            User admin = new User();
                            admin.setLogin(adminLogin);
                            admin.setPassword(passwordEncoder.encode(adminPassword));
                            admin.setRoleID(adminRole.getRoleID());
                            admin.setFullName("System Administrator");
                            admin.setContactInfo("System");

                            System.out.println("Creating initial admin user: " + adminLogin);
                            return userRepository.save(admin);
                        })
                        .switchIfEmpty(Mono.error(new RuntimeException("Role 'RetailAdmin' not found during admin creation")))
                ));
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