package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.service.WorkCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@RequestMapping("/api/admin/work-categories")
@PreAuthorize("hasRole('RetailAdmin')")
public class WorkCategoryController {

    private final WorkCategoryService workCategoryService;

    public WorkCategoryController(WorkCategoryService workCategoryService) {
        this.workCategoryService = workCategoryService;
    }

    @GetMapping
    public Mono<PagedResponse<WorkCategoryResponse>> getAllWorkCategories(
            ServerWebExchange exchange,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size
    ) {
        List<String> sortParams = exchange.getRequest().getQueryParams().get("sort");
        return workCategoryService.getAllWorkCategories(sortParams, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WorkCategoryResponse> createWorkCategory(@Valid @RequestBody Mono<CreateWorkCategoryRequest> request) {
        return request.flatMap(workCategoryService::createWorkCategory)
                .map(workCategoryService::mapWorkCategoryToResponse);
    }

    @PutMapping("/{categoryId}")
    public Mono<WorkCategoryResponse> updateWorkCategory(@PathVariable Integer categoryId, @Valid @RequestBody Mono<UpdateWorkCategoryRequest> request) {
        return request.flatMap(req -> workCategoryService.updateWorkCategory(categoryId, req));
    }

    @DeleteMapping("/{categoryId}")
    public Mono<ResponseEntity<Void>> deleteWorkCategory(@PathVariable Integer categoryId) {
        return workCategoryService.deleteWorkCategory(categoryId).thenReturn(ResponseEntity.noContent().build());
    }
}