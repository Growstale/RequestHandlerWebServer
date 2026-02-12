package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.CreateShopRequest;
import com.vodchyts.backend.feature.dto.ShopResponse;
import com.vodchyts.backend.feature.dto.UpdateShopRequest;
import com.vodchyts.backend.feature.service.AuditHelper;
import com.vodchyts.backend.feature.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.vodchyts.backend.feature.dto.PagedResponse;
import java.util.List;

@RestController
@RequestMapping("/api/admin/shops")
public class ShopController {

    private final ShopService shopService;
    private final AuditHelper auditHelper;

    public ShopController(ShopService shopService, AuditHelper auditHelper) {
        this.shopService = shopService;
        this.auditHelper = auditHelper;
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
    public Mono<ShopResponse> createShop(@Valid @RequestBody Mono<CreateShopRequest> request, ServerWebExchange exchange) {
        return request.flatMap(req -> shopService.createShop(req)
                .flatMap(shop -> {
                    // Аудит создания
                    auditHelper.auditCreate("Shops", shop.getShopID(), shop, exchange).subscribe();
                    return shopService.mapShopToResponse(shop);
                }));
    }

    @PutMapping("/{shopId}")
    public Mono<ShopResponse> updateShop(@PathVariable Integer shopId, @Valid @RequestBody Mono<UpdateShopRequest> request, ServerWebExchange exchange) {
        return request.flatMap(req -> 
            shopService.getAllShops(null, 0, 1000)
                .flatMap(paged -> {
                    // Находим старую версию для аудита
                    ShopResponse oldShop = paged.content().stream()
                            .filter(s -> s.shopID().equals(shopId))
                            .findFirst()
                            .orElse(null);
                    
                    return shopService.updateShop(shopId, req)
                            .flatMap(updatedShop -> {
                                // Аудит обновления
                                auditHelper.auditUpdate("Shops", shopId, oldShop, updatedShop, exchange).subscribe();
                                return Mono.just(updatedShop);
                            });
                })
        );
    }

    @DeleteMapping("/{shopId}")
    public Mono<ResponseEntity<Void>> deleteShop(@PathVariable Integer shopId, ServerWebExchange exchange) {
        return shopService.getAllShops(null, 0, 1000)
                .flatMap(paged -> {
                    // Находим удаляемую запись для аудита
                    ShopResponse shopToDelete = paged.content().stream()
                            .filter(s -> s.shopID().equals(shopId))
                            .findFirst()
                            .orElse(null);
                    
                    return shopService.deleteShop(shopId)
                            .then(Mono.fromRunnable(() -> {
                                // Аудит удаления
                                auditHelper.auditDelete("Shops", shopId, shopToDelete, exchange).subscribe();
                            }))
                            .thenReturn(ResponseEntity.noContent().build());
                });
    }
}