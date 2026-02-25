package com.vodchyts.backend.feature.dto;

import java.util.List;

public record DashboardStatsResponse(
        long totalRequests,
        long activeRequests,
        long completedRequests,
        long overdueRequests,
        Double averageCompletionTimeDays,
        Double slaCompliancePercent,

        List<ChartData> requestsByStatus,
        List<ChartData> requestsByUrgency,
        List<ChartData> requestsByWorkCategory,
        List<DateChartData> requestsLast7Days,
        List<TopContractorData> topContractors,
        List<ChartData> contractorWorkload,
        List<ChartData> topProblemShops,

        // Новые списки для антирейтингов
        List<ChartData> worstContractors,
        List<ChartData> worstShops
) {
    public record ChartData(String name, long value) {}
    public record DateChartData(String date, long count) {}
    public record TopContractorData(String name, long completedCount) {}
}