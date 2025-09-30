package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveUserRepository;
import com.vodchyts.backend.security.JwtUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

    private final ReactiveUserRepository userRepository;
    private final ReactiveRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthService(ReactiveUserRepository userRepository,
                       ReactiveRoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    public Mono<String> register(String login, String password, String roleName) {
        return userRepository.existsByLogin(login)
                .flatMap(exists -> {
                    if (exists) return Mono.just("User already exists");
                    return roleRepository.findByRoleName(roleName)
                            .switchIfEmpty(Mono.error(new RuntimeException("Role not found")))
                            .flatMap(role -> {
                                User user = new User();
                                user.setLogin(login);
                                user.setPassword(passwordEncoder.encode(password));
                                user.setRoleID(role.getRoleID());
                                return userRepository.save(user).thenReturn("User registered");
                            });
                });
    }

    public Mono<String> login(String login, String password) {
        return userRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return Mono.error(new RuntimeException("Invalid password"));
                    }
                    return roleRepository.findById(user.getRoleID())
                            .map(role -> jwtUtils.generateToken(user.getLogin(), role.getRoleName()));
                });
    }
}