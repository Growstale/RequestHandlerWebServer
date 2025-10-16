// Файл: feature/controller/ShopController.java

package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.CreateShopRequest;
import com.vodchyts.backend.feature.dto.ShopResponse;
import com.vodchyts.backend.feature.dto.UpdateShopRequest;
import com.vodchyts.backend.feature.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange; // <-- ВАЖНО: Добавьте этот импорт
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shops")
@PreAuthorize("hasRole('RetailAdmin')")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public Flux<ShopResponse> getAllShops(ServerWebExchange exchange) { // <-- ИЗМЕНЯЕМ ЗДЕСЬ
        // Вручную получаем параметры, чтобы избежать автоматического разделения по запятой
        List<String> sortParams = exchange.getRequest().getQueryParams().get("sort");
        return shopService.getAllShops(sortParams); // Передаем правильный список в сервис
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ShopResponse> createShop(@Valid @RequestBody Mono<CreateShopRequest> request) {
        return request.flatMap(shopService::createShop)
                .flatMap(shopService::mapShopToResponse);
    }

    @PutMapping("/{shopId}")
    public Mono<ShopResponse> updateShop(@PathVariable Integer shopId, @Valid @RequestBody Mono<UpdateShopRequest> request) {
        return request.flatMap(req -> shopService.updateShop(shopId, req));
    }

    @DeleteMapping("/{shopId}")
    public Mono<ResponseEntity<Void>> deleteShop(@PathVariable Integer shopId) {
        return shopService.deleteShop(shopId).thenReturn(ResponseEntity.noContent().build());
    }
}