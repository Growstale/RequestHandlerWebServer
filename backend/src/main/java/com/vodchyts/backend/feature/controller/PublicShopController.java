package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.PagedResponse;
import com.vodchyts.backend.feature.dto.ShopResponse;
import com.vodchyts.backend.feature.service.ShopService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/shops")
public class PublicShopController {

    private final ShopService shopService;

    public PublicShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public Mono<PagedResponse<ShopResponse>> getAllShops(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size
    ) {
        List<String> sortParams = exchange.getRequest().getQueryParams().get("sort");
        return shopService.getAllShops(sortParams, page, size);
    }
}