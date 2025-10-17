package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.UpdateUrgencyCategoryRequest;
import com.vodchyts.backend.feature.dto.UrgencyCategoryResponse;
import com.vodchyts.backend.feature.service.UrgencyCategoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/urgency-categories")
@PreAuthorize("hasRole('RetailAdmin')")
public class UrgencyCategoryController {

    private final UrgencyCategoryService urgencyCategoryService;

    public UrgencyCategoryController(UrgencyCategoryService urgencyCategoryService) {
        this.urgencyCategoryService = urgencyCategoryService;
    }

    @GetMapping
    public Flux<UrgencyCategoryResponse> getAllUrgencyCategories() {
        return urgencyCategoryService.getAllUrgencyCategories();
    }

    @PutMapping("/{urgencyId}")
    public Mono<UrgencyCategoryResponse> updateUrgencyCategory(
            @PathVariable Integer urgencyId,
            @Valid @RequestBody Mono<UpdateUrgencyCategoryRequest> request
    ) {
        return request.flatMap(req -> urgencyCategoryService.updateUrgencyCategory(urgencyId, req));
    }
}