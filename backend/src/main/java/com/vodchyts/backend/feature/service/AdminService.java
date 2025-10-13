package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.common.validator.PasswordValidator;
import com.vodchyts.backend.exception.InvalidPasswordException;
import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.exception.UserAlreadyExistsException;
import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.dto.CreateUserRequest;
import com.vodchyts.backend.feature.dto.UpdateUserRequest;
import com.vodchyts.backend.feature.dto.UserResponse;
import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Service
public class AdminService {

    private final ReactiveUserRepository userRepository;
    private final ReactiveRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    public AdminService(ReactiveUserRepository userRepository, ReactiveRoleRepository roleRepository, PasswordEncoder passwordEncoder, PasswordValidator passwordValidator) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
    }

    public Mono<User> createUser(CreateUserRequest request) {
        passwordValidator.validate(request.password());

        return userRepository.existsByLogin(request.login())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new UserAlreadyExistsException("User with login '" + request.login() + "' already exists"));
                    }
                    return roleRepository.findByRoleName(request.roleName())
                            .switchIfEmpty(Mono.error(new RuntimeException("Role '" + request.roleName() + "' not found")))
                            .flatMap(role -> {
                                User user = new User();
                                user.setLogin(request.login());
                                user.setPassword(passwordEncoder.encode(request.password()));
                                user.setRoleID(role.getRoleID());
                                user.setContactInfo(request.contactInfo());
                                if (request.telegramID() != null && !request.telegramID().isBlank()) {
                                    user.setTelegramID(Long.parseLong(request.telegramID()));
                                }
                                return userRepository.save(user);
                            });
                });
    }

    public Flux<UserResponse> getAllUsers(String roleName, List<String> sort) {
        Flux<User> users = (roleName != null && !roleName.isEmpty())
                ? roleRepository.findByRoleName(roleName)
                .flatMapMany(role -> userRepository.findAllByRoleID(role.getRoleID()))
                : userRepository.findAll();

        Flux<UserResponse> userResponses = users.flatMap(this::mapUserToUserResponse);

        if (sort != null && !sort.isEmpty()) {
            Comparator<UserResponse> comparator = buildComparator(sort);
            if (comparator != null) {
                return userResponses.collectList().flatMapMany(list -> {
                    list.sort(comparator);
                    return Flux.fromIterable(list);
                });
            }
        }
        return userResponses;
    }

    private Comparator<UserResponse> buildComparator(List<String> sortParams) {
        Comparator<UserResponse> finalComparator = null;

        for (String sortParam : sortParams) {
            String[] parts = sortParam.split(",");
            String field = parts[0];
            String direction = parts.length > 1 ? parts[1].toUpperCase() : "ASC";

            Comparator<UserResponse> currentComparator = switch (field) {
                case "userID" -> Comparator.comparing(UserResponse::userID, Comparator.nullsLast(Comparator.naturalOrder()));
                case "login" -> Comparator.comparing(UserResponse::login, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "roleName" -> Comparator.comparing(UserResponse::roleName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "contactInfo" -> Comparator.comparing(UserResponse::contactInfo, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "telegramID" -> Comparator.comparing(UserResponse::telegramID, Comparator.nullsLast(Comparator.naturalOrder()));
                default -> null;
            };

            if (currentComparator != null) {
                if ("DESC".equals(direction)) {
                    currentComparator = currentComparator.reversed();
                }

                if (finalComparator == null) {
                    finalComparator = currentComparator;
                } else {
                    finalComparator = finalComparator.thenComparing(currentComparator);
                }
            }
        }
        return finalComparator;
    }

    public Mono<Void> deleteUser(Integer userId) {
        String adminRoleName = "RetailAdmin";

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User with ID " + userId + " not found")))
                .flatMap(userToDelete ->
                        roleRepository.findById(userToDelete.getRoleID())
                                .flatMap(userRole -> {
                                    if (adminRoleName.equals(userRole.getRoleName())) {
                                        return Mono.error(new OperationNotAllowedException("Cannot delete an administrator account"));
                                    }
                                    return userRepository.delete(userToDelete);
                                })
                );
    }


    public Mono<UserResponse> updateUser(Integer userId, UpdateUserRequest request) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User with ID " + userId + " not found")))
                .flatMap(user -> {
                    if (request.password() != null && !request.password().isBlank()) {
                        passwordValidator.validate(request.password());
                        user.setPassword(passwordEncoder.encode(request.password()));
                    }

                    if (request.contactInfo() != null) {
                        user.setContactInfo(request.contactInfo());
                    }

                    if (request.telegramID() != null) {
                        if (request.telegramID().isEmpty()) {
                            user.setTelegramID(null);
                        } else {
                            user.setTelegramID(Long.parseLong(request.telegramID()));
                        }
                    }

                    Mono<User> userMono = Mono.just(user);
                    if (request.roleName() != null && !request.roleName().isBlank()) {
                        userMono = roleRepository.findByRoleName(request.roleName())
                                .switchIfEmpty(Mono.error(new RuntimeException("Role '" + request.roleName() + "' not found")))
                                .map(role -> {
                                    user.setRoleID(role.getRoleID());
                                    return user;
                                });
                    }

                    return userMono;
                })
                .flatMap(userRepository::save)
                .flatMap(this::mapUserToUserResponse);
    }

    private Mono<UserResponse> mapUserToUserResponse(User user) {
        return roleRepository.findById(user.getRoleID())
                .map(role -> new UserResponse(
                        user.getUserID(),
                        user.getLogin(),
                        role.getRoleName(),
                        user.getContactInfo(),
                        user.getTelegramID()
                ));
    }
}
