package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.PagedResponse;
import com.vodchyts.backend.feature.dto.WorkCategoryResponse;
import com.vodchyts.backend.feature.service.WorkCategoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/work-categories")
public class PublicWorkCategoryController {

    private final WorkCategoryService workCategoryService;

    public PublicWorkCategoryController(WorkCategoryService workCategoryService) {
        this.workCategoryService = workCategoryService;
    }

    @GetMapping
    public Mono<PagedResponse<WorkCategoryResponse>> getAllWorkCategories(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size
    ) {
        List<String> sortParams = exchange.getRequest().getQueryParams().get("sort");
        return workCategoryService.getAllWorkCategories(sortParams, page, size);
    }
}