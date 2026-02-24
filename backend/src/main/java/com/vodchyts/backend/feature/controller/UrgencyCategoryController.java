package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.UpdateUrgencyCategoryRequest;
import com.vodchyts.backend.feature.dto.UrgencyCategoryResponse;
import com.vodchyts.backend.feature.service.AuditHelper;
import com.vodchyts.backend.feature.service.UrgencyCategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/urgency-categories")
public class UrgencyCategoryController {

    private final UrgencyCategoryService urgencyCategoryService;
    private final AuditHelper auditHelper;

    public UrgencyCategoryController(UrgencyCategoryService urgencyCategoryService, AuditHelper auditHelper) {
        this.urgencyCategoryService = urgencyCategoryService;
        this.auditHelper = auditHelper;
    }

    @GetMapping
    public Flux<UrgencyCategoryResponse> getAllUrgencyCategories() {
        return urgencyCategoryService.getAllUrgencyCategories();
    }

    @PutMapping("/{urgencyId}")
    public Mono<UrgencyCategoryResponse> updateUrgencyCategory(
            @PathVariable Integer urgencyId,
            @Valid @RequestBody Mono<UpdateUrgencyCategoryRequest> request,
            ServerWebExchange exchange
    ) {
        return urgencyCategoryService.getAllUrgencyCategories()
                .collectList()
                .flatMap(categories -> {
                    // Находим старую версию для аудита
                    UrgencyCategoryResponse oldCategory = categories.stream()
                            .filter(c -> c.urgencyID().equals(urgencyId))
                            .findFirst()
                            .orElse(null);
                    
                    return request.flatMap(req -> urgencyCategoryService.updateUrgencyCategory(urgencyId, req)
                            .flatMap(updatedCategory -> {
                                // Аудит обновления
                                auditHelper.auditUpdate("UrgencyCategories", urgencyId, oldCategory, updatedCategory, exchange).subscribe();
                                return Mono.just(updatedCategory);
                            }));
                });
    }
}