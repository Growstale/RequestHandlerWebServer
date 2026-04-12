package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.exception.ResourceNotFoundException;
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
                "LEFT JOIN Shops s ON scc.ShopID = s.ShopID " +
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

        Mono<Boolean> duplicateCheck;
        if (request.shopID() != null && request.contractorID() != null) {
            duplicateCheck = chatRepository.existsByShopIDAndContractorID(request.shopID(), request.contractorID());
        } else if (request.shopID() == null && request.contractorID() != null) {
            duplicateCheck = chatRepository.existsByShopIDIsNullAndContractorID(request.contractorID());
        } else if (request.shopID() != null && request.contractorID() == null) {
            duplicateCheck = chatRepository.existsByShopIDAndContractorIDIsNull(request.shopID());
        } else {
            return Mono.error(new OperationNotAllowedException("Необходимо указать Магазин или Подрядчика."));
        }

        Mono<Void> logicCheck = duplicateCheck.flatMap(exists -> {
            if (exists) return Mono.error(new OperationNotAllowedException("Связь для этих параметров уже существует."));
            return validateUserIsContractor(request.contractorID());
        });

        return botCheck.then(logicCheck).then(Mono.defer(() -> {
                    ShopContractorChat chat = new ShopContractorChat();
                    chat.setShopID(request.shopID());
                    chat.setContractorID(request.contractorID());
                    chat.setTelegramID(request.telegramID());
                    return chatRepository.save(chat);
                }));
    }

    public Mono<ShopContractorChat> updateChat(Integer chatId, UpdateShopContractorChatRequest request) {
        return chatRepository.findById(chatId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Чат не найден")))
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


                    // Умная проверка на дубликаты при обновлении
                    Mono<Boolean> duplicateCheck;
                    if (request.shopID() != null && request.contractorID() != null) {
                        duplicateCheck = chatRepository.existsByShopIDAndContractorIDAndShopContractorChatIDNot(request.shopID(), request.contractorID(), chatId);
                    } else if (request.shopID() == null && request.contractorID() != null) {
                        duplicateCheck = chatRepository.existsByShopIDIsNullAndContractorIDAndShopContractorChatIDNot(request.contractorID(), chatId);
                    } else if (request.shopID() != null && request.contractorID() == null) {
                        duplicateCheck = chatRepository.existsByShopIDAndContractorIDIsNullAndShopContractorChatIDNot(request.shopID(), chatId);
                    } else {
                        return Mono.error(new OperationNotAllowedException("Необходимо указать Магазин или Подрядчика."));
                    }

                    Mono<Void> logicCheck = duplicateCheck.flatMap(exists -> {
                        if (exists) return Mono.error(new OperationNotAllowedException("Связь для этих параметров уже существует."));
                        return validateUserIsContractor(request.contractorID());
                    });

                    return botCheck.then(logicCheck).thenReturn(existingChat);
                })
                .flatMap(chat -> {
                    chat.setShopID(request.shopID());
                    chat.setContractorID(request.contractorID());
                    chat.setTelegramID(request.telegramID());
                    return chatRepository.save(chat);
                });
    }

    public Mono<Boolean> checkIfExists(Integer shopId, Integer contractorId) {
        return chatRepository.checkCoverage(shopId, contractorId);
    }

    public Mono<Void> deleteChat(Integer chatId) {
        return chatRepository.deleteById(chatId);
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
        String sql = "SELECT scc.ShopContractorChatID, scc.ShopID, s.ShopName, scc.ContractorID, COALESCE(u.FullName, u.Login) as ContractorLogin, scc.TelegramID " +
                "FROM ShopContractorChats scc " +
                "LEFT JOIN Shops s ON scc.ShopID = s.ShopID " +
                "LEFT JOIN Users u ON scc.ContractorID = u.UserID " +
                "WHERE scc.TelegramID = :telegramId";

        return databaseClient.sql(sql)
                .bind("telegramId", telegramId)
                .map(MAPPING_FUNCTION)
                .one();
    }
}