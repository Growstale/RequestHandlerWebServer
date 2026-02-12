package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.common.validator.PasswordValidator;
import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.exception.UserAlreadyExistsException;
import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.dto.CreateUserRequest;
import com.vodchyts.backend.feature.dto.PagedResponse;
import com.vodchyts.backend.feature.dto.UpdateUserRequest;
import com.vodchyts.backend.feature.dto.UserResponse;
import com.vodchyts.backend.feature.entity.Role;
import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.repository.ReactiveRequestRepository;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveShopRepository;
import com.vodchyts.backend.feature.repository.ReactiveUserRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final ReactiveUserRepository userRepository;
    private final ReactiveRoleRepository roleRepository;
    private final ReactiveRequestRepository requestRepository;
    private final ReactiveShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final DatabaseClient databaseClient;

    public AdminService(ReactiveUserRepository userRepository,
                        ReactiveRoleRepository roleRepository,
                        ReactiveRequestRepository requestRepository, ReactiveShopRepository shopRepository,
                        PasswordEncoder passwordEncoder,
                        PasswordValidator passwordValidator,
                        DatabaseClient databaseClient) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.requestRepository = requestRepository;
        this.shopRepository = shopRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
        this.databaseClient = databaseClient;
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

    public static final BiFunction<Row, RowMetadata, UserResponse> USER_MAPPING_FUNCTION = (row, rowMetaData) -> new UserResponse(
            row.get("UserID", Integer.class),
            row.get("Login", String.class),
            row.get("RoleName", String.class),
            row.get("FullName", String.class),
            row.get("ContactInfo", String.class),
            row.get("TelegramID", Long.class)
    );

    public Mono<PagedResponse<UserResponse>> getAllUsers(String roleName, List<String> sort, int page, int size) {
        boolean applyRoleFilter = roleName != null && !roleName.isEmpty() && !"Все".equalsIgnoreCase(roleName);

        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT u.UserID, u.Login, u.FullName, u.ContactInfo, u.TelegramID, r.RoleName " +
                        "FROM Users u " +
                        "LEFT JOIN Roles r ON u.RoleID = r.RoleID"
        );

        Map<String, Object> bindings = new HashMap<>();

        if (applyRoleFilter) {
            sqlBuilder.append(" WHERE r.RoleName = :roleName");
            bindings.put("roleName", roleName);
        }

        String countSql = "SELECT COUNT(*) FROM (" + sqlBuilder.toString() + ") as count_subquery";
        DatabaseClient.GenericExecuteSpec countSpec = databaseClient.sql(countSql);
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            countSpec = countSpec.bind(entry.getKey(), entry.getValue());
        }
        Mono<Long> countMono = countSpec.map(row -> row.get(0, Long.class)).one();

        sqlBuilder.append(parseSortToSql(sort));
        sqlBuilder.append(" OFFSET ").append((long) page * size).append(" ROWS FETCH NEXT ").append(size).append(" ROWS ONLY");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sqlBuilder.toString());
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            spec = spec.bind(entry.getKey(), entry.getValue());
        }

        Flux<UserResponse> resultFlux = spec.map(USER_MAPPING_FUNCTION).all();

        return Mono.zip(resultFlux.collectList(), countMono)
                .map(tuple -> {
                    List<UserResponse> content = tuple.getT1();
                    long total = tuple.getT2();
                    int totalPages = (total == 0) ? 0 : (int) Math.ceil((double) total / size);
                    return new PagedResponse<>(content, page, total, totalPages);
                });
    }

    private String parseSortToSql(List<String> sortParams) {
        if (sortParams == null || sortParams.isEmpty()) {
            return " ORDER BY u.UserID ASC";
        }
        Map<String, String> columnMapping = Map.of(
                "userID", "u.UserID",
                "login", "u.Login",
                "fullName", "u.FullName",
                "roleName", "r.RoleName",
                "contactInfo", "u.ContactInfo",
                "telegramID", "u.TelegramID"
        );

        String orders = sortParams.stream()
                .map(param -> {
                    String[] parts = param.split(",");
                    String field = parts[0];
                    String direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1])) ? "DESC" : "ASC";
                    String dbColumn = columnMapping.get(field);
                    if (dbColumn == null) return null;
                    return dbColumn + " " + direction;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        return orders.isEmpty() ? " ORDER BY u.UserID ASC" : " ORDER BY " + orders;
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
                    if (request.contactInfo() != null) user.setContactInfo(request.contactInfo());
                    if (request.fullName() != null) user.setFullName(request.fullName());
                    if (request.telegramID() != null) {
                        user.setTelegramID(request.telegramID().isEmpty() ? null : Long.parseLong(request.telegramID()));
                    }

                    Mono<User> userMono = Mono.just(user);

                    if (request.roleName() != null && !request.roleName().isBlank()) {
                        userMono = roleRepository.findById(user.getRoleID())
                                .flatMap(currentRole -> {
                                    if (currentRole.getRoleName().equals(request.roleName())) {
                                        return Mono.just(currentRole);
                                    }
                                    if ("RetailAdmin".equals(currentRole.getRoleName())) {
                                        return Mono.error(new OperationNotAllowedException(
                                                "Нельзя изменить роль Администратора. Вы не можете понизить уровень доступа для этой учетной записи."
                                        ));
                                    }
                                    if ("Contractor".equals(currentRole.getRoleName())) {
                                        return requestRepository.existsByAssignedContractorIDAndStatus(userId, "In work")
                                                .flatMap(hasActive -> {
                                                    if (hasActive) {
                                                        return Mono.error(new OperationNotAllowedException(
                                                                "Нельзя изменить роль: у этого подрядчика есть активные заявки. Сначала переназначьте или завершите их."
                                                        ));
                                                    }
                                                    return Mono.just(currentRole);
                                                });
                                    }
                                    if ("StoreManager".equals(currentRole.getRoleName())) {
                                        return shopRepository.findAllByUserID(userId)
                                                .hasElements()
                                                .flatMap(hasShops -> {
                                                    if (hasShops) {
                                                        return Mono.error(new OperationNotAllowedException(
                                                                "Нельзя изменить роль: этот пользователь привязан к магазинам. Сначала снимите его с должности в разделе 'Магазины'."
                                                        ));
                                                    }
                                                    return Mono.just(currentRole);
                                                });
                                    }
                                    return Mono.just(currentRole);
                                })
                                .then(roleRepository.findByRoleName(request.roleName()))
                                .switchIfEmpty(Mono.error(new RuntimeException("Роль '" + request.roleName() + "' не найдена")))
                                .map(newRole -> {
                                    user.setRoleID(newRole.getRoleID());
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