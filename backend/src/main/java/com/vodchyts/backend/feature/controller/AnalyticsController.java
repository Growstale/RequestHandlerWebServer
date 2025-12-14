package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.DashboardStatsResponse;
import com.vodchyts.backend.feature.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('RetailAdmin')")
    public Mono<DashboardStatsResponse> getStats() {
        return analyticsService.getDashboardStats();
    }
}