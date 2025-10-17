package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.common.validator.PasswordValidator;
import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.exception.UserAlreadyExistsException;
import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.dto.CreateUserRequest;
import com.vodchyts.backend.feature.dto.PagedResponse;
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
                        return Mono.error(new UserAlreadyExistsException("Пользователь с логином '" + request.login() + "' уже существует"));
                    }
                    return roleRepository.findByRoleName(request.roleName())
                            .switchIfEmpty(Mono.error(new RuntimeException("Роль '" + request.roleName() + "' не найдена")))
                            .flatMap(role -> {
                                User user = new User();
                                user.setLogin(request.login());
                                user.setPassword(passwordEncoder.encode(request.password()));
                                user.setRoleID(role.getRoleID());
                                user.setFullName(request.fullName());
                                user.setContactInfo(request.contactInfo());
                                if (request.telegramID() != null && !request.telegramID().isBlank()) {
                                    user.setTelegramID(Long.parseLong(request.telegramID()));
                                }
                                return userRepository.save(user);
                            });
                });
    }

    public Mono<PagedResponse<UserResponse>> getAllUsers(String roleName, List<String> sort, int page, int size) {
        boolean b = roleName != null && !roleName.isEmpty() && !"Все".equalsIgnoreCase(roleName);
        Flux<User> usersFlux = b
                ? roleRepository.findByRoleName(roleName)
                .flatMapMany(role -> userRepository.findAllByRoleID(role.getRoleID()))
                : userRepository.findAll();

        Flux<UserResponse> userResponses = usersFlux.flatMap(this::mapUserToUserResponse);

        if (sort != null && !sort.isEmpty()) {
            Comparator<UserResponse> comparator = buildComparator(sort);
            if (comparator != null) {
                userResponses = userResponses.sort(comparator);
            }
        }

        Mono<Long> countMono = b
                ? roleRepository.findByRoleName(roleName).flatMap(role -> userRepository.countByRoleID(role.getRoleID()))
                : userRepository.count();

        Mono<List<UserResponse>> contentMono = userResponses
                .skip((long) page * size)
                .take(size)
                .collectList();

        return Mono.zip(contentMono, countMono)
                .map(tuple -> {
                    List<UserResponse> content = tuple.getT1();
                    long count = tuple.getT2();
                    int totalPages = (count == 0) ? 0 : (int) Math.ceil((double) count / size);
                    return new PagedResponse<>(content, page, count, totalPages);
                });
    }

    private Comparator<UserResponse> buildComparator(List<String> sortParams) {
        if (sortParams == null) {
            return null;
        }
        Comparator<UserResponse> finalComparator = null;

        for (String sortParam : sortParams) {
            String[] parts = sortParam.split(",");
            if (parts.length == 0 || parts[0].isBlank()) continue;

            String field = parts[0];
            boolean isDescending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);

            Comparator<UserResponse> currentComparator = switch (field) {
                case "userID" -> Comparator.comparing(UserResponse::userID, Comparator.nullsLast(Comparator.naturalOrder()));
                case "login" -> Comparator.comparing(UserResponse::login, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "roleName" -> Comparator.comparing(UserResponse::roleName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "fullName" -> Comparator.comparing(UserResponse::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "contactInfo" -> Comparator.comparing(UserResponse::contactInfo, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "telegramID" -> Comparator.comparing(UserResponse::telegramID, Comparator.nullsLast(Comparator.naturalOrder()));
                default -> null;
            };

            if (currentComparator != null) {
                if (isDescending) {
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
                .switchIfEmpty(Mono.error(new UserNotFoundException("Пользователь с ID " + userId + " не найден")))
                .flatMap(userToDelete ->
                        roleRepository.findById(userToDelete.getRoleID())
                                .flatMap(userRole -> {
                                    if (adminRoleName.equals(userRole.getRoleName())) {
                                        return Mono.error(new OperationNotAllowedException("Нельзя удалить учетную запись администратора"));
                                    }
                                    return userRepository.delete(userToDelete);
                                })
                );
    }


    public Mono<UserResponse> updateUser(Integer userId, UpdateUserRequest request) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Пользователь с ID " + userId + " не найден")))
                .flatMap(user -> {
                    if (request.password() != null && !request.password().isBlank()) {
                        passwordValidator.validate(request.password());
                        user.setPassword(passwordEncoder.encode(request.password()));
                    }

                    if (request.contactInfo() != null) {
                        user.setContactInfo(request.contactInfo());
                    }

                    if (request.fullName() != null) {
                        user.setFullName(request.fullName());
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
                                .switchIfEmpty(Mono.error(new RuntimeException("Роль '" + request.roleName() + "' не найдена")))
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

    public Mono<UserResponse> mapUserToUserResponse(User user) {
        return roleRepository.findById(user.getRoleID())
                .map(role -> new UserResponse(
                        user.getUserID(),
                        user.getLogin(),
                        role.getRoleName(),
                        user.getFullName(),
                        user.getContactInfo(),
                        user.getTelegramID()
                ));
    }
}