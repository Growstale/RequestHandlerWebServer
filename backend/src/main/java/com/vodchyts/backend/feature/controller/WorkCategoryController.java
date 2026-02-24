package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.service.AuditHelper;
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
public class WorkCategoryController {

    private final WorkCategoryService workCategoryService;
    private final AuditHelper auditHelper;

    public WorkCategoryController(WorkCategoryService workCategoryService, AuditHelper auditHelper) {
        this.workCategoryService = workCategoryService;
        this.auditHelper = auditHelper;
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
    public Mono<WorkCategoryResponse> createWorkCategory(@Valid @RequestBody Mono<CreateWorkCategoryRequest> request, ServerWebExchange exchange) {
        return request.flatMap(workCategoryService::createWorkCategory)
                .flatMap(category -> {
                    WorkCategoryResponse response = workCategoryService.mapWorkCategoryToResponse(category);
                    // Аудит создания
                    auditHelper.auditCreate("WorkCategories", category.getWorkCategoryID(), response, exchange).subscribe();
                    return Mono.just(response);
                });
    }

    @PutMapping("/{categoryId}")
    public Mono<WorkCategoryResponse> updateWorkCategory(@PathVariable Integer categoryId, @Valid @RequestBody Mono<UpdateWorkCategoryRequest> request, ServerWebExchange exchange) {
        return workCategoryService.getAllWorkCategories(null, 0, 1000)
                .flatMap(paged -> {
                    // Находим старую версию для аудита
                    WorkCategoryResponse oldCategory = paged.content().stream()
                            .filter(c -> c.workCategoryID().equals(categoryId))
                            .findFirst()
                            .orElse(null);
                    
                    return request.flatMap(req -> workCategoryService.updateWorkCategory(categoryId, req)
                            .flatMap(updatedCategory -> {
                                // Аудит обновления
                                auditHelper.auditUpdate("WorkCategories", categoryId, oldCategory, updatedCategory, exchange).subscribe();
                                return Mono.just(updatedCategory);
                            }));
                });
    }

    @DeleteMapping("/{categoryId}")
    public Mono<ResponseEntity<Void>> deleteWorkCategory(@PathVariable Integer categoryId, ServerWebExchange exchange) {
        return workCategoryService.getAllWorkCategories(null, 0, 1000)
                .flatMap(paged -> {
                    // Находим удаляемую запись для аудита
                    WorkCategoryResponse categoryToDelete = paged.content().stream()
                            .filter(c -> c.workCategoryID().equals(categoryId))
                            .findFirst()
                            .orElse(null);
                    
                    return workCategoryService.deleteWorkCategory(categoryId)
                            .then(Mono.fromRunnable(() -> {
                                // Аудит удаления
                                auditHelper.auditDelete("WorkCategories", categoryId, categoryToDelete, exchange).subscribe();
                            }))
                            .thenReturn(ResponseEntity.noContent().build());
                });
    }
}