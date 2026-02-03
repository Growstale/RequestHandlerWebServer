package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.entity.ShopContractorChat;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveShopContractorChatRepository;
import com.vodchyts.backend.feature.repository.ReactiveShopRepository;
import com.vodchyts.backend.feature.repository.ReactiveUserRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class ShopContractorChatService {

    private final ReactiveShopContractorChatRepository chatRepository;
    private final ReactiveUserRepository userRepository;
    private final ReactiveRoleRepository roleRepository;
    private final ReactiveShopRepository shopRepository;
    private final DatabaseClient databaseClient;
    private final TelegramNotificationService notificationService;

    public static final BiFunction<Row, RowMetadata, ShopContractorChatResponse> MAPPING_FUNCTION = (row, rowMetaData) -> new ShopContractorChatResponse(
            row.get("ShopContractorChatID", Integer.class),
            row.get("ShopID", Integer.class),
            row.get("ShopName", String.class),
            row.get("ContractorID", Integer.class),
            row.get("ContractorLogin", String.class),
            row.get("TelegramID", Long.class)
    );

    public ShopContractorChatService(ReactiveShopContractorChatRepository chatRepository, ReactiveUserRepository userRepository, ReactiveRoleRepository roleRepository, ReactiveShopRepository shopRepository, DatabaseClient databaseClient, TelegramNotificationService notificationService) {
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.shopRepository = shopRepository;
        this.databaseClient = databaseClient;
        this.notificationService = notificationService;
    }

    public Mono<PagedResponse<ShopContractorChatResponse>> getAllChats(List<String> sort, int page, int size) {
        String sql = "SELECT scc.ShopContractorChatID, scc.ShopID, s.ShopName, scc.ContractorID, u.Login as ContractorLogin, scc.TelegramID " +
                "FROM ShopContractorChats scc " +
                "JOIN Shops s ON scc.ShopID = s.ShopID " +
                "LEFT JOIN Users u ON scc.ContractorID = u.UserID";

        String countSql = "SELECT COUNT(*) FROM ShopContractorChats";
        Mono<Long> countMono = databaseClient.sql(countSql).map(row -> row.get(0, Long.class)).one();

        String sortedSql = sql + parseSortToSql(sort) + " OFFSET " + ((long) page * size) + " ROWS FETCH NEXT " + size + " ROWS ONLY";
        Flux<ShopContractorChatResponse> contentFlux = databaseClient.sql(sortedSql).map(MAPPING_FUNCTION).all();

        return Mono.zip(contentFlux.collectList(), countMono)
                .map(tuple -> new PagedResponse<>(tuple.getT1(), page, tuple.getT2(), (int) Math.ceil((double) tuple.getT2() / size)));
    }

    private String parseSortToSql(List<String> sortParams) {
        if (sortParams == null || sortParams.isEmpty()) {
            return " ORDER BY scc.ShopContractorChatID ASC";
        }
        Map<String, String> columnMapping = Map.of(
                "shopContractorChatID", "scc.ShopContractorChatID",
                "shopName", "s.ShopName",
                "contractorLogin", "u.Login",
                "telegramID", "scc.TelegramID"
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
        return orders.isEmpty() ? " ORDER BY scc.ShopContractorChatID ASC" : " ORDER BY " + orders;
    }

    public Mono<ShopContractorChat> createChat(CreateShopContractorChatRequest request) {
        Mono<Void> botCheck = notificationService.validateChatId(request.telegramID())
                .flatMap(isValid -> {
                    if (!isValid) {
                        return Mono.error(new OperationNotAllowedException(
                                "Бот не нашел чат с ID " + request.telegramID() + ". " +
                                        "Убедитесь, что ID введен верно, Бот добавлен в этот чат и является администратором."
                        ));
                    }
                    return Mono.empty();
                });

        Mono<Void> telegramIdCheck = databaseClient.sql("SELECT COUNT(*) FROM ShopContractorChats WHERE TelegramID = :telegramId")
                .bind("telegramId", request.telegramID())
                .map(row -> row.get(0, Long.class))
                .one()
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.error(new OperationNotAllowedException("Чат с таким Telegram ID уже зарегистрирован в системе."));
                    }
                    return Mono.empty();
                });

        Mono<Void> logicCheck;
        if (request.contractorID() != null) {
            logicCheck = chatRepository.existsByShopIDAndContractorID(request.shopID(), request.contractorID())
                    .flatMap(exists -> {
                        if (exists) {
                            return Mono.error(new OperationNotAllowedException("Связь для этой пары магазина и подрядчика уже существует."));
                        }
                        return validateUserIsContractor(request.contractorID());
                    });
        } else {
            String checkSql = "SELECT COUNT(*) FROM ShopContractorChats WHERE ShopID = :shopId AND ContractorID IS NULL";
            logicCheck = databaseClient.sql(checkSql)
                    .bind("shopId", request.shopID())
                    .map(row -> row.get(0, Long.class))
                    .one()
                    .flatMap(count -> {
                        if (count > 0) {
                            return Mono.error(new OperationNotAllowedException("Связь для этого магазина без подрядчика уже существует."));
                        }
                        return Mono.empty();
                    });
        }

        return botCheck
                .then(telegramIdCheck)
                .then(logicCheck)
                .then(Mono.defer(() -> {
                    ShopContractorChat chat = new ShopContractorChat();
                    chat.setShopID(request.shopID());
                    chat.setContractorID(request.contractorID());
                    chat.setTelegramID(request.telegramID());
                    return chatRepository.save(chat);
                }));
    }

    public Mono<ShopContractorChat> updateChat(Integer chatId, UpdateShopContractorChatRequest request) {
        return chatRepository.findById(chatId)
                .switchIfEmpty(Mono.error(new RuntimeException("Чат не найден")))
                .flatMap(existingChat -> {
                    Mono<Void> botCheck = Mono.empty();
                    if (!existingChat.getTelegramID().equals(request.telegramID())) {
                        botCheck = notificationService.validateChatId(request.telegramID())
                                .flatMap(isValid -> {
                                    if (!isValid) {
                                        return Mono.error(new OperationNotAllowedException(
                                                "Бот не нашел чат с ID " + request.telegramID() + ". Проверьте корректность данных."
                                        ));
                                    }
                                    return Mono.empty();
                                });
                    }

                    Mono<Void> telegramIdCheck = Mono.empty();
                    if (!existingChat.getTelegramID().equals(request.telegramID())) {
                        telegramIdCheck = databaseClient.sql("SELECT COUNT(*) FROM ShopContractorChats WHERE TelegramID = :telegramId AND ShopContractorChatID != :currentId")
                                .bind("telegramId", request.telegramID())
                                .bind("currentId", chatId)
                                .map(row -> row.get(0, Long.class))
                                .one()
                                .flatMap(count -> {
                                    if (count > 0) {
                                        return Mono.error(new OperationNotAllowedException("Чат с таким Telegram ID уже используется в другой записи."));
                                    }
                                    return Mono.empty();
                                });
                    }

                    boolean shopContractorPairChanged = !existingChat.getShopID().equals(request.shopID()) ||
                            !Objects.equals(existingChat.getContractorID(), request.contractorID());

                    Mono<Void> logicCheck = Mono.empty();

                    if (shopContractorPairChanged) {
                        if (request.contractorID() != null) {
                            logicCheck = chatRepository.existsByShopIDAndContractorIDAndShopContractorChatIDNot(request.shopID(), request.contractorID(), chatId)
                                    .flatMap(exists -> {
                                        if (exists) {
                                            return Mono.error(new OperationNotAllowedException("Связь для этой пары магазина и подрядчика уже существует."));
                                        }
                                        return validateUserIsContractor(request.contractorID());
                                    });
                        } else {
                            String checkSql = "SELECT COUNT(*) FROM ShopContractorChats WHERE ShopID = :shopId AND ContractorID IS NULL AND ShopContractorChatID != :currentId";
                            logicCheck = databaseClient.sql(checkSql)
                                    .bind("shopId", request.shopID())
                                    .bind("currentId", chatId)
                                    .map(row -> row.get(0, Long.class))
                                    .one()
                                    .flatMap(count -> {
                                        if (count > 0) {
                                            return Mono.error(new OperationNotAllowedException("Связь для этого магазина без подрядчика уже существует."));
                                        }
                                        return Mono.empty();
                                    });
                        }
                    } else {
                        if (request.contractorID() != null) {
                            logicCheck = validateUserIsContractor(request.contractorID());
                        }
                    }

                    return botCheck
                            .then(telegramIdCheck)
                            .then(logicCheck)
                            .thenReturn(existingChat);
                })
                .flatMap(chat -> {
                    chat.setShopID(request.shopID());
                    chat.setContractorID(request.contractorID());
                    chat.setTelegramID(request.telegramID());
                    return chatRepository.save(chat);
                });
    }

    public Mono<Void> deleteChat(Integer chatId) {
        return chatRepository.deleteById(chatId);
    }

    public Mono<Boolean> checkIfExists(Integer shopId, Integer contractorId) {
        return chatRepository.existsByShopIDAndContractorID(shopId, contractorId);
    }

    private Mono<Void> validateUserIsContractor(Integer userId) {
        if (userId == null) {
            return Mono.empty();
        }
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Пользователь с ID " + userId + " не найден")))
                .flatMap(user -> roleRepository.findById(user.getRoleID()))
                .flatMap(role -> {
                    if (!"Contractor".equals(role.getRoleName())) {
                        return Mono.error(new OperationNotAllowedException("Можно выбрать только пользователя с ролью 'Подрядчик'"));
                    }
                    return Mono.empty();
                }).then();
    }

    public Mono<ShopContractorChatResponse> findByTelegramId(Long telegramId) {
        String sql = "SELECT scc.ShopContractorChatID, scc.ShopID, s.ShopName, scc.ContractorID, u.Login as ContractorLogin, scc.TelegramID " +
                "FROM ShopContractorChats scc " +
                "JOIN Shops s ON scc.ShopID = s.ShopID " +
                "LEFT JOIN Users u ON scc.ContractorID = u.UserID " +
                "WHERE scc.TelegramID = :telegramId";

        return databaseClient.sql(sql)
                .bind("telegramId", telegramId)
                .map(MAPPING_FUNCTION)
                .one();
    }
}