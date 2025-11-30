package com.vodchyts.backend.feature.dto;

import java.util.List;
import java.util.Map;

public record DashboardStatsResponse(
        long totalRequests,
        long activeRequests,
        long completedRequests,
        long overdueRequests,
        List<ChartData> requestsByStatus,
        List<ChartData> requestsByUrgency,
        List<ChartData> requestsByWorkCategory,
        List<DateChartData> requestsLast7Days,
        List<TopContractorData> topContractors
) {
    public record ChartData(String name, long value) {}
    public record DateChartData(String date, long count) {}
    public record TopContractorData(String name, long completedCount) {}
}