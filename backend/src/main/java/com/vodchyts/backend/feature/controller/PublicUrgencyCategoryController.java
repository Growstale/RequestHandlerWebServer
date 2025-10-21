package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.UrgencyCategoryResponse;
import com.vodchyts.backend.feature.service.UrgencyCategoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/urgency-categories")
public class PublicUrgencyCategoryController {

    private final UrgencyCategoryService urgencyCategoryService;

    public PublicUrgencyCategoryController(UrgencyCategoryService urgencyCategoryService) {
        this.urgencyCategoryService = urgencyCategoryService;
    }

    @GetMapping
    public Flux<UrgencyCategoryResponse> getAllUrgencyCategories() {
        return urgencyCategoryService.getAllUrgencyCategories();
    }
}