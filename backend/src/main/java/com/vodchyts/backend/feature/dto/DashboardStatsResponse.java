package com.vodchyts.backend.feature.dto;

import java.util.List;

public record DashboardStatsResponse(
        long totalRequests,
        long activeRequests,
        long completedRequests,
        long overdueRequests,
        // Новые KPI
        Double averageCompletionTimeDays, // Среднее время выполнения в днях
        Double slaCompliancePercent,      // Процент соблюдения сроков

        List<ChartData> requestsByStatus,
        List<ChartData> requestsByUrgency,
        List<ChartData> requestsByWorkCategory,
        List<DateChartData> requestsLast7Days,
        List<TopContractorData> topContractors,

        // Новые списки для графиков
        List<ChartData> contractorWorkload, // Текущая загрузка (активные заявки)
        List<ChartData> topProblemShops     // Магазины с макс. кол-вом заявок
) {
    public record ChartData(String name, long value) {}
    public record DateChartData(String date, long count) {}
    public record TopContractorData(String name, long completedCount) {}
}