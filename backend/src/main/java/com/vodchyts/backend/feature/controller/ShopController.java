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
import reactor.core.publisher.Mono;
import com.vodchyts.backend.feature.dto.PagedResponse;
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
    public Mono<PagedResponse<ShopResponse>> getAllShops(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size
    ) {
        List<String> sortParams = exchange.getRequest().getQueryParams().get("sort");
        return shopService.getAllShops(sortParams, page, size);
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