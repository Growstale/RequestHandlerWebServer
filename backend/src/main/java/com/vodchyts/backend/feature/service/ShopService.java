package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.exception.ShopAlreadyExistsException;
import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.dto.CreateShopRequest;
import com.vodchyts.backend.feature.dto.PagedResponse;
import com.vodchyts.backend.feature.dto.ShopResponse;
import com.vodchyts.backend.feature.dto.UpdateShopRequest;
import com.vodchyts.backend.feature.entity.Shop;
import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
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
public class ShopService {

    private final ReactiveShopRepository shopRepository;
    private final ReactiveUserRepository userRepository;
    private final ReactiveRoleRepository roleRepository;
    private final DatabaseClient databaseClient;

    public ShopService(ReactiveShopRepository shopRepository, ReactiveUserRepository userRepository, ReactiveRoleRepository roleRepository, DatabaseClient databaseClient) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.databaseClient = databaseClient;
    }

    public static final BiFunction<Row, RowMetadata, ShopResponse> SHOP_MAPPING_FUNCTION = (row, rowMetaData) -> new ShopResponse(
            row.get("ShopID", Integer.class),
            row.get("ShopName", String.class),
            row.get("Address", String.class),
            row.get("Email", String.class),
            row.get("UserID", Integer.class),
            row.get("UserLogin", String.class)
    );

    public Mono<PagedResponse<ShopResponse>> getAllShops(List<String> sort, int page, int size) {
        String sql = "SELECT s.ShopID, s.ShopName, s.Address, s.Email, s.UserID, u.Login as UserLogin " +
                "FROM Shops s LEFT JOIN Users u ON s.UserID = u.UserID";

        String countSql = "SELECT COUNT(*) FROM Shops";
        Mono<Long> countMono = databaseClient.sql(countSql).map(row -> row.get(0, Long.class)).one();

        String sortedSql = sql + parseSortToSql(sort) + " OFFSET " + ((long) page * size) + " ROWS FETCH NEXT " + size + " ROWS ONLY";

        Flux<ShopResponse> contentFlux = databaseClient.sql(sortedSql)
                .map(SHOP_MAPPING_FUNCTION)
                .all();

        return Mono.zip(contentFlux.collectList(), countMono)
                .map(tuple -> {
                    List<ShopResponse> content = tuple.getT1();
                    Long count = tuple.getT2();
                    int totalPages = (count == 0) ? 0 : (int) Math.ceil((double) count / size);
                    return new PagedResponse<>(content, page, count, totalPages);
                });
    }

    private String parseSortToSql(List<String> sortParams) {
        if (sortParams == null || sortParams.isEmpty()) {
            return " ORDER BY s.ShopID ASC";
        }
        Map<String, String> columnMapping = Map.of(
                "shopID", "s.ShopID",
                "shopName", "s.ShopName",
                "address", "s.Address",
                "userLogin", "UserLogin"
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

        return orders.isEmpty() ? " ORDER BY s.ShopID ASC" : " ORDER BY " + orders;
    }

    public Mono<Shop> createShop(CreateShopRequest request) {
        return shopRepository.findByShopName(request.shopName())
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ShopAlreadyExistsException("Магазин с названием '" + request.shopName() + "' уже существует"));
                    }
                    return validateUserIsStoreManager(request.userID())
                            .then(Mono.defer(() -> {
                                Shop shop = new Shop();
                                shop.setShopName(request.shopName());
                                shop.setAddress(request.address());
                                shop.setEmail(request.email());
                                shop.setUserID(request.userID());
                                return shopRepository.save(shop);
                            }));
                });
    }

    public Mono<ShopResponse> updateShop(Integer shopId, UpdateShopRequest request) {
        Mono<Void> uniquenessCheck = shopRepository.findByShopName(request.shopName())
                .flatMap(existingShop -> {
                    if (!Objects.equals(existingShop.getShopID(), shopId)) {
                        return Mono.error(new ShopAlreadyExistsException("Магазин с названием '" + request.shopName() + "' уже существует"));
                    }
                    return Mono.empty();
                }).then();

        return uniquenessCheck
                .then(shopRepository.findById(shopId))
                .switchIfEmpty(Mono.error(new RuntimeException("Магазин не найден")))
                .flatMap(shop -> validateUserIsStoreManager(request.userID())
                        .thenReturn(shop))
                .flatMap(shop -> {
                    shop.setShopName(request.shopName());
                    shop.setAddress(request.address());
                    shop.setEmail(request.email());
                    shop.setUserID(request.userID());
                    return shopRepository.save(shop);
                })
                .flatMap(this::mapShopToResponse);
    }

    public Mono<Void> deleteShop(Integer shopId) {
        return shopRepository.deleteById(shopId);
    }

    public Mono<ShopResponse> mapShopToResponse(Shop shop) {
        Mono<String> userLoginMono = (shop.getUserID() != null)
                ? userRepository.findById(shop.getUserID())
                .map(User::getLogin)
                .defaultIfEmpty("N/A")
                : Mono.just("N/A");

        return userLoginMono.map(userLogin -> new ShopResponse(
                shop.getShopID(),
                shop.getShopName(),
                shop.getAddress(),
                shop.getEmail(),
                shop.getUserID(),
                userLogin
        ));
    }

    private Mono<Void> validateUserIsStoreManager(Integer userId) {
        if (userId == null) {
            return Mono.empty();
        }
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Назначаемый пользователь с ID " + userId + " не найден")))
                .flatMap(user -> roleRepository.findById(user.getRoleID()))
                .flatMap(role -> {
                    if (!"StoreManager".equals(role.getRoleName())) {
                        return Mono.error(new OperationNotAllowedException("В качестве ответственного можно назначить только пользователя с ролью 'Менеджер магазина'"));
                    }
                    return Mono.empty();
                }).then();
    }
}