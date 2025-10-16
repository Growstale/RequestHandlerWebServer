package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.exception.ShopAlreadyExistsException;
import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.dto.CreateShopRequest;
import com.vodchyts.backend.feature.dto.ShopResponse;
import com.vodchyts.backend.feature.dto.UpdateShopRequest;
import com.vodchyts.backend.feature.entity.Shop;
import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveShopRepository;
import com.vodchyts.backend.feature.repository.ReactiveUserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ShopService {

    private final ReactiveShopRepository shopRepository;
    private final ReactiveUserRepository userRepository;
    private final ReactiveRoleRepository roleRepository;

    public ShopService(ReactiveShopRepository shopRepository, ReactiveUserRepository userRepository, ReactiveRoleRepository roleRepository) {
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public Flux<ShopResponse> getAllShops(List<String> sort) {
        Flux<ShopResponse> shopResponses = shopRepository.findAll().flatMap(this::mapShopToResponse);

        if (sort != null && !sort.isEmpty()) {
            Comparator<ShopResponse> comparator = buildComparator(sort);
            if (comparator != null) {
                return shopResponses.collectList()
                        .map(list -> {
                            list.sort(comparator);
                            return list;
                        })
                        .flatMapMany(Flux::fromIterable);
            }
        }
        return shopResponses;
    }

    private Comparator<ShopResponse> buildComparator(List<String> sortParams) {
        Comparator<ShopResponse> finalComparator = null;

        for (String sortParam : sortParams) {
            String[] parts = sortParam.split(",");
            if (parts.length == 0 || parts[0].isBlank()) continue;

            String field = parts[0];
            boolean isDescending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);

            Comparator<ShopResponse> currentComparator = switch (field) {
                case "shopID" ->
                        Comparator.comparing(ShopResponse::shopID, Comparator.nullsLast(Comparator.naturalOrder()));
                case "shopName" ->
                        Comparator.comparing(ShopResponse::shopName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "address" ->
                        Comparator.comparing(ShopResponse::address, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "userLogin" ->
                        Comparator.comparing(ShopResponse::userLogin, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
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
                                if (request.telegramID() != null && !request.telegramID().isBlank()) {
                                    shop.setTelegramID(Long.parseLong(request.telegramID()));
                                }
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
                    if (request.telegramID() != null && !request.telegramID().isBlank()) {
                        shop.setTelegramID(Long.parseLong(request.telegramID()));
                    } else {
                        shop.setTelegramID(null);
                    }
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
                shop.getTelegramID(),
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